package io.brutus.minecraft.serverclusters.gameserver;

import io.brutus.minecraft.serverclusters.protocol.Heartbeat;
import io.brutus.minecraft.serverclusters.protocol.ShutdownNotification;
import io.brutus.networking.pubsubmessager.PubSubMessager;

/**
 * A messager that handles sending heartbeat and shutdown messages. Notifies connected servers of
 * this server's status.
 * <p>
 * Uses a variable "heart rate" (the frequency of outgoing heartbeat messages). When the number of
 * open slots changes often, the heart will beat fast in order to reduce the chances of a connected
 * server having outdated information. When no change is happening, the heart will beat more slowly,
 * just periodically letting connected servers know that this server has not crashed.
 */
public class BeatingHeart {

  private final String thisServerId;

  private final SlotManager slotManager;

  private final PubSubMessager messager;
  private final byte[] shutdownChannel;
  private final byte[] heartbeatChannel;
  private final byte[] baseMessage;

  private volatile boolean alive;

  public BeatingHeart(ServerClustersConfiguration config, PubSubMessager messager,
      SlotManager slotManager) throws IllegalArgumentException {

    if (config == null || slotManager == null || messager == null) {
      throw new IllegalArgumentException("params cannot be null");
    }

    this.thisServerId = config.getServerId();

    this.slotManager = slotManager;

    shutdownChannel = config.getShutdownChannel();
    heartbeatChannel = config.getHeartbeatChannel();
    if (shutdownChannel == null || shutdownChannel.length < 1 || heartbeatChannel == null
        || heartbeatChannel.length < 1) {
      throw new IllegalArgumentException(
          "the configured heartbeat and shutdown messaging channels cannot be null or empty");
    }

    this.messager = messager;

    ServerUtils utils = ServerClusters.getSingleton().getServerUtils();
    String ip = utils.getServerIp();
    if (ip == null || ip.isEmpty()) {
      System.out.println("==================================================================");
      System.out
          .println("[IMPORTANT!!!] The server's IP address must be manually set in server.properties for "
              + "ServerClusters to work properly. It is not set, and so ServerClusters will not work.");
      System.out.println("==================================================================");
      throw new IllegalArgumentException("server ip must be defined");
    }
    int port = utils.getServerPort();

    this.baseMessage =
        Heartbeat.createMessage(config.getClusterId(), config.getServerId(), ip, port, 0);

    startHeartBeating(config);
  }

  /**
   * Sends a shutdown notification for this server and stops its heartbeat.
   * <p>
   * Does NOT stop this messager from receiving or using other server's heartbeat messages. Turns
   * this messager into a receiver only. To fully relinquish all of this messager's resources, use
   * {@link #destroy()}.
   * <p>
   * Cannot be reversed.
   */
  public void sendShutdownNotification() {
    if (!alive) {
      return;
    }
    alive = false;
    ServerClusters.getSingleton().getServerUtils().sync(new Runnable() {
      @Override
      public void run() {
        messager.publish(shutdownChannel, ShutdownNotification.createMessage(thisServerId));
      }
    });
  }

  /**
   * Stops this from sending/receiving any more heartbeats, sends a shutdown notification if one has
   * not already been sent, and kills its connections.
   * <p>
   * Cannot be reversed.
   */
  public void destroy() {
    sendShutdownNotification();
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
    ServerClusters.getSingleton().getServerUtils().sync(new Runnable() {
      @Override
      public void run() {
        Heartbeat.updateMessage(baseMessage, openSlots);
        messager.publish(heartbeatChannel, baseMessage);
      }
    });
  }

  /**
   * Starts the variable-rate heartbeats.
   */
  private void startHeartBeating(ServerClustersConfiguration config) {
    alive = true;
    final long checkInInterval = config.getMaxHeartRate();
    final long maxWaitTime = config.getMinHeartRate();

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
              System.out.println("[ServerClusters] The heartbeat thread was interrupted.");
              e.printStackTrace();
              destroy();
            }
            timePassed += checkInInterval;
          }
        }
      }

    }).start();
  }
}
