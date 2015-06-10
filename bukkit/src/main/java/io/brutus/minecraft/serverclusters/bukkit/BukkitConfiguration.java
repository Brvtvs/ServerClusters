package io.brutus.minecraft.serverclusters.bukkit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import io.brutus.minecraft.pubsub.PubSub;
import io.brutus.minecraft.serverclusters.config.SharedConfiguration;
import io.brutus.minecraft.serverclusters.config.SharedConfigurationManager;
import io.brutus.minecraft.serverclusters.gameserver.ServerClustersConfiguration;
import io.brutus.minecraft.serverclusters.protocol.serialization.Encoding;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;
import io.brutus.minecraft.serverclusters.uid.IdRequester;
import io.brutus.networking.pubsubmessager.PubSubMessager;

/**
 * Bukkit-plugin config implementation for ServerClusters.
 */
public class BukkitConfiguration implements ServerClustersConfiguration {

  private final BukkitMain plugin;

  private String serverId;
  private String clusterId;
  private int totalSlots;
  private boolean strictReservations;
  private boolean attemptInstanceConsolidations;

  private String messagerName;
  private byte[] configRequestChannel;
  private byte[] configResponseChannel;

  private SharedConfigurationManager configManager;
  private SharedConfiguration sharedConfig;

  public BukkitConfiguration(BukkitMain plugin) throws IllegalArgumentException {
    if (plugin == null) {
      throw new IllegalArgumentException("plugin cannot be null");
    }
    this.plugin = plugin;
    load();
  }

  private void load() {
    try {

      plugin.saveDefaultConfig();
      FileConfiguration config = plugin.getConfig();

      // loads local values
      clusterId = config.getString("cluster-id");
      totalSlots = config.getInt("default-player-slots");
      strictReservations = config.getBoolean("strict-reservations");

      ConfigurationSection messagerSec = config.getConfigurationSection("central-config");
      messagerName = messagerSec.getString("pubsub-messager");
      configRequestChannel = messagerSec.getString("request-channel").getBytes(Encoding.CHARSET);
      configResponseChannel = messagerSec.getString("response-channel").getBytes(Encoding.CHARSET);

      PubSubMessager messager = PubSub.getSingleton().getMessager(messagerName);
      if (messager == null) {
        throw new IllegalArgumentException("Could not find a pubsubmessager with the id '"
            + messagerName + "'");
      }

      // blocks thread while attempting to load central configuration
      configManager =
          new SharedConfigurationManager(messager, configRequestChannel, configResponseChannel);
      // TODO add admin notifier
      sharedConfig = configManager.loadConfiguration(null);

      if (sharedConfig == null) {
        throw new Exception(
            "Could not load the central shared network configuration. Check to make sure the provider is up and reachable and that the channels being used are correct");
      }

      // attempts instance consolidation if it is set to true AND this server's cluster is in
      // matchmaking mode
      ServerSelectionMode thisClustersMode = sharedConfig.getSelectionMode(clusterId);
      attemptInstanceConsolidations =
          config.getBoolean("attempt-instance-consolidations") && thisClustersMode != null
              && thisClustersMode == ServerSelectionMode.MATCHMAKING;

      String ip = plugin.getServer().getIp();
      if (ip == null || ip.isEmpty() || ip.equals("0.0.0.0")) {
        plugin
            .getLogger()
            .severe(
                "[IMPORTANT!!!] The server's IP address must be manually set in server.properties for "
                    + "ServerClusters to work properly. It is not set, and so ServerClusters will not work.");
        throw new IllegalArgumentException("server ip must be defined");
      }

      int port = plugin.getServer().getPort();
      if (port < 1) {
        plugin
            .getLogger()
            .warning(
                "This server's port was read as a non-positive number. This may prevent players from connecting to this server");
      }

      // blocks thread while fetching a unique id for this server
      // TODO add admin notifier
      serverId =
          IdRequester.getUniqueId(messager, sharedConfig.getIdRequestChannel(),
              sharedConfig.getIdResponseChannel(), clusterId, ip, port, null);
      if (serverId == null) {
        throw new Exception(
            "Could not get a unique id from the network. Check to make sure the provicer is up and reachable, and that the channels being used are correct.");
      }

    } catch (Exception e) {
      plugin
          .getLogger()
          .severe(
              "There was an issue while loading the ServerClusters config. To continue, fix the configuration and restart the server.");
      e.printStackTrace();
    }
  }

  public void destroy() {
    configManager.destroy();
  }

  @Override
  public String getServerId() {
    return serverId;
  }

  @Override
  public String getClusterId() {
    return clusterId;
  }

  @Override
  public int getTotalSlots() {
    return totalSlots;
  }

  @Override
  public boolean strictReservations() {
    return strictReservations;
  }

  @Override
  public boolean attemptInstanceConsolidations() {
    return attemptInstanceConsolidations;
  }

  @Override
  public ServerSelectionMode getSelectionMode(String clusterId) {
    return sharedConfig.getSelectionMode(clusterId);
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

}
