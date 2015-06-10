package io.brutus.minecraft.serverclusters.bungee;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import io.brutus.minecraft.pubsub.PubSub;
import io.brutus.minecraft.serverclusters.config.LocalConfiguration;
import io.brutus.minecraft.serverclusters.config.SharedConfiguration;
import io.brutus.minecraft.serverclusters.config.SharedConfigurationManager;
import io.brutus.minecraft.serverclusters.protocol.serialization.Encoding;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;
import io.brutus.minecraft.serverclusters.uid.IdRequester;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

/**
 * Bungee configuration for ServerClustersBungee.
 */
public class BungeeConfiguration implements SharedConfiguration, LocalConfiguration {

  private static final String BUNGEE_CLUSTER_NAME = "bungee";

  private Plugin plugin;

  private String bungeeId;

  private String defaultCluster;
  private Map<String, String> forcedHostDefaults;

  private String messagerName;
  private byte[] configRequestChannel;
  private byte[] configResponseChannel;

  private SharedConfigurationManager configManager;
  private SharedConfiguration sharedConfig;

  public BungeeConfiguration(Plugin plugin) {
    if (plugin == null) {
      throw new IllegalArgumentException("plugin cannot be null");
    }
    this.plugin = plugin;
    load();
  }

  private void load() {
    try {

      copyFromDefault();

      Configuration config = null;
      try {
        config =
            ConfigurationProvider.getProvider(YamlConfiguration.class).load(
                new File(plugin.getDataFolder(), "config.yml"));
      } catch (IOException e) {
        plugin.getLogger().severe("Could not load plugin config!");
        e.printStackTrace();
        return;
      }

      // ----
      // LOADS LOCAL VALUES
      // ----
      defaultCluster = config.getString("default-cluster");
      forcedHostDefaults = new ConcurrentHashMap<String, String>();
      Configuration fhSec = config.getSection("forced-hosts");
      for (String key : fhSec.getKeys()) {
        String forcedHost = key.replace("_", ".");
        String cluster = fhSec.getString(key);
        forcedHostDefaults.put(forcedHost, cluster);
      }

      Configuration messagerSec = config.getSection("central-config");
      messagerName = messagerSec.getString("pubsub-messager");
      configRequestChannel = messagerSec.getString("request-channel").getBytes(Encoding.CHARSET);
      configResponseChannel = messagerSec.getString("response-channel").getBytes(Encoding.CHARSET);

      PubSubMessager messager = null;
      // if PubSub is not enabled, waits for it to enable, periodically retrying to get an instance
      // of it
      try {
        messager = PubSub.getSingleton().getMessager(messagerName);
      } catch (IllegalStateException ex) {
        Logger lg = plugin.getLogger();
        lg.info("Failed to get an instance of PubSub, it is not enabled.");
        throw ex;
      }

      if (messager == null) {
        throw new IllegalArgumentException("Could not find a pubsubmessager with the id '"
            + messagerName + "'");
      }

      // ----
      // GETS SHARED NETWORK CONFIG
      // ----
      // blocks thread while attempting to load central configuration
      configManager =
          new SharedConfigurationManager(messager, configRequestChannel, configResponseChannel);
      // TODO add admin notifier
      sharedConfig = configManager.loadConfiguration(null);

      if (sharedConfig == null) {
        throw new Exception(
            "Could not load the central shared network configuration. Check to make sure the provider is up and reachable and that the channels being used are correct");
      }

      // ----
      // GETS UNIQUE ID
      // ----
      // picks a listener whose ip and port will be used to uniquely identify this proxy instance
      // until it gets its unique id.
      String ip = null;
      int port = 0;
      for (ListenerInfo info : plugin.getProxy().getConfig().getListeners()) {
        if (info != null) {
          String infoIp = info.getHost().getHostString();
          if (infoIp != null && !infoIp.isEmpty() && !infoIp.equals("0.0.0.0")) {
            // found a usable ip, uses it
            ip = infoIp;
            port = info.getHost().getPort();
            break;
          }
        }
      }

      if (ip == null) {
        plugin
            .getLogger()
            .severe(
                "[IMPORTANT!!!] The proxy must have at least one listener with an IP address set manually in the config.yml (must be an actual ip, not the default 0.0.0.0) for "
                    + "ServerClusters to work properly. None are set, and so ServerClusters will not work.");
        throw new IllegalArgumentException("no usable ip found");
      } else if (port < 1) {
        plugin
            .getLogger()
            .warning(
                "This proxy's port was read as a non-positive number. This may prevent players from connecting.");
      }

      // blocks thread while fetching a unique id for this server
      // TODO add admin notifier
      bungeeId =
          IdRequester.getUniqueId(messager, sharedConfig.getIdRequestChannel(),
              sharedConfig.getIdResponseChannel(), BUNGEE_CLUSTER_NAME, ip, port, null);
      if (bungeeId == null) {
        throw new Exception(
            "Could not get a unique id from the network. Check to make sure the provicer is up and reachable, and that the channels being used are correct.");
      }

    } catch (Exception e) {
      plugin
          .getLogger()
          .severe(
              "There was an issue while loading the ServerClustersBungee config. To continue, fix the configuration and restart BungeeCord.");
      e.printStackTrace();
    }
  }

