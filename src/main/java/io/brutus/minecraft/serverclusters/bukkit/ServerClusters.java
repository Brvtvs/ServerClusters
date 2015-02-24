package io.brutus.minecraft.serverclusters.bukkit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.brutus.minecraft.pubsub.PubSub;
import io.brutus.minecraft.serverclusters.mcserver.BeatingHeart;
import io.brutus.minecraft.serverclusters.mcserver.InstanceConsolidator;
import io.brutus.minecraft.serverclusters.mcserver.PlayerRelocator;
import io.brutus.minecraft.serverclusters.mcserver.ServerClustersAPI;
import io.brutus.minecraft.serverclusters.mcserver.ServerClustersConfig;
import io.brutus.minecraft.serverclusters.mcserver.SlotManager;
import io.brutus.minecraft.serverclusters.networkstatus.HeartbeatSubscription;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkCache;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkStatus;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;
import io.brutus.networking.pubsubmessager.PubSubMessager;

import org.bukkit.Bukkit;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * A utility that groups interchangeable Minecraft servers together into clusters so they can be
 * provisioned as a group. Supports matchmaking and load-balancing provision for players, so that
 * when they are sent to a cluster, they can be sent to the currently optimal instance.
 * <p>
 * Functions as a peer-to-peer network, with each Minecraft server serving as a client and server on
 * the network. There is no central coordinator. Servers announce themselves, announce what cluster
 * they are a part of, and then periodically send heartbeat messages to update the network on their
 * status.
 * <p>
 * Each Minecraft server then serves as the coordinator for its own player slots only, allowing
 * other servers to contact it, reserve slots, and then send players to those slots. In this way,
 * the peer-to-peer network translates approximate, cached data from heartbeats into synchronous,
 * coordinated instance provision for players.
 */
public class ServerClusters implements ServerClustersAPI {

  private static final String PLUGIN_NAME = "ServerClusters";

  private static volatile ServerClusters singleton;

  private final PluginMain plugin;
  private final ServerClustersConfig config;

  private final PubSubMessager messager;

  private final HeartbeatSubscription heartbeatListener;
  private final NetworkStatus network;

  private final SlotManager slotManager;
  private final BeatingHeart beatingHeart;
  private final PlayerRelocator relocator;
  private final InstanceConsolidator consolidator;

  private boolean heartBeating;

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

    this.messager = PubSub.getSingleton().getMessager(config.getMessagerInstanceName());
    if (messager == null) {
      throw new IllegalStateException("a messager for the configured name could not be found");
    }

    // initializes heartbeat listening and network-status caching
    heartbeatListener =
        new HeartbeatSubscription(messager, config.getHeartbeatChannel(),
            config.getShutdownChannel());
    network = new NetworkCache(config.getServerTimeout(), config.getServerId());
    heartbeatListener.registerListener((NetworkCache) network);

    // initializes slot manager that will track this server's open slots and handle other server's
    // reservation requests
    BukkitSlotManager bukkitSlots =
        new BukkitSlotManager(plugin, config.getTotalSlots(), config.getReservationTimeout(),
            config.strictReservations());

    plugin.getServer().getPluginManager().registerEvents(bukkitSlots, plugin);
    this.slotManager = bukkitSlots;

    // starts this server's heart beating so the network will know about it
    BeatingHeart heart = null;
    try {
      heart = new BeatingHeart(config, messager, slotManager);
    } catch (Exception e) {
      e.printStackTrace();
    }
    beatingHeart = heart;

    relocator = new PlayerRelocator(config, network, slotManager);
    if (config.attemptInstanceConsolidations()) {
      consolidator = new InstanceConsolidator(config, network, slotManager, relocator);
    } else {
      consolidator = null;
    }

    plugin.getCommand("networkstatus")
        .setExecutor(
            new NetworkStatusCommand(network, slotManager, config.getServerId(), config
                .getClusterId()));

