package io.brutus.minecraft.serverclusters.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Protocol for answering a request to reserve a set of player slots on a connected server.
 */
public class ReservationResponse {

  /*
   * Protocol: [int targetIdLength, byte[] targetId, int respondingIdLength, byte[] respondingId,
   * int requestId, boolean approved]
   */

  private static final Charset CHARSET = Charset.forName("UTF-8");
  // 2 string length definitions, 1 request id, and 1 byte for a boolean value
  private static final int BASE_LENGTH = ((Integer.SIZE / 8) * 3) + Byte.SIZE / 8;

  /**
   * Creates a serialized <code>byte</code> array of a reservation response.
   * 
   * @param targetServer The server the response is being sent to. (The server that originally made
   *        the request)
   * @param respondingServer The server sending the response (The server that the request was made
   *        to).
   * @param requestId The id of the original request.
   * @param approved <code>true</code> if the request is approved and the target server can start
   *        filling the slots is requested. <code>false</code> if the target server must find
   *        another instance for its players.
   * @return The serialized <code>byte</code> array version of the response. Can be decoded with
   *         {@link #fromBytes(byte[])}.
   * @throws IllegalArgumentException on a <code>null</code> or empty server id.
   */
  public static byte[] createMessage(String targetServer, String respondingServer, int requestId,
      boolean approved) throws IllegalArgumentException {
    if (targetServer == null || targetServer.equals("") || respondingServer == null
        || respondingServer.equals("")) {
      throw new IllegalArgumentException("server ids cannot be null or empty");
    }

    byte[] targetBytes = targetServer.getBytes(CHARSET);
    byte[] respondingBytes = respondingServer.getBytes(CHARSET);

    ByteBuffer bb = ByteBuffer.allocate(targetBytes.length + respondingBytes.length + BASE_LENGTH);

    bb.putInt(targetBytes.length);
    bb.put(targetBytes);

    bb.putInt(respondingBytes.length);
    bb.put(respondingBytes);

    bb.putInt(requestId);

    if (approved) {
      bb.put((byte) 1);
    } else {
      bb.put((byte) 0);
    }

    return bb.array();
  }

  /**
   * Gets a <code>ReservationResponse</code> object for a serialized <code>byte</code> array version
   * of a response.
   * 
   * @param message The <code>byte</code> array to get a <code>ReservationResponse</code> object
   *        for.
   * @return The decoded response.
   * @throws IllegalArgumentException on a <code>null</code> or incorrectly formatted message array.
   */
  public static ReservationResponse fromBytes(byte[] message) throws IllegalArgumentException {
    if (message == null || message.length < BASE_LENGTH) {
      throw new IllegalArgumentException("improperly formatted reservation response message array");
    }

    try {
      ByteBuffer bb = ByteBuffer.wrap(message);

      int targetIdLength = bb.getInt();
      byte[] targetBytes = new byte[targetIdLength];
      bb.get(targetBytes);
      String targetServer = new String(targetBytes, CHARSET);

      int respondingIdLength = bb.getInt();
      byte[] respondingBytes = new byte[respondingIdLength];
      bb.get(respondingBytes);
      String respondingServer = new String(respondingBytes, CHARSET);

      int requestId = bb.getInt();

      byte approvedByte = bb.get();
      boolean approved = false;
      if (approvedByte == 1) {
        approved = true;
      }

      return new ReservationResponse(targetServer, respondingServer, requestId, approved);

    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException("improperly formatted reservation response message array");
    }
  }

  private final String targetServer;
  private final String respondingServer;
  private final int requestId;
  private final boolean approved;

  private ReservationResponse(String targetServer, String respondingServer, int requestId,
      boolean approved) throws IllegalArgumentException {
    if (targetServer == null || targetServer.equals("") || respondingServer == null
        || respondingServer.equals("")) {
      throw new IllegalArgumentException("server ids cannot be null or empty");
    }
    this.targetServer = targetServer;
    this.respondingServer = respondingServer;
    this.requestId = requestId;
    this.approved = approved;
  }

  /**
   * Gets the server this response is being sent to.
   * 
   * @return The id of this response's destination.
   */
  public String getTargetServer() {
    return targetServer;
  }

  /**
   * Gets the server sending this response.
   * 
   * @return The responding server's id.
   */
  public String getRespondingServer() {
    return respondingServer;
  }

  /**
   * Gets the id of the request that this is a response to.
   * 
   * @return The original request's id.
   */
  public int getRequestId() {
    return requestId;
  }

  /**
   * Gets whether the original request was approved by the responding server.
   * 
   * @return <code>true</code> if the requesting server can start sending its players to fill the
   *         slots it requested. <code>false</code> if the request was denied and the requesting
   *         server needs to find another instance.
   */
  public boolean isApproved() {
    return approved;
  }


}
