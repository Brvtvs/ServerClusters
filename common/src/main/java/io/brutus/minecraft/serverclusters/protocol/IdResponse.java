package io.brutus.minecraft.serverclusters.protocol;

import io.brutus.minecraft.serverclusters.protocol.serialization.SerializationUtils;

import java.io.Serializable;

/**
 * A response to a request for a unique server id.
 */
public class IdResponse implements Serializable {

  private static final long serialVersionUID = -4525553620769064094L;

  /**
   * Creates a serialized <code>byte</code> array of an id response.
   * 
   * @param serverId A unique id for the requesting server.
   * @param request The request that is being answered.
   * @return The serialized <code>byte</code> array version of the message. Can be decoded with
   *         {@link #fromBytes(byte[])}.
   * @throws IllegalArgumentException on a <code>null</code> or empty parameter.
   */
  public static byte[] createMessage(String serverId, IdRequest request)
      throws IllegalArgumentException {
    if (request == null) {
      throw new IllegalArgumentException("request cannot be null");
    }
    return SerializationUtils.serialize(new IdResponse(serverId, request.getClusterId(), request
        .getRequestingIp(), request.getRequestingPort()));
  }

  /**
   * Gets an <code>IdResponse</code> object for a serialized <code>byte</code> array version of a
   * message.
   * 
   * @param message The <code>byte</code> array to get a <code>IdResponse</code> object for.
   * @return The decoded response.
   * @throws IllegalArgumentException on a <code>null</code> or incorrectly formatted message array.
   */
  public static IdResponse fromBytes(byte[] message) throws IllegalArgumentException {
    if (message == null || message.length < 1) {
      throw new IllegalArgumentException("message array cannot be null or empty");
    }
    try {
      return (IdResponse) SerializationUtils.deserialize(message);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException("improperly formatted add cluster message array");
    }
  }

  private final String serverId;
  private final String clusterId;
  private final String requestingIp;
  private final int requestingPort;

  private IdResponse(String serverId, String clusterId, String ip, int port) {
    if (serverId == null || serverId.isEmpty() || clusterId == null || clusterId.isEmpty()
        || ip == null || ip.isEmpty()) {
      throw new IllegalArgumentException("strings cannot be empty or null");
    }
    this.serverId = serverId;
    this.clusterId = clusterId;
    this.requestingIp = ip;
    this.requestingPort = port;
  }

  /**
   * Gets the unique server id that is being returned in this response.
   * 
   * @return A unique id for the requesting server.
   */
  public String getUniqueServerId() {
    return serverId;
  }

  /**
   * Gets the id of the cluster the requesting server is a part of.
   * 
   * @return The requester's cluster id.
   */
  public String getClusterId() {
    return clusterId;
  }

  /**
   * Gets the IP address which can be used to connect to the requesting server.
   * 
   * @return The requesting server's ip.
   */
  public String getRequestingIp() {
    return requestingIp;
  }

  /**
   * Gets the port which the requesting server is running on.
   * 
   * @return The requesting server's port.
   */
  public int getRequestingPort() {
    return requestingPort;
  }

}
