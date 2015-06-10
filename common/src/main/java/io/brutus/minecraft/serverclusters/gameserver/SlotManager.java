package io.brutus.minecraft.serverclusters.gameserver;

import java.util.Set;
import java.util.UUID;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Manager for the player slots on servers managed by ServerClusters.
 */
public interface SlotManager {

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
  ListenableFuture<Boolean> setTotalSlots(int totalSlots) throws IllegalArgumentException;

  /**
   * Gets the current total number of player slots on this Minecraft server.
   * <p>
   * This number can change over time and without affecting online players. As such, the total
   * number of slots does not reflect how many players are online.
   * 
   * @return The currently set total number of player slots.
   */
  int getTotalSlots();

  /**
   * Gets the current number of open slots on this server.
   * <p>
   * This number is the current total number of slots minus the number of players online and the
   * number of players in the process of connecting to this server.
   * 
   * @return The number of open slots on this server.
   */
  int getOpenSlots();

  /**
   * Attempts to get a reservation on player slots for a set of players.
   * <p>
   * A reservation allows players connecting to this server to log in. Players without reservations
   * will be kicked when they try to join.
   * <p>
   * Reservations will expire after a configured timeout.
   * <p>
   * Succeeds or fails for all players passed in. To try to get reservations for individual players
   * that succeed or fail individually, invoke this method for each player individually.
   * 
   * @param players The players to request reservation on slots for.
   * @return <code>true</code> if slots are successfully reserved for all given players.
   *         <code>false</code> if this server cannot currently accommodate all of the given
   *         players.
   * @throws IllegalArgumentException on a <code>null</code> or empty set of players.
   */
  boolean getReservation(Set<UUID> players);

}
