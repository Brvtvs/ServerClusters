package io.brutus.minecraft.serverclusters.config;

import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

/**
 * A wrapper for a config message that can be updated as newer config messages arrive.
 */
public class ConfigWrapper implements SharedConfiguration {

  private volatile SharedConfiguration config;

  ConfigWrapper(SharedConfiguration config) {
    this.config = config;
  }

  void setConfiguration(SharedConfiguration config) {
    this.config = config;
  }

  @Override
  public ServerSelectionMode getSelectionMode(String clusterId) throws IllegalArgumentException {
    return config.getSelectionMode(clusterId);
  }

  @Override
  public byte[] getIdRequestChannel() {
    return config.getIdRequestChannel();
  }

  @Override
  public byte[] getIdResponseChannel() {
    return config.getIdResponseChannel();
  }

  @Override
  public byte[] getHeartbeatChannel() {
    return config.getHeartbeatChannel();
  }

  @Override
  public byte[] getShutdownChannel() {
    return config.getShutdownChannel();
  }

  @Override
  public byte[] getReservationRequestChannel() {
    return config.getReservationRequestChannel();
  }

  @Override
  public byte[] getReservationResponseChannel() {
    return config.getReservationResponseChannel();
  }

  @Override
  public long getMinHeartRate() {
    return config.getMinHeartRate();
  }

  @Override
  public long getMaxHeartRate() {
    return config.getMaxHeartRate();
  }

  @Override
  public long getServerTimeout() {
    return config.getServerTimeout();
  }

  @Override
  public long getReservationResponseTimeout() {
    return config.getReservationResponseTimeout();
  }

  @Override
  public long getReservationFulfillmentTimeout() {
    return config.getReservationFulfillmentTimeout();
  }

}
