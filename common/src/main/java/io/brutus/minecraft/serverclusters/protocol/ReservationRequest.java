package io.brutus.minecraft.serverclusters.protocol;

import io.brutus.minecraft.serverclusters.protocol.serialization.SerializationUtils;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

/**
 * Protocol for requesting a reservation on a number of slots on a connected server.
 */
public abstract class ReservationRequest implements Serializable {

  private static final long serialVersionUID = 8143141308175013438L;

  /**
   * Creates a serialized <code>byte</code> array of a reservation request targeted at a given
   * server.
   * 
   * @param targetServer The id of the server to request slots from.
   * @param requestingServer The id of the server making the request.
   * @param requestId A unique identified for this request. The response will reference this request
   *        in order to make clear what it is responding to. The id should be unique within the
   *        server sending the request. No definite scheme for defining ids is defined.
   * @param players The players the requested slots are for.
   * @return The serialized <code>byte</code> array version of the request. Can be decoded with
   *         {@link #fromBytes(byte[])}.
   * @throws IllegalArgumentException on a <code>null</code> or empty target or requesting server
   *         id, or on less than <code>1</code> player being passed in.
   */
  public static byte[] createMessageToServer(String targetServer, String requestingServer,
      int requestId, Set<UUID> players) throws IllegalArgumentException {

    return SerializationUtils.serialize(new ServerIdReservationRequest(targetServer,
        requestingServer, requestId, players));

  }

  /**
   * Creates a serialized <code>byte</code> array of a reservation request targeted at a given
   * player using their unique id.
   * 
   * @param targetPlayerUniqueId The id of the target player.
   * @param requestingServer The id of the server making the request.
   * @param requestId A unique identified for this request. The response will reference this request
   *        in order to make clear what it is responding to. The id should be unique within the
   *        server sending the request. No definite scheme for defining ids is defined.
   * @param players The players the requested slots are for.
   * @return The serialized <code>byte</code> array version of the request. Can be decoded with
   *         {@link #fromBytes(byte[])}.
   * @throws IllegalArgumentException on a <code>null</code> parameter or empty requesting server
   *         id, or on less than <code>1</code> player being passed in.
   */
  public static byte[] createMessageToPlayer(UUID targetPlayerUniqueId, String requestingServer,
      int requestId, Set<UUID> players) throws IllegalArgumentException {

    return SerializationUtils.serialize(new PlayerUuidReservationRequest(targetPlayerUniqueId,
        requestingServer, requestId, players));

  }

  /**
   * Creates a serialized <code>byte</code> array of a reservation request targeted at a given
   * player using their name.
   * 
   * @param targetPlayerName The name of the target player.
   * @param requestingServer The id of the server making the request.
   * @param requestId A unique identified for this request. The response will reference this request
   *        in order to make clear what it is responding to. The id should be unique within the
   *        server sending the request. No definite scheme for defining ids is defined.
   * @param players The players the requested slots are for.
   * @return The serialized <code>byte</code> array version of the request. Can be decoded with
   *         {@link #fromBytes(byte[])}.
   * @throws IllegalArgumentException on a <code>null</code> or empty requesting server id or player
   *         name, or on less than <code>1</code> player being passed in.
   */
  public static byte[] createMessageToPlayer(String targetPlayerName, String requestingServer,
      int requestId, Set<UUID> players) throws IllegalArgumentException {

    return SerializationUtils.serialize(new PlayerNameReservationRequest(targetPlayerName,
        requestingServer, requestId, players));

  }

  /**
   * Gets a <code>ReservationRequest</code> object for a serialized <code>byte</code> array version
   * of a request.
   * 
   * @param message The <code>byte</code> array to get a <code>ReservationRequest</code> object for.
   * @return The decoded request. Its type can be ascertained with {@link #getTargetType()}.
   * @throws IllegalArgumentException on a <code>null</code> or improperly formatted message array,
   *         or on a number of players that is not positive.
   */
  public static ReservationRequest fromBytes(byte[] message) throws IllegalArgumentException {
    if (message == null || message.length < 1) {
      throw new IllegalArgumentException("message array cannot be null or empty");
    }
    try {
      return (ReservationRequest) SerializationUtils.deserialize(message);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException("inproperly formatted reservation request message");
    }
  }

  private final TargetType type;

  private final String requestingServer;
  private final int requestId;
  private final Set<UUID> players;

  protected ReservationRequest(TargetType type, String requestingServer, int requestId,
      Set<UUID> players) {
    if (type == null || requestingServer == null || requestingServer.equals("")) {
      throw new IllegalArgumentException("type and server id cannot be null or empty");
    }
    if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("request must have at least one player");
    }

    this.type = type;

    this.requestingServer = requestingServer;
    this.requestId = requestId;
    this.players = players;
  }

  /**
   * Gets the type of this request. Can be use to cast and interpret the this request's target.
   * 
   * @return This request's target.
   */
  public TargetType getTargetType() {
    return type;
  }

  /**
   * Gets the id of the server that is making the reservation request.
   * 
   * @return The requesting server's string id.
   */
  public String getRequestingServer() {
    return requestingServer;
  }

  /**
   * Gets the unique id of this request. Should be returned with the response in order to identify
   * what exactly is being responded to.
   * 
   * @return This request's id.
   */
  public int getRequestId() {
    return requestId;
  }

  /**
   * Gets the number of slots that the requester is trying to reserve.
   * 
   * @return The number of slots requested.
   */
  public int getSlotsRequested() {
    return players.size();
  }

  /**
   * Gets the players this request is for.
   * <p>
   * For the sake of efficiency, does not clone the set. It should not be edited or exposed to
   * clients.
   * 
   * @return The players this reservation request is for.
   */
  public Set<UUID> getPlayers() {
    return players;
  }

  /**
   * The different supported types of reservation request targets.
   */
  public enum TargetType {
    SERVER_ID(), PLAYER_UUID(), PLAYER_NAME();
  }

}
