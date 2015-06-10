package io.brutus.minecraft.serverclusters.uid;

import java.util.Arrays;

import io.brutus.minecraft.serverclusters.notifications.AdminNotifier;
import io.brutus.minecraft.serverclusters.protocol.IdRequest;
import io.brutus.minecraft.serverclusters.protocol.IdResponse;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import io.brutus.networking.pubsubmessager.Subscriber;

/**
 * Makes requests for unique server ids.
 * <p>
 * Hangs and continually retries until a response is received.
 */
public class IdRequester implements Subscriber {

  private static final long WAIT_INTERVAL = 100;
  private static final long LOG_INTERVAL = 15000;
  private static final long RESEND_INTERVAL = 10000;
  private static final int FAILS_TO_NOTIFY_ADMIN = 10;

  /**
   * Gets a unique id for a server that is a member of a cluster.
   * <p>
   * Thread-safe, but waits to get a response from a remote server on whatever thread it is run on.
   * Should not be used on any thread that cannot be delayed indefinitely.
   * <p>
   * Making multiple requests for the same cluster-ip-port combination may cause strange behavior.
   * 
   * @param messager The pub/sub messager to make the request on.
   * @param requestChannel The channel to publish the request on.
   * @param responseChannel The channel to listen for a response on.
   * @param clusterId The id of the cluster the server that is being identified is part of.
   * @param ip The ip of the server that is being identified.
   * @param port The port of the server that is being identified.
   * @param notifier The admin notifier to use in case the id request cannot be completed and hangs
   *        indefinitely. Can be <code>null</code> not to attempt to notify admins in case of a
   *        continuously unfulfilled request.
   * @return A unique id for the server to use. <code>null</code> if one could not be obtained.
   * @throws IllegalArgumentException On a <code>null</code> parameter or an empty string/array.
   */
  public static String getUniqueId(PubSubMessager messager, byte[] requestChannel,
      byte[] responseChannel, String clusterId, String requesterIp, int requesterPort,
      AdminNotifier notifier) throws IllegalArgumentException {

    return new IdRequester(messager, requestChannel, responseChannel, clusterId, requesterIp,
        requesterPort, notifier).get();
  }

  private final PubSubMessager messager;
  private final byte[] requestChannel;
  private final byte[] responseChannel;

  private final String clusterId;
  private final String ip;
  private final int port;

  private final AdminNotifier notifier;

  private volatile String result;

  private IdRequester(PubSubMessager messager, byte[] requestChannel, byte[] responseChannel,
      String clusterId, String requesterIp, int requesterPort, AdminNotifier notifier)
      throws IllegalArgumentException {
    if (messager == null) {
      throw new IllegalArgumentException("messager cannot be null");
    } else if (requestChannel == null || requestChannel.length < 1 || responseChannel == null
        || responseChannel.length < 1) {
      throw new IllegalArgumentException("messaging channels cannot be null or empty");
    }
    if (clusterId == null || clusterId.isEmpty() || requesterIp == null || requesterIp.isEmpty()) {
      throw new IllegalArgumentException("cluster id and requester ip cannot be null or empty");
    }

    this.messager = messager;
    this.requestChannel = requestChannel;
    this.responseChannel = responseChannel;

    this.clusterId = clusterId;
    this.ip = requesterIp;
    this.port = requesterPort;

    this.notifier = notifier;

    messager.subscribe(responseChannel, this);
  }

  private String get() {

    System.out.println("[ServerClusters] Requesting unique server id...");
    try {

      long timeSinceRequest = Long.MAX_VALUE;
      long timeSinceLog = 0;
      int tries = 0;

      while (result == null) {

        if (timeSinceRequest >= RESEND_INTERVAL) {
          // time to publish a request
          timeSinceRequest = 0;
          tries++;

          if (!messager.publish(requestChannel, IdRequest.createMessage(clusterId, ip, port)).get()) {
            // publishing obviously failed
            System.out.println("[ServerClusters] failed to publish an id-request message.");
          }
        }

        if (timeSinceLog >= LOG_INTERVAL) {
          System.out.println("[ServerClusters] Still waiting for a unique server id...");
          timeSinceLog = 0;
        }

        try {
          Thread.sleep(WAIT_INTERVAL);
          timeSinceRequest += WAIT_INTERVAL;
          timeSinceLog += WAIT_INTERVAL;
        } catch (InterruptedException e) {
          System.out
              .println("[ServerClusters] thread was interrupted while waiting for a unique server id");
          e.printStackTrace();
          return null;
        }

        // attempts to send a notification to admins if the request keeps failing
        if (notifier != null && result == null && tries == FAILS_TO_NOTIFY_ADMIN) {
          notifier
              .sendNotification(
                  "ServerClusters Coordinator Unresponsive (id request)",
                  "The ServerClusters coordinator is failing to respond to a unique-id request from a server in the cluster '"
                      + clusterId
                      + "' on the host "
                      + ip
                      + ":"
                      + port
                      + ". The coordinator, the message broker, or the network path between them and this server may be down or malfunctioning.");
        }
      }

    } catch (Exception e) {
      System.out.println("[ServerClusters] Failed to get a unique id.");
      e.printStackTrace();
    }

    if (result != null) {
      System.out.println("[ServerClusters] Unique server id '" + result + "' obtained.");
    } else {
      System.out.println("[ServerClusters] Could not get a unique server id.");
    }

    return result;
  }

  @Override
  public void onMessage(byte[] channel, byte[] message) {
    if (Arrays.equals(channel, responseChannel)) {
      try {
        IdResponse ir = IdResponse.fromBytes(message);

        // if the response is to this object's specific request, preps the result for being returned
        // and stops listening for any more responses.
        if (ir.getClusterId().equals(clusterId) && ir.getRequestingIp().equals(ip)
            && ir.getRequestingPort() == port) {

          result = ir.getUniqueServerId();

          messager.unsubscribe(responseChannel, this);

        }
      } catch (Exception e) {
        System.out
            .println("[ServerClusters] Received a message on the id response channel, but it was not a correctly formatted id message");
        e.printStackTrace();
      }
    }
  }

}
