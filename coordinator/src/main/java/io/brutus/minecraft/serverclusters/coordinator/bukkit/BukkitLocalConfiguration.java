package io.brutus.minecraft.serverclusters.coordinator.bukkit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import io.brutus.minecraft.serverclusters.config.LocalConfiguration;
import io.brutus.minecraft.serverclusters.protocol.serialization.Encoding;
import io.brutus.minecraft.simpleconfig.Configuration;
import io.brutus.minecraft.simpleconfig.YamlConfigAccessor;

/**
 * Bukkit config for options that by definition must be defined locally.
 */
public class BukkitLocalConfiguration extends Configuration implements LocalConfiguration {

  private static final String SUBDIRECTORY = "";
  private static final String FILE_NAME = "local-config.yml";

  private JavaPlugin plugin;

  private String messagerInstanceName;
  private byte[] configRequestChannel;
  private byte[] configResponseChannel;

  public BukkitLocalConfiguration(JavaPlugin plugin) {
    super(new YamlConfigAccessor(plugin, FILE_NAME, SUBDIRECTORY));
    this.plugin = plugin;
    load();
  }

  public void reload() {
    refresh();
    load();
  }

  private void load() {
    FileConfiguration config = getConfig();

    try {
      messagerInstanceName = config.getString("pubsub-messager");

      ConfigurationSection sharingSec = config.getConfigurationSection("config-sharing");
      configRequestChannel = sharingSec.getString("request-channel").getBytes(Encoding.CHARSET);
      configResponseChannel = sharingSec.getString("response-channel").getBytes(Encoding.CHARSET);

    } catch (Exception e) {
      plugin
          .getLogger()
          .severe(
              "There was an issue while loading the local config. It was not loaded. To continue, fix it and restart the server.");
      e.printStackTrace();
    }
  }

  @Override
  public String getMessagerInstanceName() {
    return messagerInstanceName;
  }

  @Override
  public byte[] getConfigurationRequestChannel() {
    return configRequestChannel.clone();
  }

  @Override
  public byte[] getConfigurationResponseChannel() {
    return configResponseChannel.clone();
  }

}
