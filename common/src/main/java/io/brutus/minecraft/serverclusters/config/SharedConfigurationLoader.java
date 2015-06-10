package io.brutus.minecraft.serverclusters.config;

import io.brutus.minecraft.serverclusters.protocol.config.ConfigurationMessage;

/**
 * Loads the shared configuration.
 */
public interface SharedConfigurationLoader {

  /**
   * Reloads the configuration from file, updating the object returned by
   * {@link #getConfigurationMessage()}.
   */
  void reload();

  /**
   * Gets the current, in-memory state of the shared configuration, as a message that can be
   * forwarded to connected servers.
   * <p>
   * To reload the config from disk, use {@link #reload()}.
   * 
   * @return A configuration message for the shared network config.
   */
  ConfigurationMessage getConfigurationMessage();

}
