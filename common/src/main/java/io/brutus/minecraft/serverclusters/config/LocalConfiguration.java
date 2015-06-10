package io.brutus.minecraft.serverclusters.config;

/**
 * Configuration for the basic methods of connecting to the network.
 * <p>
 * Options that by definition need to be set locally.
 */
public interface LocalConfiguration {

  /**
   * Gets the name of the instance of the PubSub messaging implementation that should be used to
   * send and receive messages.
   * <p>
   * Each pubsub messager on the network should reference the same pub/sub message brokers.
   * 
   * @return The name of the messager instance to use.
   */
  String getMessagerInstanceName();

  /**
   * Gets the channel, as a <code>byte</code> array, on which to send and listen for configuration
   * requests to/from connected servers.
   * 
   * @return The pub/sub configuration-request messaging channel.
   */
  byte[] getConfigurationRequestChannel();

  /**
   * Gets the channel, as a <code>byte</code> array, on which to send and listen for configuration
   * responses to/from connected servers.
   * 
   * @return The pub/sub configuration-response messaging channel.
   */
  byte[] getConfigurationResponseChannel();

}
