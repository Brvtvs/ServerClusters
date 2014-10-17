package io.brutus.minecraft.serverclusters;

/**
 * A configuration for p2p-server-specific functions of the ServerClusters utility. Unused if this
 * instance of ServerClusters is not filling the role of a server on a ServerClusters p2p network.
 */
public interface ServerClustersServerConfig {

  /**
   * Gets the id of the cluster that this server is a part of.
   * 
   * @return The cluster this server is a part of.
   */
  String getClusterId();

  /**
   * Gets how total slots to start with. Can be overwritten through the ServerClusters API.
   * 
   * @return The total number of players slots for this server.
   */
  int getTotalSlots();

  /**
   * Gets whether to kick players when they try to join without a valid reservation, even when there
   * are open slots available.
   * 
   * @return <code>true</code> to kick players when they log in without a reservation.
   */
  boolean enforceReservations();

  /**
   * Gets how long, in milliseconds, to wait after approving a reservation before assuming the
   * player(s) are not coming and revoking their reservation, reopening the slot for other players.
   * 
   * @return The maximum time a reservation can wait without being fulfilled. The reservation
   *         timeout.
   */
  long getReservationTimeout();

  /**
   * Gets the maximum amount of time, in milliseconds, to wait in between sending heartbeats, even
   * if there have been no updates to local data in the meantime.
   * 
   * @return The maximum amount of time to wait in between heartbeats.
   */
  long getMinHeartRate();

  /**
   * Gets the minimum amount of time, in milliseconds, to wait between sending heartbeats, even if
   * there are updates to local data in the meantime.
   * 
   * @return This server's maximum rate of sending heartbeat messages.
   */
  long getMaxHeartRate();

}
