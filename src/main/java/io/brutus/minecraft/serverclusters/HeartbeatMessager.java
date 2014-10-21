package io.brutus.minecraft.serverclusters;

import java.util.Arrays;

import io.brutus.minecraft.pubsub.PubSub;
import io.brutus.minecraft.serverclusters.bukkit.ServerUtil;
import io.brutus.minecraft.serverclusters.protocol.Heartbeat;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import io.brutus.networking.pubsubmessager.Subscriber;

/**
 * A messager that handles sending and receiving heartbeat and shutdown messages. Notifies connected
 * servers of this server's status, and updates the local cache with incoming data about other
 * servers.
 * <p>
 * Uses a variable "heart rate" (the frequency of outgoing heartbeat messages). When the number of
 * open slots changes often, the heart will beat fast in order to reduce the chances of a connected
 * server having outdated information. When no change is happening, the heart will beat more slowly,
 * just periodically letting connected servers know that this server has not crashed.
 */
public class HeartbeatMessager implements Subscriber {

  private final String thisServerId;

  private final SlotManager slotManager;
  private final NetworkStatus networkCache;

  private final PubSubMessager messager;
  private final byte[] heartbeatChannel;
  private final byte[] baseMessage;

  private volatile boolean alive;

  public HeartbeatMessager(ServerClustersConfig config, NetworkStatus networkStatus,
      SlotManager slotManager) throws IllegalArgumentException {

    if (config == null || networkStatus == null || slotManager == null) {
      throw new IllegalArgumentException("params cannot be null");
    }

    this.thisServerId = config.getGameServerId();

    this.slotManager = slotManager;
    this.networkCache = networkStatus;

    heartbeatChannel = config.getHeartbeatChannel();
    if (heartbeatChannel == null || heartbeatChannel.length < 1) {
      throw new IllegalArgumentException(
          "the configured heartbeat messaging channels cannot be null or empty");
    }

    this.messager = PubSub.getSingleton().getMessager(config.getMessagerInstanceName());
    if (messager == null) {
      throw new IllegalStateException("a messager for the configured name could not be found");
    }

    messager.subscribe(heartbeatChannel, this);

    this.baseMessage =
        Heartbeat.createMessage(config.getServerConfig().getClusterId(), config.getGameServerId(),
            0);

    startHeartBeating(config);
  }

  @Override
  public void onMessage(byte[] channel, byte[] message) {
    if (Arrays.equals(channel, heartbeatChannel)) {
      onHeartbeatMessage(message);
    }
  }

  private void onHeartbeatMessage(byte[] message) {
    Heartbeat hb = null;
    try {
      hb = Heartbeat.fromBytes(message);

    } catch (Exception e) {
      System.out.println("Received a message on the heartbeat channel that could not be parsed.");
      e.printStackTrace();
      return;
    }

    if (hb.getServerId().equals(thisServerId)) { // This server's heartbeat; ignores it.
      return;
    }

    networkCache.onHeartbeat(hb);
  }

  /**
   * Sends a heartbeat message to connected servers.
   * <p>
   * Always sends the heartbeat on this server's main thread, because the main thread is what really
   * indicates whether this server can accept players or not. If anything bad happens to the main
   * thread, it does not matter that some thread that only triggers heartbeats is still running.
   * 
   * @param openSlots The number of open slots to send the heartbeat with.
   */
  private void sendHeartbeat(final int openSlots) {
    ServerUtil.sync(new Runnable() {
      @Override
      public void run() {

        // TODO debug
        System.out.println("[ServerClusters] Sending a heartbeat message ( " + openSlots
            + " open slots).");

        Heartbeat.updateMessage(baseMessage, openSlots);
        messager.publish(heartbeatChannel, baseMessage);
      }
    });
  }

  /**
   * Starts the variable-rate heartbeats.
   */
  private void startHeartBeating(ServerClustersConfig config) {
    alive = true;
    final long checkInInterval = config.getServerConfig().getMaxHeartRate();
    final long maxWaitTime = config.getServerConfig().getMinHeartRate();

    new Thread(new Runnable() {

      @Override
      public void run() {
        long timePassed = 0;
        int lastOpenSlots = Integer.MIN_VALUE;

        while (alive) {

          // sends a heartbeat if anything has changed that connected servers should know about.
          int slotsNow = slotManager.getOpenSlots();
          if (slotsNow != lastOpenSlots) {
            sendHeartbeat(slotsNow);
            lastOpenSlots = slotsNow;
            timePassed = 0;
          }

          // if waiting another checkInInterval would cause there to be no heartbeat past the max
          // amount of wait time allowed, waits only just long enough to hit the max wait time, and
          // then forces a heartbeat.
          if (timePassed + checkInInterval >= maxWaitTime) {
            try {
              Thread.sleep(maxWaitTime - timePassed);
            } catch (InterruptedException e) {
              System.out.println("[ServerClusters] The heartbeat thread was interrupted");
            }
            lastOpenSlots = Integer.MIN_VALUE; // sets an impossible value; triggers heartbeat

          } else {
            try {
              Thread.sleep(checkInInterval);
            } catch (InterruptedException e) {
              System.out.println("[ServerClusters] The heartbeat thread was interrupted");
            }
            timePassed += checkInInterval;
          }
        }
      }

    }).start();
  }
}
