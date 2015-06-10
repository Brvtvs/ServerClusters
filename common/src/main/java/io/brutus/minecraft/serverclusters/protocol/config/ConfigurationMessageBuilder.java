package io.brutus.minecraft.serverclusters.protocol.config;

import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A builder for a serializeable configuration.
 */
public class ConfigurationMessageBuilder {

  private ConfigurationMessage building;

  ConfigurationMessageBuilder() {
    building = new ConfigurationMessage();
  }

  /**
   * Finishes the configuration message based on what has been set so far.
   * <p>
   * All values should be set before finishing. Any unset values will be left as default values.
   * Some default values are invalid and will cause issues for clients reading this config.
   * 
   * @return The finished configuration message.
   */
  public ConfigurationMessage build() {
    ConfigurationMessage ret = building;
    if (ret.containsBadValues()) {
      throw new IllegalArgumentException(
          "The shared configuration was either not correctly or not fully configured. It contains invalid or missing values");
    }
    building = new ConfigurationMessage();
    return ret;
  }

  /**
   * Sets the network's clusters and how servers should be selected from them.
   * 
   * @param clusters A map of the network's clusters and their server-selection modes.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setClusters(
      ConcurrentHashMap<String, ServerSelectionMode> clusters) {
    building.clusters = clusters;
    return this;
  }

  /**
   * Sets the channel, as a <code>byte</code> array, on which servers should send requests for
   * unique ids.
   * 
   * @param idRequestChannel The pub/sub id-request messaging channel.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setIdRequestChannel(byte[] idRequestChannel) {
    building.idRequestChannel = idRequestChannel;
    return this;
  }

  /**
   * Sets the channel, as a <code>byte</code> array, on which unique ids should be allocated to
   * servers that requested them.
   * 
   * @param idResponseChannel The pub/sub id-response messaging channel.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setIdResponseChannel(byte[] idResponseChannel) {
    building.idResponseChannel = idResponseChannel;
    return this;
  }

  /**
   * Sets the messaging channel that heartbeats should be sent on.
   * 
   * @param heartbeatChannel The pub/sub heartbeat channel. Must be unique from all other channels
   *        in use.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setHeartbeatChannel(byte[] heartbeatChannel) {
    building.heartbeatChannel = heartbeatChannel;
    return this;
  }

  /**
   * Sets the messaging channel that shutdown notification should be sent on.
   * 
   * @param shutdownChannel The pub/sub shutdown channel. Must be unique from all other channels in
   *        use.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setShutdownChannel(byte[] shutdownChannel) {
    building.shutdownChannel = shutdownChannel;
    return this;
  }

  /**
   * Sets the messaging channel that reservation requests should be sent on.
   * 
   * @param reservationChannel The pub/sub reservation request channel. Must be unique from all
   *        other channels in use.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setReservationRequestChannel(byte[] reservationChannel) {
    building.reservationRequestChannel = reservationChannel;
    return this;
  }

  /**
   * Sets the messaging channel that reservation responses should be sent on.
   * 
   * @param responseChannel The pub/sub reservation response channel. Must be unique from all other
   *        channels in use.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setReservationResponseChannel(byte[] responseChannel) {
    building.reservationResponseChannel = responseChannel;
    return this;
  }

  /**
   * Sets the maximum amount of time, in milliseconds, to wait in between sending heartbeats, even
   * if there have been no updates to local data in the meantime.
   * 
   * @param minHeartRate The maximum amount of time to wait in between heartbeats.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setMinHeartRate(long minHeartRate) {
    building.minHeartRate = minHeartRate;
    return this;
  }

  /**
   * Sets the minimum amount of time, in milliseconds, to wait between sending heartbeats, even if
   * there are updates to local data in the meantime.
   * 
   * @param maxHeartRate This server's maximum rate of sending heartbeat messages.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setMaxHeartRate(long maxHeartRate) {
    building.maxHeartRate = maxHeartRate;
    return this;
  }

  /**
   * Sets how long to wait for a heartbeat, in milliseconds, after the previous heartbeat before
   * assuming the sending server is unresponsive.
   * 
   * @param serverTimeout The acceptable amount of time in between heartbeat messages before the
   *        server sending them is considered offline.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setServerTimeout(long serverTimeout) {
    building.serverTimeout = serverTimeout;
    return this;
  }

  /**
   * Sets the timeout, in milliseconds, after sending a reservation request to give up on listening
   * for a response. Applies to messages targeted at servers as well as at specific players.
   * 
   * @param responseTimeout The reservation-response timeout.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setReservationResponseTimeout(long responseTimeout) {
    building.reservationResponseTimeout = responseTimeout;
    return this;
  }

  /**
   * Sets how long, in milliseconds, to wait after approving a reservation before assuming the
   * player(s) are not coming and revoking their reservation, reopening the slot for other players.
   * 
   * @param reservationTimeout The maximum time a reservation can wait without being fulfilled. The
   *        reservation timeout.
   * @return This builder.
   */
  public ConfigurationMessageBuilder setReservationTimeout(long reservationTimeout) {
    building.reservationTimeout = reservationTimeout;
    return this;
  }
}
