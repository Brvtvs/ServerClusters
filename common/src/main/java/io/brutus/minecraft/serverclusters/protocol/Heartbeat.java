package io.brutus.minecraft.serverclusters.protocol;

import io.brutus.minecraft.serverclusters.protocol.serialization.Encoding;

import java.nio.ByteBuffer;

/**
 * Protocol for encoding/decoding heartbeat messages from servers and clusters on the network.
 * <p>
 * Serialization is done manually, in large part to efficiently allow editing of the same heartbeat
 * message over and over again for reuse with minor changes.
 */
public class Heartbeat {

  /*
   * Protocol: (int clusterIdLength, byte[] clusterId, int serverIdLength, byte[] serverId, int
   * serverIpLength, byte[] serverIp, int serverPort, int openSlots)
   */

  // 3 ints, for number of open slots and 2 id lengths.
  private static final int BASE_LENGTH = ((Integer.SIZE / 8) * 5);

  /**
   * Creates a serialized <code>byte</code> array of a heartbeat message.
   * 
   * @param clusterId The id of the cluster of the server the heartbeat is for.
   * @param serverId The id of the server the heartbeat is for.
   * @param serverIp The ip address of the server the heartbeat is for.
   * @param serverPort The port of the server the heartbeat is for.
   * @param openSlots The number of open slots the server has.
   * @return The serialized <code>byte</code> array version of the heartbeat. Can be decoded with
   *         {@link #fromBytes(byte[])}.
   * @throws IllegalArgumentException on a <code>null</code> or empty parameter or on a negative
   *         number of open slots.
   */
  public static byte[] createMessage(String clusterId, String serverId, String serverIp,
      int serverPort, int openSlots) throws IllegalArgumentException {
    if (clusterId == null || clusterId.equals("") || serverId == null || serverId.equals("")
        || serverIp == null || serverIp.equals("")) {
      throw new IllegalArgumentException("strings cannot be null or empty");
    }
    if (openSlots < 0) {
      throw new IllegalArgumentException("open slots cannot be negative");
    }

    byte[] clusterBytes = clusterId.getBytes(Encoding.CHARSET);
    byte[] serverBytes = serverId.getBytes(Encoding.CHARSET);
    byte[] ipBytes = serverIp.getBytes(Encoding.CHARSET);

    int messageLength = clusterBytes.length + serverBytes.length + ipBytes.length + BASE_LENGTH;
    ByteBuffer bb = ByteBuffer.allocate(messageLength);

    bb.putInt(clusterBytes.length);
    bb.put(clusterBytes);

    bb.putInt(serverBytes.length);
    bb.put(serverBytes);

    bb.putInt(ipBytes.length);
    bb.put(ipBytes);

    bb.putInt(serverPort);

    bb.putInt(openSlots);

    return bb.array();
  }

  /**
   * Updates an existing serialized <code>byte</code> array heartbeat with a new number of open
   * slots.
   * <p>
   * Open slots will change constantly, whereas the server and cluster ids will not. Updating an
   * existing message saves time and memory.
   * <p>
   * Modifies the exact array passed in. Does not copy it.
   * 
   * @param message The existing message to modify.
   * @param newOpenSlots The new number of slots the message should contain.
   * @throws IllegalArgumentException on a <code>null</code> or incorrectly formatted message array,
   *         or on a negative number of open slots.
   */
  public static void updateMessage(byte[] message, int newOpenSlots)
      throws IllegalArgumentException {
    if (message == null || message.length < BASE_LENGTH) {
      throw new IllegalArgumentException("message not a heartbeat message or incorrectly formatted");
    }
    if (newOpenSlots < 0) {
      throw new IllegalArgumentException("open slots cannot be negative");
    }

    try {
      ByteBuffer bb = ByteBuffer.wrap(message);
      bb.putInt(message.length - (Integer.SIZE / 8), newOpenSlots);

    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException("improperly formatted heartbeat message array");
    }
  }

  /**
   * Gets a <code>Heartbeat</code> object for a serialized <code>byte</code> array version of a
   * heartbeat.
   * 
   * @param message The <code>byte</code> array to get a <code>Heartbeat</code> object for.
   * @return The decoded message.
   * @throws IllegalArgumentException on a <code>null</code> or incorrectly formatted message array
   *         or a negative number of open slots.
   */
  public static Heartbeat fromBytes(byte[] message) throws IllegalArgumentException {
    if (message == null || message.length < BASE_LENGTH) {
      throw new IllegalArgumentException("message not a heartbeat message or incorrectly formatted");
    }

    try {
      ByteBuffer bb = ByteBuffer.wrap(message);

      int clusterIdLength = bb.getInt();
      byte[] clusterBytes = new byte[clusterIdLength];
      bb.get(clusterBytes);
      String clusterId = new String(clusterBytes, Encoding.CHARSET);

      int serverIdLength = bb.getInt();
      byte[] serverBytes = new byte[serverIdLength];
      bb.get(serverBytes);
      String serverId = new String(serverBytes, Encoding.CHARSET);

      int serverIpLength = bb.getInt();
      byte[] ipBytes = new byte[serverIpLength];
      bb.get(ipBytes);
      String serverIp = new String(ipBytes, Encoding.CHARSET);

      int serverPort = bb.getInt();

      int openSlots = bb.getInt();

      return new Heartbeat(clusterId, serverId, serverIp, serverPort, openSlots);

    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException("improperly formatter heartbeat message array");
    }
  }

  private final String clusterId;
  private final String serverId;
  private final String serverIp;
  private final int serverPort;
  private final int openSlots;

  private Heartbeat(String clusterId, String serverId, String serverIp, int serverPort,
      int openSlots) {
    if (clusterId == null || serverId == null || clusterId.equals("") || serverId.equals("")) {
      throw new IllegalArgumentException("ids cannot be null or empty");
    }
    if (openSlots < 0) {
      throw new IllegalArgumentException("open slots cannot be negative");
    }
    this.clusterId = clusterId;
    this.serverId = serverId;
    this.serverIp = serverIp;
    this.serverPort = serverPort;
    this.openSlots = openSlots;
  }

  /**
   * Gets the id of the cluster of the server this heartbeat is for.
   * 
   * @return This heartbeat's cluster.
   */
  public String getClusterId() {
    return clusterId;
  }

  /**
   * Gets the id of the server this heartbeat is for.
   * 
   * @return This heartbeat's server.
   */
  public String getServerId() {
    return serverId;
  }

  /**
   * Gets the IP of the server this heartbeat is for.
   * 
   * @return This heartbeat's server ip.
   */
  public String getServerIp() {
    return serverIp;
  }

  /**
   * Gets the connection port of the server this heartbeat is for.
   * 
   * @return This heartbeat's server port.
   */
  public int getServerPort() {
    return serverPort;
  }

  /**
   * Gets how many open slots that the server this heartbeat is for currently has.
   * 
   * @return The server's number of open player slots.
   */
  public int getOpenSlots() {
    return openSlots;
  }

}
