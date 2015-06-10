package io.brutus.minecraft.serverclusters.protocol.config;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

import io.brutus.minecraft.serverclusters.config.SharedConfiguration;
import io.brutus.minecraft.serverclusters.protocol.serialization.SerializationUtils;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

/**
 * A message that communicates the contents of the centralized, network-wide config.
 */
public class ConfigurationMessage implements SharedConfiguration, Serializable {

  private static final long serialVersionUID = 7218826520878476483L;

  /**
   * Gets a new builder which can be used to construct a configuration message.
   * 
   * @return A configuration-message builder.
   */
  public static ConfigurationMessageBuilder builder() {
    return new ConfigurationMessageBuilder();
  }

  /**
   * Creates a serialized <code>byte</code> array of a configuration message.
   * 
   * @param serialize The configuration message to serialize.
   * @return The serialized <code>byte</code> array version of the message. Can be decoded with
   *         {@link #fromBytes(byte[])}.
   * @throws NullPointerException on a <code>null</code> configuration message.
   */
  public static byte[] createMessage(ConfigurationMessage serialize) throws NullPointerException {
    if (serialize == null) {
      throw new NullPointerException();
    }
    return SerializationUtils.serialize(serialize);
  }

  /**
   * Gets an <code>ConfigurationMessage</code> object for a serialized <code>byte</code> array
   * version of a message.
   * 
   * @param message The <code>byte</code> array to get a <code>ConfigurationMessage</code> object
   *        for.
   * @return The decoded response.
   * @throws IllegalArgumentException on a <code>null</code> or incorrectly formatted message array.
   */
  public static ConfigurationMessage fromBytes(byte[] message) throws IllegalArgumentException {
    if (message == null || message.length < 1) {
      throw new IllegalArgumentException("message array cannot be null or empty");
    }
    try {
      return (ConfigurationMessage) SerializationUtils.deserialize(message);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException("improperly formatted configuration message array");
    }
  }

  ConcurrentHashMap<String, ServerSelectionMode> clusters;

  byte[] idRequestChannel;
  byte[] idResponseChannel;
  byte[] heartbeatChannel;
  byte[] shutdownChannel;
  byte[] reservationRequestChannel;
  byte[] reservationResponseChannel;

  long minHeartRate;
  long maxHeartRate;
  long serverTimeout;
  long reservationResponseTimeout;
  long reservationTimeout;

  ConfigurationMessage() {
    this.clusters = new ConcurrentHashMap<String, ServerSelectionMode>();
  }

  /**
   * Gets whether this object has been correctly constructed.
   * 
   * @return <code>true</code> if this is a theoretically usable configuration (which still might be
   *         incorrect or have some logical problems). <code>false</code> if it definitely has
   *         invalid values.
   */
  boolean containsBadValues() {
    return !(checkNotNull(clusters) && checkNotEmpty(idRequestChannel)
        && checkNotEmpty(idResponseChannel) && checkNotEmpty(heartbeatChannel)
        && checkNotEmpty(shutdownChannel) && checkNotEmpty(reservationRequestChannel)
        && checkNotEmpty(reservationResponseChannel) && checkPositive(minHeartRate)
        && checkPositive(maxHeartRate) && checkPositive(serverTimeout)
        && checkPositive(reservationResponseTimeout) && checkPositive(reservationTimeout));
  }

  private boolean checkNotNull(Object check) throws IllegalArgumentException {
    return check != null;
  }

  private boolean checkNotEmpty(byte[] check) {
    return checkNotNull(check) && check.length > 0;
  }

  private boolean checkPositive(long check) {
    return check > 0;
  }

  @Override
  public ServerSelectionMode getSelectionMode(String clusterId) {
    return clusters.get(clusterId);
  }

  @Override
  public byte[] getIdRequestChannel() {
    return idRequestChannel.clone();
  }

  @Override
  public byte[] getIdResponseChannel() {
    return idResponseChannel.clone();
  }

  @Override
  public byte[] getHeartbeatChannel() {
    return heartbeatChannel.clone();
  }

  @Override
  public byte[] getShutdownChannel() {
    return shutdownChannel.clone();
  }

  @Override
  public byte[] getReservationRequestChannel() {
    return reservationRequestChannel.clone();
  }

  @Override
  public byte[] getReservationResponseChannel() {
    return reservationResponseChannel.clone();
  }

  @Override
  public long getMinHeartRate() {
    return minHeartRate;
  }

  @Override
  public long getMaxHeartRate() {
    return maxHeartRate;
  }

  @Override
  public long getServerTimeout() {
    return serverTimeout;
  }

  @Override
  public long getReservationResponseTimeout() {
    return reservationResponseTimeout;
  }

  @Override
  public long getReservationFulfillmentTimeout() {
    return reservationTimeout;
  }

}
