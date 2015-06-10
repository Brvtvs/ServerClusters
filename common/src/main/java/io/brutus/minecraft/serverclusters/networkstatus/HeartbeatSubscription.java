package io.brutus.minecraft.serverclusters.networkstatus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.brutus.minecraft.serverclusters.protocol.Heartbeat;
import io.brutus.minecraft.serverclusters.protocol.ShutdownNotification;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import io.brutus.networking.pubsubmessager.Subscriber;

/**
 * Listens for heartbeat and related messages and translates/deserializes them into objects before
 * passing them on to any listeners.
 */
public class HeartbeatSubscription {

  private final PubSubMessager messager;
  private final byte[] heartbeatChannel;
  private final byte[] shutdownChannel;
  private final HeartbeatSubscriber sub;

  private Set<HeartbeatListener> listeners;

  public HeartbeatSubscription(PubSubMessager messager, byte[] heartbeatChannel,
      byte[] shutdownChannel) throws IllegalArgumentException {

    if (messager == null) {
      throw new IllegalStateException("a messager for the configured name could not be found");
    }
    if (shutdownChannel == null || shutdownChannel.length < 1 || heartbeatChannel == null
        || heartbeatChannel.length < 1) {
      throw new IllegalArgumentException(
          "the heartbeat and shutdown messaging channels cannot be null or empty");
    }
    if (Arrays.equals(heartbeatChannel, shutdownChannel)) {
      throw new IllegalArgumentException(
          "heartbeats and shutdown notifications cannot be on the same channel");
    }

    this.messager = messager;
    this.heartbeatChannel = heartbeatChannel.clone();
    this.shutdownChannel = shutdownChannel.clone();
    this.sub = new HeartbeatSubscriber();

    messager.subscribe(this.heartbeatChannel, sub);
    messager.subscribe(this.shutdownChannel, sub);

    listeners = new HashSet<HeartbeatListener>();
  }

  /**
   * Registers a listener to be informed of incoming messages.
   * 
   * @param listener The listener to inform of incoming messages.
   */
  public void registerListener(HeartbeatListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a listener from being informed of incoming messages.
   * 
   * @param listener The listener to stop notifying of incoming messages.
   */
  public void unregisterListener(HeartbeatListener listener) {
    listeners.remove(listener);
  }

  /**
   * Stops this object's functioning and unsubcribes it from incoming messages.
   * <p>
   * Irreversible.
   */
  public void destroy() {
    messager.unsubscribe(heartbeatChannel, sub);
    messager.unsubscribe(shutdownChannel, sub);

    listeners.clear();
  }

  /**
   * Hides the subscriber interface so it cannot be messed with.
   */
  private class HeartbeatSubscriber implements Subscriber {

    @Override
    public void onMessage(byte[] channel, byte[] message) {
      if (channel == null || message == null || listeners.isEmpty()) {
        return;
      }

      if (Arrays.equals(heartbeatChannel, channel)) {
        Heartbeat hb = null;
        try {
          hb = Heartbeat.fromBytes(message);
          for (HeartbeatListener listener : listeners) {
            listener.onHeartbeat(hb);
          }

        } catch (Exception e) {
          System.out
              .println("Received a message on the heartbeat channel that could not be parsed.");
          e.printStackTrace();
          return;
        }

      } else if (Arrays.equals(shutdownChannel, channel)) {
        ShutdownNotification sn = null;
        try {
          sn = ShutdownNotification.fromBytes(message);
          for (HeartbeatListener listener : listeners) {
            listener.onShutdownNotification(sn);
          }

        } catch (Exception e) {
          System.out
              .println("Received a message on the shutdown channel that could not be parsed.");
          e.printStackTrace();
          return;
        }
      }
    }
  }

}
