package io.brutus.minecraft.serverclusters.gameserver;

import io.brutus.minecraft.serverclusters.sendplayer.PlayerSender;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Necessary utilities for interacting with an arbitrary server platform in a generic way.
 * <p>
 * By using this interface, clients can get access to fundamentally necessary server tasks without
 * directly referencing any specific implementation or platform-specific API of an implementation.
 */
public interface ServerUtils extends PlayerSender {

  /**
   * Gets the IP that this server is bound to.
   * 
   * @return This server's IP.
   */
  String getServerIp();

  /**
   * Gets the port that this server is running on.
   * 
   * @return This server's port.
   */
  int getServerPort();

  /**
   * Gets a logger for this server.
   * 
   * @return A logger.
   */
  Logger getLogger();

  /**
   * Gets whether there is a player online with the given unique id.
   * 
   * @param id The id to search with.
   * @return <code>true</code> if the player is currently online.
   */
  boolean isPlayerOnline(UUID id);

  /**
   * Gets whether there is a player online with the exact given name (not case sensitive).
   * 
   * @param name The name to search with.
   * @return <code>true</code> if the player is currently online.
   */
  boolean isPlayerOnline(String name);

  /**
   * Gets a set of the unique ids of all the players on the server.
   * <p>
   * This method is NOT thread safe. If you need to run this from a thread other than the server's
   * main thread, you need to sync it first, such as with {@link #sync(Runnable)}.
   * 
   * @return The unique ids of all online players.
   */
  Set<UUID> getOnlinePlayerIds();

  /**
   * Sends a message to one or a group of players.
   * <p>
   * This method is thread safe and synchronizes to the main thread before the message is sent.
   * 
   * @param message The message to send.
   * @param targets The ids of players to send the messages to.
   */
  void sendMessage(final String message, Collection<UUID> targets);

  /**
   * Synchronizes a task to this server's main thread.
   * 
   * @param task The task to run on the main thread.
   * @throws IllegalStateException on being unable to get a plugin instance to synchronize with.
   * @throws IllegalArgumentException on a <code>null</code> task.
   */
  void sync(Runnable task) throws IllegalStateException, IllegalArgumentException;

}
