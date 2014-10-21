package io.brutus.minecraft.serverclusters.bukkit;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import io.brutus.minecraft.serverclusters.ServerClustersConfig;
import io.brutus.minecraft.serverclusters.ServerClustersServerConfig;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

/**
 * Bukkit-plugin config implementation for ServerClusters.
 */
public class PluginConfig implements ServerClustersConfig, ServerClustersServerConfig {

  private static final Charset CHARSET = Charset.forName("UTF-8");

  private final PluginMain plugin;

  private String messagerInstanceName;
  private byte[] heartbeatChannel;
  private byte[] reservationChannel;
  private byte[] responseChannel;

  private String clusterId;
  private int totalSlots;
  private boolean enforceReservations;
  private long reservationTimeout;
  private long minHeartRate;
  private long maxHeartRate;

  private String serverId;
  private long serverTimeout;
  private long responseTimeout;
  private Map<String, ServerSelectionMode> clusters;

  public PluginConfig(PluginMain plugin) throws IllegalArgumentException {
    if (plugin == null) {
      throw new IllegalArgumentException("plugin cannot be null");
    }
    this.plugin = plugin;
    this.clusters = new HashMap<String, ServerSelectionMode>();
    load();
  }

  private void load() {
    try {
      plugin.saveDefaultConfig();
      FileConfiguration config = plugin.getConfig();

      ConfigurationSection messagingSec = config.getConfigurationSection("messaging");
      messagerInstanceName = messagingSec.getString("instance-name");

      ConfigurationSection channelsSec = messagingSec.getConfigurationSection("channels");
      heartbeatChannel = channelsSec.getString("heartbeat").getBytes(CHARSET);
      reservationChannel = channelsSec.getString("reservation-requests").getBytes(CHARSET);
      responseChannel = channelsSec.getString("reservation-responses").getBytes(CHARSET);

      ConfigurationSection serverSec = config.getConfigurationSection("server-functions");
      clusterId = serverSec.getString("cluster-id");
      totalSlots = serverSec.getInt("default-player-slots");
      enforceReservations = serverSec.getBoolean("kick-unwelcome-players");
      reservationTimeout = serverSec.getLong("reservation-timeout");

      ConfigurationSection heartSec = serverSec.getConfigurationSection("heart-rate");
      minHeartRate = heartSec.getLong("min-rate");
      maxHeartRate = heartSec.getLong("max-rate");

      ConfigurationSection clientSec = config.getConfigurationSection("client-functions");
      serverId = clientSec.getString("server-id");
      serverTimeout = clientSec.getLong("server-timeout");
      responseTimeout = clientSec.getLong("response-timeout");

      ConfigurationSection selectionSec = clientSec.getConfigurationSection("server-selection");
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
    } catch (Exception e) {
      plugin
          .getLogger()
          .severe(
              "There was an issue while loading the ServerClusters config. To continue, fix the configuration and restart the server.");
      e.printStackTrace();
    }
  }

  @Override
  public String getMessagerInstanceName() {
    return messagerInstanceName;
  }

  @Override
  public byte[] getHeartbeatChannel() {
    return heartbeatChannel.clone();
  }

  @Override
  public byte[] getReservationRequestChannel() {
    return reservationChannel.clone();
  }

  @Override
  public byte[] getReservationResponseChannel() {
    return responseChannel.clone();
  }

  @Override
  public ServerClustersServerConfig getServerConfig() {
    return this;
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
  public boolean enforceReservations() {
    return enforceReservations;
  }

  @Override
  public long getReservationTimeout() {
    return reservationTimeout;
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
  public String getGameServerId() {
    return serverId;
  }

  @Override
  public long getServerTimeout() {
    return serverTimeout;
  }

  @Override
  public long getResponseTimeout() {
    return responseTimeout;
  }

  @Override
  public ServerSelectionMode getSelectionMode(String clusterId) {
    return clusters.get(clusterId);
  }

}
