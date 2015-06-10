package io.brutus.minecraft.serverclusters.uid;

import java.util.Arrays;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import io.brutus.minecraft.serverclusters.protocol.IdRequest;
import io.brutus.minecraft.serverclusters.protocol.IdResponse;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import io.brutus.networking.pubsubmessager.Subscriber;

/**
 * Provides unique ids for servers that request them.
 */
public class IdProvider {

  private static final String CLUSTER_VAR = "%cluster%";
  private static final String COUNTER_VAR = "%counter%";

  private PubSubMessager messager;
  private byte[] requestChannel;
  private byte[] responseChannel;
  private IdCounterConfiguration counterConfig;
  private RequestSubscriber sub;

  public IdProvider(PubSubMessager messager, byte[] requestChannel, byte[] responseChannel,
      IdCounterConfiguration counterConfig) {
    if (messager == null || requestChannel == null || responseChannel == null
        || counterConfig == null) {
      throw new IllegalArgumentException("params cannot be null");
    } else if (requestChannel.length < 1 || responseChannel.length < 1) {
      throw new IllegalArgumentException("messaging channels cannot be empty");
    }

    this.messager = messager;
    this.requestChannel = requestChannel;
    this.responseChannel = responseChannel;
    this.counterConfig = counterConfig;
    this.sub = new RequestSubscriber();

    messager.subscribe(requestChannel, sub);
  }

  /**
   * Stops this object from functioning and relinquishes its resources.
   * <p>
   * Irreversible.
   */
  public void destroy() {
    messager.unsubscribe(requestChannel, sub);
  }

  /**
   * Responds to incoming id requests.
   */
  private class RequestSubscriber implements Subscriber {

    @Override
    public void onMessage(byte[] channel, byte[] message) {

      if (Arrays.equals(requestChannel, channel)) {

        final IdRequest request;
        try {
          request = IdRequest.fromBytes(message);
        } catch (Exception ex) {
          System.out
              .println("[ServerClusters] Received a message on the id request channel, but it was not a correctly formatted id message");
          ex.printStackTrace();
          return;
        }

        final ListenableFuture<Long> callback = counterConfig.getCounter(request.getClusterId());
        callback.addListener(new Runnable() {
          @Override
          public void run() {
            try {
              Long counter = callback.get();
              if (counter == null) {
                return;
              }

              // creates an id by replacing the variables with values in the configured format
              String id = counterConfig.getIdFormat();
              id = id.replace(CLUSTER_VAR, request.getClusterId());
              id = id.replace(COUNTER_VAR, String.valueOf(counter));

              byte[] response = IdResponse.createMessage(id, request);
              messager.publish(responseChannel, response);

            } catch (Exception e) {
            }
          }
        }, MoreExecutors.sameThreadExecutor());
      }
    }
  }

}
