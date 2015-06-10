package io.brutus.minecraft.serverclusters.config;

import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

/**
 * Configuration for settings shared by the entire network.
 */
public interface SharedConfiguration {

  /**
   * Gets the configured selection mode for a given cluster.
   * 
   * @param clusterId The cluster to get the selection mode for.
   * @return A selection mode for the cluster. <code>null</code> if no mode is found for the given
   *         cluster id.
   * @throws IllegalArgumentException on a <code>null</code> or empty cluster id.
   */
  ServerSelectionMode getSelectionMode(String clusterId) throws IllegalArgumentException;

  /**
   * Gets the channel, as a <code>byte</code> array, on which servers should send requests for
   * unique ids.
   * 
   * @return The pub/sub id-request messaging channel.
   */
  byte[] getIdRequestChannel();

  /**
   * Gets the channel, as a <code>byte</code> array, on which unique ids should be allocated to
   * servers that requested them.
   * 
   * @return The pub/sub id-response messaging channel.
   */
  byte[] getIdResponseChannel();

  /**
   * Gets the channel, as a <code>byte</code> array, on which to send and listen for heartbeat
   * notifications to/from connected servers.
   * 
   * @return The pub/sub heartbeat messaging channel.
   */
  byte[] getHeartbeatChannel();

  /**
   * Getes the channel, as a <code>byte</code> array, on which to send and listen for shutdown
   * notifications to/from connected servers.
   * 
   * @return The pub/sub shutdown messaging channel.
   */
  byte[] getShutdownChannel();

  /**
   * Gets the channel, as a <code>byte</code> array, on which to send and listen for
   * slot-reservation requests to/from connected servers.
   * 
   * @return The pub/sub slot-reservation-request messaging channel.
   */
  byte[] getReservationRequestChannel();

  /**
   * Gets the channel, as a <code>byte</code> array, on which to send and listen for
   * slot-reservation responses to/from connected servers.
   * 
   * @return The pub/sub slot-reservation-response messaging channel.
   */
  byte[] getReservationResponseChannel();

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

  /**
   * Gets how long to wait for a heartbeat, in milliseconds, after the previous heartbeat before
   * assuming the sending server is unresponsive.
   * 
   * @return The acceptable amount of time in between heartbeat messages before the server sending
   *         them is considered offline.
   */
  long getServerTimeout();

  /**
   * Gets the timeout, in milliseconds, after sending a reservation request to give up on listening
   * for a response. Applies to messages targeted at servers as well as at specific players.
   * 
   * @return The reservation-response timeout.
   */
  long getReservationResponseTimeout();

  /**
   * Gets how long, in milliseconds, to wait after approving a reservation before assuming the
   * player(s) are not coming and revoking their reservation, reopening the slot for other players.
   * 
   * @return The maximum time a reservation can wait without being fulfilled. The reservation
   *         timeout.
   */
  long getReservationFulfillmentTimeout();

}
