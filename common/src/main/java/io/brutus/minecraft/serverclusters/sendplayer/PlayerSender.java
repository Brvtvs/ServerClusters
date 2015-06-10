package io.brutus.minecraft.serverclusters.sendplayer;

import java.util.UUID;

/**
 * Sends players to servers on the network, given their id.
 */
public interface PlayerSender {

  /**
   * Sends a player to a connected server.
   * <p>
   * The given player may need to be online this server/proxy/node in order for this to work.
   * 
   * @param playerId The player to send.
   * @param destinationServer The id of the server to send the player to, as it is known to their
   *        current network proxy.
   * @throws IllegalStateException on being unable to access the resources required to send the
   *         player.
   * @throws IllegalArgumentException on a <code>null</code> parameter or an empty server name.
   */
  void sendPlayer(final UUID playerId, final String destinationServer)
      throws IllegalStateException, IllegalArgumentException;

}
