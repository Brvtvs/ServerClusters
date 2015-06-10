package io.brutus.minecraft.serverclusters.protocol;

import java.util.Set;
import java.util.UUID;

/**
 * A reservation request targeted at a specific player by their name. The server on which the target
 * player is online is the intended recipient of this request.
 */
public class PlayerNameReservationRequest extends ReservationRequest {

  private static final long serialVersionUID = 3146229448989352008L;

  private final String targetPlayerName;

  PlayerNameReservationRequest(String targetPlayerName, String requestingServer, int requestId,
      Set<UUID> players) throws IllegalArgumentException {

    super(TargetType.PLAYER_NAME, requestingServer, requestId, players);

    if (targetPlayerName == null || targetPlayerName.equals("")) {
      throw new IllegalArgumentException("target player's name cannot be null or empty");
    }
    this.targetPlayerName = targetPlayerName;
  }

  /**
   * Gets the name of the player this reservation is targeted at. The server where this player
   * online is the intended recipient of this message.
   * 
   * @return The target player's name.
   */
  public final String getTargetPlayerName() {
    return targetPlayerName;
  }


}
