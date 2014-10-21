package io.brutus.minecraft.serverclusters;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.brutus.minecraft.serverclusters.bukkit.BukkitSlotManager;
import io.brutus.minecraft.serverclusters.bukkit.NetworkStatusCommand;
import io.brutus.minecraft.serverclusters.bukkit.PluginConfig;
import io.brutus.minecraft.serverclusters.bukkit.PluginMain;
import io.brutus.minecraft.serverclusters.cache.NetworkCache;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

import org.bukkit.Bukkit;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * A utility that groups interchangeable gameservers together into clusters so they can be
 * provisioned as a group. Supports matchmaking and load-balancing provision for players, so that
 * when they are sent to a cluster, they can be sent to the currently optimal instance.
 * <p>
 * Functions as a peer-to-peer network, with each gameserver serving as a client and/or server on
 * the network. There is no central coordinator. Gameservers announce themselves, announce what
 * cluster they are a part of, and then periodically send heartbeat messages to update the network
 * on their status.
 * <p>
 * Each gameserver then serves as the coordinator for its own player slots only, allowing other
 * gameservers to contact it, reserve slots, and then send players to those slots. In this way, the
 * peer-to-peer network translates approximate, cached data from heartbeats into synchronous,
 * coordinated instance provision for players.
 */
public class ServerClusters implements ServerClustersAPI {

  private static final String PLUGIN_NAME = "ServerClusters";

  private static volatile ServerClusters singleton;

  private final PluginMain plugin;
  private final ServerClustersConfig config;

  private final NetworkStatus network;
  private final SlotManager slotManager;

  private final HeartbeatMessager heartbeats;
  private final PlayerRelocator relocator;

  public static ServerClusters getSingleton() {
    if (singleton == null) {
      try {
        PluginMain plugin =
            (PluginMain) Bukkit.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        singleton = new ServerClusters(plugin);
      } catch (Exception e) {
        System.out.println("Could not create a singleton of " + PLUGIN_NAME);
        e.printStackTrace();
      }
    }
    return singleton;
  }

  private ServerClusters(PluginMain plugin) {
    if (plugin == null) {
      throw new IllegalArgumentException("plugin cannot be null");
    }
    this.plugin = plugin;
    this.config = new PluginConfig(plugin);

    if (config.actAsServer()) {

      BukkitSlotManager bukkitSlots =
          new BukkitSlotManager(plugin, config.getServerConfig().getTotalSlots(), config
              .getServerConfig().getReservationTimeout(), config.getServerConfig()
              .enforceReservations());

      plugin.getServer().getPluginManager().registerEvents(bukkitSlots, plugin);
      this.slotManager = bukkitSlots;

    } else {
      slotManager = null;
    }

    network = new NetworkCache(config);

    heartbeats = new HeartbeatMessager(config, network, slotManager);
    relocator = new PlayerRelocator(config, network, slotManager);


    if (config.actAsServer()) {
      plugin.getCommand("networkstatus").setExecutor(
          new NetworkStatusCommand(network, slotManager, config.getGameServerId(), config
              .getServerConfig().getClusterId()));
    } else {
      plugin.getCommand("networkstatus").setExecutor(
          new NetworkStatusCommand(network, null, config.getGameServerId(), null));
    }
  }


  @Override
  public String getServerId() {
    return config.getGameServerId();
  }

  @Override
  public String getClusterId() {
    return config.getServerConfig().getClusterId();
  }

  @Override
  public int getOpenSlots() {
    return slotManager.getOpenSlots();
  }

  @Override
  public int getTotalSlots() {
    return slotManager.getTotalSlots();
  }

  @Override
  public ListenableFuture<Boolean> updateTotalSlots(int totalSlots) throws IllegalArgumentException {
    return slotManager.setTotalSlots(totalSlots);
  }

  @Override
  public ListenableFuture<Boolean> sendPlayersToCluster(String clusterId, UUID... players)
      throws IllegalArgumentException {
    if (players == null || players.length < 1) {
      throw new IllegalArgumentException("players cannot be null or empty");
    }

    ServerSelectionMode mode = config.getSelectionMode(clusterId);
    if (mode == null) {
      throw new IllegalArgumentException("could not find configuration for the given cluster id");
    }

    Set<UUID> pSet = sanitizePlayers(players);

    if (pSet.isEmpty()) {
      SettableFuture<Boolean> ret = SettableFuture.create();
      ret.set(true);
      return ret;
    }

    return relocator.sendPlayers(clusterId, mode, pSet);
  }

  @Override
  public ListenableFuture<Boolean> sendPlayersToPlayer(UUID targetPlayerId, UUID... playersToSend)
      throws IllegalArgumentException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ListenableFuture<Boolean> sendPlayersToPlayer(String targetPlayerName,
      UUID... playersToSend) throws IllegalArgumentException {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Gets the plugin instance for the <code>ServerClusters</code> utility.
   * 
   * @return <code>ServerClusters</code> main plugin class.
   */
  public PluginMain getPlugin() {
    return plugin;
  }

  private Set<UUID> sanitizePlayers(UUID[] players) {
    Set<UUID> pSet = new HashSet<UUID>();
    for (UUID p : players) {
      if (p != null && plugin.getServer().getPlayer(p).isOnline()) {
        pSet.add(p);
      }
    }
    return pSet;
  }

}
