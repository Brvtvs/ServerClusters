package io.brutus.minecraft.serverclusters.config;

import java.util.Arrays;

import io.brutus.minecraft.serverclusters.protocol.config.ConfigurationMessage;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import io.brutus.networking.pubsubmessager.Subscriber;

/**
 * Provides the shared network configuration to clients who request it.
 */
public class SharedConfigurationProvider {

  private PubSubMessager messager;
  private LocalConfiguration localConfig;
  private SharedConfigurationLoader sharedConfig;

  private RequestSubscriber sub;

  public SharedConfigurationProvider(PubSubMessager messager, LocalConfiguration localConfig,
      SharedConfigurationLoader sharedConfig) throws IllegalArgumentException {
    if (messager == null || localConfig == null || sharedConfig == null) {
      throw new IllegalArgumentException("params cannot be null");
    }
    this.messager = messager;
    this.localConfig = localConfig;
    this.sharedConfig = sharedConfig;

    sub = new RequestSubscriber();
    messager.subscribe(localConfig.getConfigurationRequestChannel(), sub);
  }

  /**
   * Stops this object from functioning and relinquishes its resources.
   * <p>
   * Irreversible.
   */
  public void destroy() {
    messager.unsubscribe(localConfig.getConfigurationRequestChannel(), sub);
  }

  /**
   * Forces this to publish its current version of the shared configuration.
   */
  public void publishConfiguration() {
    messager.publish(localConfig.getConfigurationResponseChannel(),
        ConfigurationMessage.createMessage(sharedConfig.getConfigurationMessage()));
  }

  /**
   * Responds to incoming requests.
   */
  private class RequestSubscriber implements Subscriber {

    @Override
    public void onMessage(byte[] channel, byte[] message) {
      if (Arrays.equals(channel, localConfig.getConfigurationRequestChannel())) {
        publishConfiguration();
      }
    }
  }

}
