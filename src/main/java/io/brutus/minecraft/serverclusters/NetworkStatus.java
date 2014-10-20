package io.brutus.minecraft.serverclusters;

import java.util.List;

import io.brutus.minecraft.serverclusters.cache.ServerStatus;
import io.brutus.minecraft.serverclusters.protocol.Heartbeat;
import io.brutus.minecraft.serverclusters.protocol.ShutdownNotification;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

/**
 * A store of information about connected servers, updated with heartbeat and shutdown messages.
 */
public interface NetworkStatus {

  /**
   * Updates the network's status with the information contained in the heartbeat.
   * 
   * @param heartbeat The contents of a received heartbeat message from a connected server.
   * @throws IllegalArgumentException on a <code>null</code> heartbeat.
   */
  void onHeartbeat(Heartbeat heartbeat) throws IllegalArgumentException;

  /**
   * Updates the network's status with the information contained in the shutdown notification.
   * 
   * @param shutdown The contents of a received shutdown notification from a connected server.
   * @throws IllegalArgumentException on a <code>null</code> shutdown notification.
   */
  void onShutdown(ShutdownNotification shutdown) throws IllegalArgumentException;

  /**
   * Gets an ordered list of the ids of available servers. Orders by the given selection mode, only
   * including responsive servers with enough slots to accommodate the number of players being
   * relocated.
   * <p>
   * Tries to get a server with enough slots for the defined number of players. If the players do
   * not need to end up on the same server, call this method for each individual player.
   * 
   * @param clusterId The id of the cluster to get a server for.
   * @param mode The mode to select a server with.
   * @param numPlayers The number of players being relocated.
   * @return The id of the best available server according to the selection criteria. An empty list
   *         if no valid server for the cluster was found whatsoever.
   * @throws IllegalArgumentException on a <code>null</code> parameter or an empty cluster id.
   * 
   * @see ServerStatus
   */
  List<String> getServers(String clusterId, ServerSelectionMode mode, int numPlayers)
      throws IllegalArgumentException;

  /**
   * Gets a human-readable string array version of the network's status.
   * <p>
   * Includes a one-line header and only includes clusters that have at least one server that is
   * considered responsive.
   * 
   * @return The network's current status, as a string array.
   */
  String[] toStringArray();

}
