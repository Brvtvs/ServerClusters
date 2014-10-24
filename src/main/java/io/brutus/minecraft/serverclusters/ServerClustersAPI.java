package io.brutus.minecraft.serverclusters;

import java.util.UUID;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Documentation for the main ServerClusters API.
 */
public interface ServerClustersAPI {

  /**
   * Gets the id of this server, as it is uniquely identified on the network to other servers,
   * proxies, etc.
   * 
   * @return This server's id.
   */
  String getServerId();

  /**
   * Gets the id of the cluster this server is a part of.
   * 
   * @return The id of this server's cluster.
   */
  String getClusterId();

  /**
   * Gets the current number of open player slots on this server.
   * 
   * @return The number of open slots for players on this server.
   */
  int getOpenSlots();

  /**
   * Gets the current total number of player slots on this game server.
   * <p>
   * Can be less than the number of players currently online.
   * 
   * @return The currently set total number of player slots.
   */
  int getTotalSlots();

  /**
   * Makes an asynchronous attempt to update the total number of player slots.
   * <p>
   * Only affects new connections; does not affect players currently online or players in the
   * process of connecting to this server.
   * <p>
   * This number minus the number of players currently online defines how many players other servers
   * can send here and affects whether they will pick this server.
   * <p>
   * May not take effect immediately. If the number of slots is reduced and there are still players
   * connecting to this server to occupy their reserved slots, this process will not complete until
   * they finish connecting or timeout. No new player connections will be authorized, but existing
   * ones will be allowed to complete. It is not safe to assume that players have stopped
   * legitimately connecting until the asynchronous future object is done.
   * <p>
   * Does not affect players currently online this server in any way. For example, it is fully
   * possible to make sure no players are sent here, such as during a match, by setting the total
   * number of slots to <code>0</code> and then waiting for this process to complete. Doing so will
   * not kick any players that are currently online.
   * <p>
   * In addition to overwriting the total number of player slots, this overrides any existing
   * uncompleted attempt to update the total slots, causing its future objects to return
   * <code>false</code>.
   * 
   * 
   * @param totalSlots The total number of player slots that connected servers can fill with
   *        players.
   * @return A future object that will update when this operation is complete. Returns
   *         <code>true</code> when the number of slots is successfully altered and any existing
   *         player connections are resolved. <code>false</code> if the process is interrupted
   *         before definitively completing.
   * @throws IllegalArgumentException on a negative value.
   */
  ListenableFuture<Boolean> updateTotalSlots(int totalSlots) throws IllegalArgumentException;

  /**
   * Makes an asynchronous attempt to send players to an instance of the given Minecraft-server
   * cluster together.
   * <p>
   * Players will be sent as a group to the same instance of the cluster. Only servers with enough
   * slots for every player will be considered. If the players can be split up, call this method
   * separately for each player. Sending players as a group unnecessarily is less efficient, less
   * optimal, and more likely to fail.
   * <p>
   * Finds the best server in the cluster based on its configuration and the number of players being
   * sent. If the cluster is not configured, picks at random.
   * <p>
   * Players will be relocated asynchronously (not immediately and not particularly fast). It will
   * take some time before an instance is found and approved and the players are relocated to it.
   * During that time, the players will still be able to run around, play on, and interact with this
   * server.
   * <p>
   * Once this process has started, it cannot be stopped. If multiple, simultaneous requests are
   * made for the same player, it may cause strange behavior. They might be bounced around between
   * servers and no guarantee is made about what order requests would be finished in. This also
   * applies for other methods of server relocation not through this method.
   * <p>
   * Ignores attempts to send players who are not currently online this server.
   * <p>
   * Can fail if no appropriate instances for the cluster are found or for any of a number of other
   * reasons.
   * 
   * @param clusterId The id of the cluster to send the players to.
   * @param players The player or players to send to an instance of the server cluster.
   * @return The asynchronous, future result of this attempt to relocate players. Returns a value
   *         when the request finishes. <code>true</code> if the relocation is successful (does not
   *         guarantee the players make it to their destination, but does successfully find a server
   *         and try to send them there). <code>false</code> if the relocation fails, such as if no
   *         valid servers exist for the given cluster.
   * @throws IllegalArgumentException on a <code>null</code> parameter or an empty cluster id.
   */
  ListenableFuture<Boolean> sendPlayersToCluster(String clusterId, UUID... playersToSend)
      throws IllegalArgumentException;

