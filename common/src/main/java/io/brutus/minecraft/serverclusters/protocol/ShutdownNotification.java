package io.brutus.minecraft.serverclusters.protocol;

import java.nio.charset.Charset;

/**
 * Protocol for encoding/decoding server-shutdown messages (a notification that a server on the
 * network is shutting down and should immediately stop being considered for relocating players
 * there).
 */
public class ShutdownNotification {

  /*
   * Protocol: [byte[] serverId]
   */

  private static final Charset CHARSET = Charset.forName("UTF-8");

  /**
   * Creates a serialized <code>byte</code> array of a server-shutdown message.
   * 
   * @param serverId The id of the server to create a shutdown message for.
   * @return The serialized <code>byte</code> array version of the shutdown notification. Can be
   *         decoded with {@link #fromBytes(byte[])}.
   * @throws IllegalArgumentException on a a <code>null</code> or empty server id.
   */
  public static byte[] createMessage(String serverId) throws IllegalArgumentException {
    if (serverId == null || serverId.equals("")) {
      throw new IllegalArgumentException("server id cannot be null or empty");
    }
    return serverId.getBytes(CHARSET);
  }

  /**
   * Gets a <code>ShutdownNotification</code> object for a serialized <code>byte</code> array
   * version of a shutdown message.
   * 
   * @param message The <code>byte</code> array to get a <code>ShutdownNotification</code> object
   *        for.
   * @return The decoded message.
   * @throws IllegalArgumentException on a <code>null</code> or empty message array.
   */
  public static ShutdownNotification fromBytes(byte[] message) throws IllegalArgumentException {
    if (message == null || message.length <= 0) {
      throw new IllegalArgumentException("message array cannot be null or empty");
    }
    return new ShutdownNotification(new String(message, CHARSET));
  }

  private final String serverId;

  private ShutdownNotification(String serverId) {
    if (serverId == null || serverId.equals("")) {
      throw new IllegalArgumentException("server id cannot be null or empty");
    }
    this.serverId = serverId;
  }

  /**
   * Gets the id of the server that this shutdown message is for.
   * 
   * @return The server that is shutting down.
   */
  public String getServerId() {
    return serverId;
  }

}
