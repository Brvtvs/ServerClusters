package io.brutus.minecraft.serverclusters.coordinator.bukkit;

import java.util.concurrent.ConcurrentHashMap;

import io.brutus.minecraft.serverclusters.config.SharedConfigurationLoader;
import io.brutus.minecraft.serverclusters.protocol.config.ConfigurationMessage;
import io.brutus.minecraft.serverclusters.protocol.config.ConfigurationMessageBuilder;
import io.brutus.minecraft.serverclusters.protocol.serialization.Encoding;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;
import io.brutus.minecraft.simpleconfig.Configuration;
import io.brutus.minecraft.simpleconfig.YamlConfigAccessor;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads the local version of the network configuration so it can be shared with the rest of the
 * network.
 */
public class BukkitSharedConfiguration extends Configuration implements SharedConfigurationLoader {

  private static final String SUBDIRECTORY = "";
  private static final String FILE_NAME = "shared-network-config.yml";

  private JavaPlugin plugin;
  private ConfigurationMessage message;

  public BukkitSharedConfiguration(JavaPlugin plugin) {
    super(new YamlConfigAccessor(plugin, FILE_NAME, SUBDIRECTORY));
    this.plugin = plugin;
    load();
  }

  @Override
  public ConfigurationMessage getConfigurationMessage() {
    return message;
  }

  @Override
  public void reload() {
    refresh();
    load();
  }

  private void load() {
    FileConfiguration config = getConfig();

    ConfigurationMessageBuilder builder = ConfigurationMessage.builder();

    try {

      ConcurrentHashMap<String, ServerSelectionMode> clusters =
          new ConcurrentHashMap<String, ServerSelectionMode>();

      ConfigurationSection clustersSec = config.getConfigurationSection("clusters");
      ConfigurationSection selectionSec = clustersSec.getConfigurationSection("server-selection");

      for (String cluster : selectionSec.getKeys(false)) {
        String modeName = selectionSec.getString(cluster);
        if ("matchmaking".equalsIgnoreCase(modeName)) {
          clusters.put(cluster, ServerSelectionMode.MATCHMAKING);
        } else if ("loadbalancing".equalsIgnoreCase(modeName)) {
          clusters.put(cluster, ServerSelectionMode.LOAD_BALANCING);
        } else if ("random".equalsIgnoreCase(modeName)) {
          clusters.put(cluster, ServerSelectionMode.RANDOM);
        } else {
          plugin.getLogger().severe(
              "Could not parse configuration for cluster '" + cluster
                  + "', because the server selection mode " + modeName + " is not recongized.");
        }
      }
      builder.setClusters(clusters);

      ConfigurationSection messagingSec = config.getConfigurationSection("messaging");
      ConfigurationSection channelsSec = messagingSec.getConfigurationSection("channels");

      builder.setIdRequestChannel(channelsSec.getString("id-requests").getBytes(Encoding.CHARSET));
      builder
          .setIdResponseChannel(channelsSec.getString("id-responses").getBytes(Encoding.CHARSET));
      builder.setHeartbeatChannel(channelsSec.getString("heartbeat").getBytes(Encoding.CHARSET));
      builder.setShutdownChannel(channelsSec.getString("shutdown").getBytes(Encoding.CHARSET));
      builder.setReservationRequestChannel(channelsSec.getString("reservation-requests").getBytes(
          Encoding.CHARSET));
      builder.setReservationResponseChannel(channelsSec.getString("reservation-responses")
          .getBytes(Encoding.CHARSET));

      ConfigurationSection timingsSec = config.getConfigurationSection("timings");

      ConfigurationSection heartSec = timingsSec.getConfigurationSection("heart-rate");
      builder.setMinHeartRate(heartSec.getLong("min-rate"));
      builder.setMaxHeartRate(heartSec.getLong("max-rate"));

      builder.setServerTimeout(timingsSec.getLong("server-timeout"));
      builder.setReservationResponseTimeout(timingsSec.getLong("reservation-response-timeout"));
      builder.setReservationTimeout(timingsSec.getLong("reservation-timeout"));

      message = builder.build();

    } catch (Exception e) {
      plugin
          .getLogger()
          .severe(
              "There was an issue while loading the shared config. It was not loaded. To continue, fix the configuration and reload it or restart the server.");
      e.printStackTrace();
    }
  }
}
