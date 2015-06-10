package io.brutus.minecraft.serverclusters.gameserver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.brutus.minecraft.pubsub.PubSub;
import io.brutus.minecraft.serverclusters.networkstatus.HeartbeatSubscription;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkCache;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkStatus;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;
import io.brutus.minecraft.serverclusters.sendplayer.PlayerRelocationClient;
import io.brutus.networking.pubsubmessager.PubSubMessager;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Main class for a game-server node on the ServerClusters network.
 * <p>
 * ServerClusters is a utility that groups interchangeable Minecraft servers together into clusters
 * so they can be provisioned as a group. Supports matchmaking and load-balancing provision for
 * players, so that when they are sent to a cluster, they can be sent to the currently optimal
 * instance.
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

  private static volatile ServerClusters singleton;

  private ServerClustersConfiguration config;
  private ServerUtils serverUtils;
  private PubSubMessager messager;

  private HeartbeatSubscription heartbeatListener;
  private NetworkStatus network;

  private SlotManager slotManager;
  private BeatingHeart beatingHeart;
  private PlayerRelocationServer relocationServer;
  private PlayerRelocationClient relocationClient;
  private InstanceConsolidator consolidator;

  private boolean heartBeating;

  /**
   * Gets the ServerClusters singleton.
   * 
   * @return The ServerClusters singleton.
   * @throws IllegalStateException If ServerClusters is not enabled.
   * @see #isEnabled()
   */
  public static ServerClusters getSingleton() throws IllegalStateException {
    if (singleton == null) {
      throw new IllegalStateException("ServerClusters is not enabled");
    }
    return singleton;
  }

  /**
   * Enables ServerClusters.
   * <p>
   * Does nothing if it is already enabled.
   * 
   * @param config The configuration that ServerClusters should use.
   * @param slotManager The manager for the slots of this local server.
   * @param serverUtils Utilities for interacting with this server implementation.
   * @throws IllegalArgumentException On a <code>null</code> parameter.
   */
  public static void onEnable(ServerClustersConfiguration config, SlotManager slotManager,
      ServerUtils serverUtils) throws IllegalArgumentException {
    if (singleton != null) {
      return;
    }
    singleton = new ServerClusters(config, slotManager, serverUtils);
    singleton.init();
  }

  /**
   * Disables ServerClusters.
   * <p>
   * Does nothing if it has not been enabled or has already been disabled.
   */
  public static void onDisable() {
    if (singleton == null) {
      return;
    }
    singleton.destroy();
    singleton = null;
  }

  /**
   * Gets whether ServerClusters is currently enabled and usable.
   * <p>
   * Does not get for sure whether the underlying minecraft-server platform is enabled, nor whether
   * individual ServerClusters components are initialized/enabled.
   * 
   * @return <code>true</code> if ServerClusters is enabled and {@link #getSingleton()} can be
   *         called safely.
   */
  public static boolean isEnabled() {
    return singleton != null;
  }

  private ServerClusters(ServerClustersConfiguration config, SlotManager slotManager,
      ServerUtils serverUtils) {
    if (config == null || slotManager == null || serverUtils == null) {
      throw new IllegalArgumentException("params cannot be null");
    }
    this.config = config;
    this.slotManager = slotManager;
    this.serverUtils = serverUtils;
  }

  private void init() {
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

    // starts this server's heart beating so the network will know about it
    try {
      beatingHeart = new BeatingHeart(config, messager, slotManager);
    } catch (Exception e) {
      e.printStackTrace();
    }

    relocationServer =
        new PlayerRelocationServer(config.getServerId(), messager, slotManager, config);
    relocationClient =
        new PlayerRelocationClient(config.getServerId(), network, serverUtils, messager, config);

    if (config.attemptInstanceConsolidations()) {
      consolidator = new InstanceConsolidator(config, network, slotManager, relocationClient);
    } else {
      consolidator = null;
    }

    heartBeating = true;
  }

  /**
   * Gets a set of generic utilities for interacting with this server's underlying implementation,
   * whatever that might be.
   * 
   * @return A group of common server utilities specific to the implementation of the server that
   *         ServerClusters is running on.
   */
  public ServerUtils getServerUtils() {
    return serverUtils;
  }

  /**
   * Gets a human-readable version of the network's current status.
   * <p>
   * Based on cached data and may be somewhat out of date.
   * 
   * @return The network's current status, as this server understands it.
   */
  public List<String> getHumanReadableNetworkStatus() {
    List<String> status = network.toStringList();
    // appends info specific to this server before returning
    status.add("Your server: " + getServerId() + " (cluster: " + getClusterId() + ", "
        + slotManager.getOpenSlots() + " open slots, " + slotManager.getTotalSlots()
        + " total slots)");
    return status;
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
      serverUtils.getLogger().warning(
          "Players are being sent to cluster '" + clusterId
              + "', but it is not configured. Defaulting to random instance selection...");
    }

    return relocationClient.sendPlayersToCluster(clusterId, mode, pSet);
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

    return relocationClient.sendPlayersToPlayer(targetPlayerId, pSet);
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

    return relocationClient.sendPlayersToPlayer(targetPlayerName, pSet);
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

  private void destroy() {
    heartbeatListener.destroy();
    if (beatingHeart != null) {
      beatingHeart.destroy();
    }
    relocationServer.destroy();
    relocationClient.destroy();
    if (consolidator != null) {
      consolidator.destroy();
    }
  }

  private Set<UUID> sanitizePlayers(UUID[] players) throws IllegalArgumentException {
    if (players == null || players.length < 1) {
      throw new IllegalArgumentException("players cannot be null or empty");
    }

    Set<UUID> pSet = new HashSet<UUID>();
    for (UUID p : players) {
      if (p != null && serverUtils.isPlayerOnline(p)) {
        pSet.add(p);
      }
    }
    return pSet;
  }

}