  /**
   * Makes an asynchronous attempt to send players to the Minecraft server of a given player. Takes
   * the target player's unique id as an argument.
   * <p>
   * Players will be sent as a group to the same instance of the player. The request will fail and
   * not send any players if there are not enough open slots for all players being sent. If each
   * player should be sent individually and their attempt should succeed/fail individually, then
   * this method should be called separately for each player.
   * <p>
   * Players will be relocated asynchronously (not immediately and not particularly fast). It will
   * take some time before the target player is located, their server approves the relocation, and
   * the players are sent to it. During that time, the players being sent will still be able to run
   * around, play on, and interact with this server.
   * <p>
   * Once this process has started, it cannot be stopped. If multiple, simultaneous requests are
   * made for the same player, it may cause strange behavior. They might be bounced around between
   * servers and no guarantee is made about what order requests would be finished in. This also
   * applies for other methods of server relocation not through this method.
   * <p>
   * Can fail if the target player is not currently online an appropriate connected server or for
   * any of a number of other reasons. There is no guarantee that by the time the player(s) arrive
   * at the target player's server that the target player will still be online there.
   * <p>
   * Ignores attempts to send players who are not currently online this server.
   * 
   * @param targetPlayerId The player whose server to try to send the given players to.
   * @param players The players to try to send to the server of the target player.
   * @return The asynchronous, future result of this attempt to relocate players. Returns a value
   *         when the request finishes. <code>true</code> if the relocation is successful (does not
   *         guarantee the players make it to their destination, but successfully tries to send them
   *         to the target server). <code>false</code> if the relocation fails, such as if the
   *         target player is not online a connected server.
   * @throws IllegalArgumentException on a <code>null</code> parameter.
   */
  ListenableFuture<Boolean> sendPlayersToPlayer(UUID targetPlayerId, UUID... playersToSend)
      throws IllegalArgumentException;

  /**
   * Makes an asynchronous attempt to send players to the Minecraft server of a given player. Takes
   * the target player's name as an argument.
   * <p>
   * Players will be sent as a group to the same instance of the player. The request will fail and
   * not send any players if there are not enough open slots for all players being sent. If each
   * player should be sent individually and their attempt should succeed/fail individually, then
   * this method should be called separately for each player.
   * <p>
   * Players will be relocated asynchronously (not immediately and not particularly fast). It will
   * take some time before the target player is located, their server approves the relocation, and
   * the players are sent to it. During that time, the players being sent will still be able to run
   * around, play on, and interact with this server.
   * <p>
   * Once this process has started, it cannot be stopped. If multiple, simultaneous requests are
   * made for the same player, it may cause strange behavior. They might be bounced around between
   * servers and no guarantee is made about what order requests would be finished in. This also
   * applies for other methods of server relocation not through this method.
   * <p>
   * Can fail if the target player is not currently online an appropriate connected server or for
   * any of a number of other reasons. There is no guarantee that by the time the player(s) arrive
   * at the target player's server that the target player will still be online there.
   * <p>
   * Ignores attempts to send players who are not currently online this server.
   * 
   * @param targetPlayerName The name of the player whose server to try to send the given players to
   *        (not case sensitive).
   * @param players The players to try to send to the server of the target player.
   * @return The asynchronous, future result of this attempt to relocate players. Returns a value
   *         when the request finishes. <code>true</code> if the relocation is successful (does not
   *         guarantee the players make it to their destination, but successfully tries to send them
   *         to the target server). <code>false</code> if the relocation fails, such as if the
   *         target player is not online a connected server.
   * @throws IllegalArgumentException on a <code>null</code> parameter or an empty player name.
   */
  ListenableFuture<Boolean> sendPlayersToPlayer(String targetPlayerName, UUID... playersToSend)
      throws IllegalArgumentException;

}
