package io.brutus.minecraft.serverclusters;

import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

/**
 * Main configuration for the ServerClusters utility.
 */
public interface ServerClustersConfig {

  /**
   * Gets the name of the instance of the messaging implementation that should be used to send and
   * receive messages.
   * 
   * @return The name of the messager instance to use.
   */
  String getMessagerInstanceName();

  /**
   * Gets the channel, as a <code>byte</code> array, on which to send and listen for heartbeat
   * notifications to/from connected servers.
   * 
   * @return The pub/sub heartbeat messaging channel.
   */
  byte[] getHeartbeatChannel();

  /**
   * Gets the channel, as a <code>byte</code> array, on which to send and listen for server-shutdown
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
   * Gets whether this instance of ServerClusters is configured to act in the role of P2P-server, or
   * just as a client.
   * <p>
   * In the P2P-server role, this instance will send heartbeat messages about its status, declare
   * itself as a member of a cluster, and allow players to be sent to its open slots as an instance
   * of that cluster.
   * <p>
   * If not in the server role, this instance will still listen for heartbeats from connected
   * servers and be able to send players to clusters on the ServerClusters network.
   * 
   * @return <code>true</code> to act in the role of server.
   */
  boolean actAsServer();

  /**
   * Gets the configuration for functions specific to a "P2P-server role" among connected servers.
   * Gets nothing if this ServerClusters instance is not configured to act as a server in
   * ServerClusters' P2P network.
   * <p>
   * In the P2P-server role, this instance will send heartbeat messages about its status, declare
   * itself as a member of a cluster, and allow players to be sent to its open slots as an instance
   * of that cluster.
   * <p>
   * If not in the server role, this instance will still listen for heartbeats from connected
   * servers and be able to send players to clusters on the ServerClusters network.
   * 
   * @return The config for server functions. <code>null</code> if this is not configured to be a
   *         ServerClusters-server instance.
   */
  ServerClustersServerConfig getServerConfig();

  /**
   * Gets the id of this gameserver as it is recognized by proxies, other servers, etc.
   * 
   * @return The id of the server this is running on.
   */
  String getGameServerId();

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
   * for a response and retry.
   * 
   * @return The reservation-response timeout.
   */
  long getResponseTimeout();

  /**
   * Gets the configured selection mode for a given cluster.
   * 
   * @param clusterId The cluster to get the selection mode for.
   * @return A selection mode for the cluster. <code>null</code> if no mode is found for the given
   *         cluster id.
   * @throws IllegalArgumentException on a <code>null</code> or empty cluster id.
   */
  ServerSelectionMode getSelectionMode(String clusterId) throws IllegalArgumentException;

}