  public void destroy() {
    configManager.destroy();
  }

  /**
   * Gets the current, ephemeral unique id of this bungee instance.
   * 
   * @return This bungee instance's current unique id.
   */
  public String getBungeeId() {
    return bungeeId;
  }

  /**
   * Gets the id of the cluster that players should be sent to on logging into this proxy as a
   * default.
   * 
   * @return The default cluster id.
   */
  public String getDefaultClusterId() {
    return defaultCluster;
  }

  /**
   * Gets the id of the cluster that players should be sent to on logging into this proxy on a
   * specific forced host.
   * 
   * @param forcedHost The forced host the player is logging in on.
   * @return The default cluster for the given forced host. If no specifically defined default
   *         cluster is found for the forced host, returns the default cluster the same as
   *         {@link #getDefaultClusterId()}.
   */
  public String getForcedHostDefaultClusterId(String forcedHost) {
    String ret = forcedHostDefaults.get(forcedHost);
    if (ret == null) {
      ret = getDefaultClusterId();
    }
    return ret;
  }

  @Override
  public String getMessagerInstanceName() {
    return messagerName;
  }

  @Override
  public byte[] getConfigurationRequestChannel() {
    return configRequestChannel.clone();
  }

  @Override
  public byte[] getConfigurationResponseChannel() {
    return configResponseChannel.clone();
  }

  @Override
  public ServerSelectionMode getSelectionMode(String clusterId) {
    return sharedConfig.getSelectionMode(clusterId);
  }

  @Override
  public byte[] getIdRequestChannel() {
    return sharedConfig.getIdRequestChannel();
  }

  @Override
  public byte[] getIdResponseChannel() {
    return sharedConfig.getIdResponseChannel();
  }

  @Override
  public byte[] getHeartbeatChannel() {
    return sharedConfig.getHeartbeatChannel();
  }

  @Override
  public byte[] getShutdownChannel() {
    return sharedConfig.getShutdownChannel();
  }

  @Override
  public byte[] getReservationRequestChannel() {
    return sharedConfig.getReservationRequestChannel();
  }

  @Override
  public byte[] getReservationResponseChannel() {
    return sharedConfig.getReservationResponseChannel();
  }

  @Override
  public long getMinHeartRate() {
    return sharedConfig.getMinHeartRate();
  }

  @Override
  public long getMaxHeartRate() {
    return sharedConfig.getMaxHeartRate();
  }

  @Override
  public long getServerTimeout() {
    return sharedConfig.getServerTimeout();
  }

  @Override
  public long getReservationResponseTimeout() {
    return sharedConfig.getReservationResponseTimeout();
  }

  @Override
  public long getReservationFulfillmentTimeout() {
    return sharedConfig.getReservationFulfillmentTimeout();
  }

  private void copyFromDefault() {
    if (!plugin.getDataFolder().exists()) {
      plugin.getDataFolder().mkdir();
    }

    File file = new File(plugin.getDataFolder(), "config.yml");

    if (!file.exists()) {
      try {
        Files.copy(plugin.getResourceAsStream("config.yml"), file.toPath());
      } catch (IOException e) {
        plugin.getLogger().severe("Could not load plugin config!");
        e.printStackTrace();
        return;
      }
    }
  }

}
