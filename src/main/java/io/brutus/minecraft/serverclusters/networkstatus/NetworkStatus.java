package io.brutus.minecraft.serverclusters.networkstatus;

import java.util.List;

import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

/**
 * A store of information about connected servers, updated with heartbeat and shutdown messages.
 */
public interface NetworkStatus {

  /**
   * Gets an ordered list of cached data about available servers. Orders by the given selection
   * mode, only including responsive servers with enough slots to accommodate the number of players
   * being relocated.
   * <p>
   * Tries to get a server with enough slots for the defined number of players. If the players do
   * not need to end up on the same server, call this method for each individual player.
   * 
   * @param clusterId The id of the cluster to get a server for.
   * @param mode The mode to select a server with.
   * @param numPlayers The number of players being relocated.
   * @return An ordered list of the best available servers according to the selection criteria. An
   *         empty list if no valid server for the cluster was found whatsoever.
   * @throws IllegalArgumentException on a <code>null</code> parameter or an empty cluster id.
   * 
   * @see ServerStatus
   */
  List<ServerStatus> getServers(String clusterId, ServerSelectionMode mode, int numPlayers)
      throws IllegalArgumentException;

  /**
   * Gets the number of server instances in a cluster on the network.
   * <p>
   * The result is based on cached data and may be slightly out of date.
   * 
   * @param clusterId The id of the cluster to get the size of.
   * @return The number of responding servers currently in the given cluster. <code>0</code> if no
   *         instances are up or if there is no cluster for the given id.
   */
  int getClusterSize(String clusterId);

  /**
   * Registers a listener to be informed when connected servers come online or go offline.
   * 
   * @param listener The listener to inform of servers starting/stopping.
   */
  void registerListener(NetworkChangeListener listener);

  /**
   * Removes a listener from being informed when connected servers come online or go offline.
   * 
   * @param listener The listener to stop informing of servers starting/stopping.
   */
  void unregisterListener(NetworkChangeListener listener);

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
