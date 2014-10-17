package io.brutus.minecraft.serverclusters.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Protocol for requesting a reservation on a number of slots on a connected server.
 */
public class ReservationRequest {

  /*
   * Protocol: [int targetIdLength, byte[] targetId, int requesterIdLength, byte[] requesterId, int
   * requestId, int numPlayers, UUID... players]
   */

  private static final Charset CHARSET = Charset.forName("UTF-8");
  // 3 ints, for the request id, the number of players, and 2 string id lengths.
  private static final int BASE_LENGTH = ((Integer.SIZE / 8) * 4);

  /**
   * Creates a serialized <code>byte</code> array of a reservation request.
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
  public static byte[] createMessage(String targetServer, String requestingServer, int requestId,
      Set<UUID> players) throws IllegalArgumentException {
    if (targetServer == null || targetServer.equals("") || requestingServer == null
        || requestingServer.equals("")) {
      throw new IllegalArgumentException("target and requesting server ids cannot be null or empty");
    }
    if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("must have at least one player");
    }

    byte[] targetBytes = targetServer.getBytes(CHARSET);
    byte[] requestingBytes = requestingServer.getBytes(CHARSET);

    int messageLength =
        targetBytes.length + requestingBytes.length + (players.size() * (Long.SIZE / 8) * 2)
            + BASE_LENGTH;
    ByteBuffer bb = ByteBuffer.allocate(messageLength);

    bb.putInt(targetBytes.length);
    bb.put(targetBytes);

    bb.putInt(requestingBytes.length);
    bb.put(requestingBytes);

    bb.putInt(requestId);

    bb.putInt(players.size());

    for (UUID uid : players) {
      bb.putLong(uid.getMostSignificantBits());
      bb.putLong(uid.getLeastSignificantBits());
    }

    return bb.array();
  }

  /**
   * Gets a <code>ReservationRequest</code> object for a serialized <code>byte</code> array version
   * of a request.
   * 
   * @param message The <code>byte</code> array to get a <code>ReservationRequest</code> object for.
   * @return The decoded request.
   * @throws IllegalArgumentException on a <code>null</code> or improperly formatted message array,
   *         or on a number of players that is not positive.
   */
  public static ReservationRequest fromBytes(byte[] message) throws IllegalArgumentException {
    if (message == null || message.length < BASE_LENGTH) {
      throw new IllegalArgumentException("message array cannot be null or empty");
    }

    try {
      ByteBuffer bb = ByteBuffer.wrap(message);

      int targetIdLength = bb.getInt();
      byte[] targetBytes = new byte[targetIdLength];
      bb.get(targetBytes);
      String targetServer = new String(targetBytes, CHARSET);

      int requestingIdLength = bb.getInt();
      byte[] requestingBytes = new byte[requestingIdLength];
      bb.get(requestingBytes);
      String requestingServer = new String(requestingBytes, CHARSET);

      int requestId = bb.getInt();
      int numPlayers = bb.getInt();

      Set<UUID> players = new HashSet<UUID>();
      for (int i = 0; i < numPlayers; i++) {
        players.add(new UUID(bb.getLong(), bb.getLong()));
      }

      return new ReservationRequest(targetServer, requestingServer, requestId, players);

    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException("inproperly formatted reservation request message");
    }
  }

  private final String targetServer;
  private final String requestingServer;
  private final int requestId;
  private final Set<UUID> players;

  private ReservationRequest(String targetServer, String requestingServer, int requestId,
      Set<UUID> players) {
    if (targetServer == null || targetServer.equals("") || requestingServer == null
        || requestingServer.equals("")) {
      throw new IllegalArgumentException("server ids cannot be null or empty");
    }
    if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("request must have at least one player");
    }

    this.targetServer = targetServer;
    this.requestingServer = requestingServer;
    this.requestId = requestId;
    this.players = players;
  }

  /**
   * Gets the id of the server that the requester is trying to reserve slots on.
   * 
   * @return The target server's string id.
   */
  public String getTargetServer() {
    return targetServer;
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

}
