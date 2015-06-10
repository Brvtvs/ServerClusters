package io.brutus.minecraft.serverclusters.gameserver;

import io.brutus.minecraft.serverclusters.config.LocalConfiguration;
import io.brutus.minecraft.serverclusters.config.SharedConfiguration;

/**
 * Main configuration for the ServerClusters utility.
 */
public interface ServerClustersConfiguration extends SharedConfiguration, LocalConfiguration {

  /**
   * Gets the id of this server as it is recognized by proxies, other servers, etc.
   * 
   * @return The id of the server this is running on.
   */
  String getServerId();

  /**
   * Gets the id of the cluster that this server is a part of.
   * 
   * @return The cluster this server is a part of.
   */
  String getClusterId();

  /**
   * Gets how many total player slots to start with. Can be overwritten through the ServerClusters
   * API.
   * 
   * @return The total number of players slots for this server.
   */
  int getTotalSlots();

  /**
   * Gets whether to kick players when they try to join without a valid reservation, even when there
   * are open slots available.
   * 
   * @return <code>true</code> to always kick players when they log in without a reservation.
   */
  boolean strictReservations();

  /**
   * Whether this instance should periodically attempt to consolidate its players into another, more
   * ideal instance.
   * 
   * @return <code>true</code> if this server should attempt to consolidate its players into more
   *         ideal instances of the same cluster.
   */
  boolean attemptInstanceConsolidations();

}
