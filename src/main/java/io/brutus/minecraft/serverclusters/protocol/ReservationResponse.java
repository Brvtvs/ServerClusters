package io.brutus.minecraft.serverclusters.protocol;

import io.brutus.minecraft.serverclusters.protocol.serialization.SerializationUtils;

import java.io.Serializable;

/**
 * Protocol for answering a request to reserve a set of player slots on a connected server.
 */
public class ReservationResponse implements Serializable {

  private static final long serialVersionUID = -5346588569921043743L;

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

    return SerializationUtils.serialize(new ReservationResponse(targetServer, respondingServer,
        requestId, approved));

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
    if (message == null || message.length < 1) {
      throw new IllegalArgumentException("message array cannot be null or empty");
    }

    try {
      return (ReservationResponse) SerializationUtils.deserialize(message);
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
