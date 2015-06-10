package io.brutus.minecraft.serverclusters.protocol;

import java.util.Set;
import java.util.UUID;

/**
 * A reservation request targeted at a specific player by their unique id. The server on which the
 * target player is online is the intended recipient of this request.
 */
public class PlayerUuidReservationRequest extends ReservationRequest {

  private static final long serialVersionUID = 6095655508911952935L;

  private final UUID targetPlayerId;

  PlayerUuidReservationRequest(UUID targetPlayerUniqueId, String requestingServer, int requestId,
      Set<UUID> players) throws IllegalArgumentException {

    super(TargetType.PLAYER_UUID, requestingServer, requestId, players);

    if (targetPlayerUniqueId == null) {
      throw new IllegalArgumentException("target player's id cannot be null");
    }
    this.targetPlayerId = targetPlayerUniqueId;
  }

  /**
   * Gets the unique id of the player this reservation is targeted at. The server where this player
   * online is the intended recipient of this message.
   * 
   * @return The target player's name.
   */
  public final UUID getTargetPlayerUniqueId() {
    return targetPlayerId;
  }


}
