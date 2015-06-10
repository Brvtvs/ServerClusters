package io.brutus.minecraft.serverclusters.protocol;

import io.brutus.minecraft.serverclusters.protocol.serialization.SerializationUtils;

import java.io.Serializable;

/**
 * A request for a unique server id.
 */
public class IdRequest implements Serializable {

  private static final long serialVersionUID = -419917864200196289L;

  /**
   * Creates a serialized <code>byte</code> array of an id request.
   * 
   * @param clusterId The id of the cluster that the requesting server is part of.
   * @param ip The ip of the server making the request. Will be used to help identify the
   *        destination of the response.
   * @param port The port of the server making the request. Will be used to help identify the
   *        destination of the response.
   * @return The serialized <code>byte</code> array version of the message. Can be decoded with
   *         {@link #fromBytes(byte[])}.
   * @throws IllegalArgumentException on a <code>null</code> or empty string.
   */
  public static byte[] createMessage(String clusterId, String ip, int port)
      throws IllegalArgumentException {
    return SerializationUtils.serialize(new IdRequest(clusterId, ip, port));
  }

  /**
   * Gets an <code>IdRequest</code> object for a serialized <code>byte</code> array version of a
   * message.
   * 
   * @param message The <code>byte</code> array to get a <code>IdRequest</code> object for.
   * @return The decoded response.
   * @throws IllegalArgumentException on a <code>null</code> or incorrectly formatted message array.
   */
  public static IdRequest fromBytes(byte[] message) throws IllegalArgumentException {
    if (message == null || message.length < 1) {
      throw new IllegalArgumentException("message array cannot be null or empty");
    }
    try {
      return (IdRequest) SerializationUtils.deserialize(message);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException("improperly formatted add cluster message array");
    }
  }

  private final String clusterId;
  private final String requestingIp;
  private final int requestingPort;

  private IdRequest(String clusterId, String ip, int port) {
    if (clusterId == null || clusterId.isEmpty() || ip == null || ip.isEmpty()) {
      throw new IllegalArgumentException("strings cannot be empty or null");
    }
    this.clusterId = clusterId;
    this.requestingIp = ip;
    this.requestingPort = port;
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

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((clusterId == null) ? 0 : clusterId.hashCode());
    result = prime * result + ((requestingIp == null) ? 0 : requestingIp.hashCode());
    result = prime * result + requestingPort;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    IdRequest other = (IdRequest) obj;
    if (clusterId == null) {
      if (other.clusterId != null)
        return false;
    } else if (!clusterId.equals(other.clusterId))
      return false;
    if (requestingIp == null) {
      if (other.requestingIp != null)
        return false;
    } else if (!requestingIp.equals(other.requestingIp))
      return false;
    if (requestingPort != other.requestingPort)
      return false;
    return true;
  }

}
