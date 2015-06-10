package io.brutus.minecraft.serverclusters.protocol;

import java.util.Set;
import java.util.UUID;

/**
 * A reservation request targeted at a specific server by its id.
 */
public class ServerIdReservationRequest extends ReservationRequest {

  private static final long serialVersionUID = -970482228095698099L;

  private final String targetServerId;

  ServerIdReservationRequest(String targetServerId, String requestingServer, int requestId,
      Set<UUID> players) throws IllegalArgumentException {

    super(TargetType.SERVER_ID, requestingServer, requestId, players);

    if (targetServerId == null || targetServerId.equals("")) {
      throw new IllegalArgumentException("target server id cannot be null or empty");
    }
    this.targetServerId = targetServerId;
  }

  /**
   * Gets the id of the server this reservation is targeted at.
   * 
   * @return The target server's id.
   */
  public final String getTargetServer() {
    return targetServerId;
  }


}
