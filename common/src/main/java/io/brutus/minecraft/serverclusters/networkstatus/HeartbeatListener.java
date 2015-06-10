package io.brutus.minecraft.serverclusters.networkstatus;

import io.brutus.minecraft.serverclusters.protocol.Heartbeat;
import io.brutus.minecraft.serverclusters.protocol.ShutdownNotification;

/**
 * A listener to incoming heartbeat and related messages.
 */
public interface HeartbeatListener {

  /**
   * Called when a heartbeat is received.
   * <p>
   * Does not ignore any heartbeats, including in cases like where the server this is running on is
   * the one that sent the heartbeat message.
   * 
   * @param heartbeat The heartbeat.
   */
  void onHeartbeat(Heartbeat heartbeat);

  /**
   * Called when a shutdown notification is received.
   * 
   * @param notification The shutdown notification.
   */
  void onShutdownNotification(ShutdownNotification notification);

}