    heartBeating = true;
  }

  @Override
  public String getServerId() {
    return config.getServerId();
  }

  @Override
  public String getClusterId() {
    return config.getClusterId();
  }

  @Override
  public int getClusterSize(String clusterId) {
    return network.getClusterSize(clusterId);
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
    if (!heartBeating) {
      SettableFuture<Boolean> dummy = SettableFuture.create();
      dummy.set(false);
      return dummy;
    }
    return slotManager.setTotalSlots(totalSlots);
  }

  @Override
  public ListenableFuture<Boolean> sendPlayersToCluster(String clusterId, UUID... playersToSend)
      throws IllegalArgumentException {

    Set<UUID> pSet = sanitizePlayers(playersToSend);

    // tolerates and ignores offline players, because it is not really an illegal argument that
    // indicates a logical issue; it is just likely references to players who have since logged off.
    if (pSet.isEmpty()) {
      SettableFuture<Boolean> ret = SettableFuture.create();
      ret.set(true);
      return ret;
    }

    ServerSelectionMode mode = config.getSelectionMode(clusterId);
    if (mode == null) {
      mode = ServerSelectionMode.RANDOM;
      plugin.getLogger().warning(
          "Players are being sent to cluster '" + clusterId
              + "', but it is not configured. Defaulting to random instance selection...");
    }

    return relocator.sendPlayersToCluster(clusterId, mode, pSet);
  }

  @Override
  public ListenableFuture<Boolean> sendPlayersToPlayer(UUID targetPlayerId, UUID... playersToSend)
      throws IllegalArgumentException {

    Set<UUID> pSet = sanitizePlayers(playersToSend);

    // tolerates and ignores offline players, because it is not really an illegal argument that
    // indicates a logical issue; it is just likely references to players who have since logged off.
    if (pSet.isEmpty()) {
      SettableFuture<Boolean> ret = SettableFuture.create();
      ret.set(true);
      return ret;
    }

    return relocator.sendPlayersToPlayer(targetPlayerId, pSet);
  }

  @Override
  public ListenableFuture<Boolean> sendPlayersToPlayer(String targetPlayerName,
      UUID... playersToSend) throws IllegalArgumentException {

    Set<UUID> pSet = sanitizePlayers(playersToSend);

    // tolerates and ignores offline players, because it is not really an illegal argument that
    // indicates a logical issue; it is just likely references to players who have since logged off.
    if (pSet.isEmpty()) {
      SettableFuture<Boolean> ret = SettableFuture.create();
      ret.set(true);
      return ret;
    }

    return relocator.sendPlayersToPlayer(targetPlayerName, pSet);
  }

  @Override
  public ListenableFuture<Boolean> closeThisServer() {
    ListenableFuture<Boolean> ret = null;
    if (!heartBeating) {
      ret = SettableFuture.create();
      ((SettableFuture<Boolean>) ret).set(false);

    } else {
      heartBeating = false;
      if (beatingHeart != null) {
        beatingHeart.sendShutdownNotification();
      }
      ret = slotManager.setTotalSlots(0);
    }

    return ret;
  }

  /**
   * Gets the plugin instance for the <code>ServerClusters</code> utility.
   * 
   * @return <code>ServerClusters</code> main plugin class.
   */
  public PluginMain getPlugin() {
    return plugin;
  }

  void onDisable() {
    heartbeatListener.destroy();
    if (beatingHeart != null) {
      beatingHeart.destroy();
    }
    relocator.destroy();
    if (consolidator != null) {
      consolidator.destroy();
    }
    singleton = null;
  }

  private Set<UUID> sanitizePlayers(UUID[] players) throws IllegalArgumentException {
    if (players == null || players.length < 1) {
      throw new IllegalArgumentException("players cannot be null or empty");
    }

    Set<UUID> pSet = new HashSet<UUID>();
    for (UUID p : players) {
      if (p != null && plugin.getServer().getPlayer(p).isOnline()) {
        pSet.add(p);
      }
    }
    return pSet;
  }

}
