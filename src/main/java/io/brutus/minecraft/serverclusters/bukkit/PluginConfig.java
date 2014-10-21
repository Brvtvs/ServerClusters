package io.brutus.minecraft.serverclusters.bukkit;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import io.brutus.minecraft.serverclusters.ServerClustersConfig;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

/**
 * Bukkit-plugin config implementation for ServerClusters.
 */
public class PluginConfig implements ServerClustersConfig {

  private static final Charset CHARSET = Charset.forName("UTF-8");

  private final PluginMain plugin;

  private String serverId;
  private String clusterId;
  private int totalSlots;
  private boolean strictReservations;

  private Map<String, ServerSelectionMode> clusters;

  private String messagerInstanceName;
  private byte[] heartbeatChannel;
  private byte[] reservationChannel;
  private byte[] responseChannel;

  private long minHeartRate;
  private long maxHeartRate;
  private long serverTimeout;
  private long responseTimeout;
  private long reservationTimeout;

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


      ConfigurationSection thisServerSec = config.getConfigurationSection("this-server");
      serverId = thisServerSec.getString("server-id");
      clusterId = thisServerSec.getString("cluster-id");
      totalSlots = thisServerSec.getInt("default-player-slots");
      strictReservations = thisServerSec.getBoolean("strict-reservations");


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


      ConfigurationSection messagingSec = config.getConfigurationSection("messaging");
      messagerInstanceName = messagingSec.getString("instance-name");

      ConfigurationSection channelsSec = messagingSec.getConfigurationSection("channels");
      heartbeatChannel = channelsSec.getString("heartbeat").getBytes(CHARSET);
      reservationChannel = channelsSec.getString("reservation-requests").getBytes(CHARSET);
      responseChannel = channelsSec.getString("reservation-responses").getBytes(CHARSET);


      ConfigurationSection timingsSec = config.getConfigurationSection("timings");

      ConfigurationSection heartSec = timingsSec.getConfigurationSection("heart-rate");
      minHeartRate = heartSec.getLong("min-rate");
      maxHeartRate = heartSec.getLong("max-rate");

      serverTimeout = timingsSec.getLong("server-timeout");
      responseTimeout = timingsSec.getLong("response-timeout");
      reservationTimeout = timingsSec.getLong("reservation-timeout");

    } catch (Exception e) {
      plugin
          .getLogger()
          .severe(
              "There was an issue while loading the ServerClusters config. To continue, fix the configuration and restart the server.");
      e.printStackTrace();
    }
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
  public ServerSelectionMode getSelectionMode(String clusterId) {
    return clusters.get(clusterId);
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
  public long getMinHeartRate() {
    return minHeartRate;
  }

  @Override
  public long getMaxHeartRate() {
    return maxHeartRate;
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
  public long getReservationTimeout() {
    return reservationTimeout;
  }

}
