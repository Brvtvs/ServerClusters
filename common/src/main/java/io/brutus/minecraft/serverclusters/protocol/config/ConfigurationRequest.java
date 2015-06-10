package io.brutus.minecraft.serverclusters.protocol.config;

/**
 * A request for the network-wide configuration.
 */
public class ConfigurationRequest {

  /**
   * Creates a serialized <code>byte</code> array of a configuration request.
   * 
   * @return The serialized <code>byte</code> array version of the message. Can be decoded with
   *         {@link #fromBytes(byte[])}.s
   */
  public static byte[] createMessage() {
    return new byte[1];
  }

  /**
   * Gets an <code>ConfigurationRequest</code> object for a serialized <code>byte</code> array
   * version of a message.
   * 
   * @param message The <code>byte</code> array to get a <code>ConfigurationRequest</code> object
   *        for.
   * @return The decoded response.
   */
  public static ConfigurationRequest fromBytes(byte[] message) {
    return new ConfigurationRequest();
  }

  private ConfigurationRequest() {}

}
