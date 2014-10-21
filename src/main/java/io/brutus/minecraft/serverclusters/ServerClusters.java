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
public class ServerClusters {

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

  /**
   * Gets the id of this server, as it is uniquely identified on the network to other servers,
   * proxies, etc.
   * 
   * @return This server's id. <code>null</code> if p2p-server functionality is disabled.
   */
  public String getServerId() {
    return config.getGameServerId();
  }

  /**
   * Makes an asynchronous attempt to send players to an instance of the given cluster together.
   * <p>
   * Players will be sent as a group to the same instance of the cluster. Only servers with enough
   * slots for every player will be considered. If the players can be split up, call this method
   * separately for each player. Sending players as a group unnecessarily is less efficient, less
   * optimal, and more likely to fail.
   * <p>
   * Finds the best server in the cluster based on its configuration and the number of players being
   * sent.
   * <p>
   * Players will be relocated asynchronously (not immediately and not particularly fast). It will
   * take some time before an instance is found and approved and the players are relocated to it.
   * During that time, the players will still be able to run around, play, and interact with this
   * server they are currently on.
   * <p>
   * Once this process has started, it cannot be stopped. If multiple requests are made for the same
   * player, it may cause strange behavior. They might be bounced around between servers and no
   * guarantee is made about what order requests would be finished in. This also applies for other
   * methods of server relocation not through this method.
   * <p>
   * Can fail. It is possible no instances will be found, or that this method could fail for any of
   * a number of other reasons.
   * <p>
   * Ignores attempts to send players who are not currently online this server.
   * 
   * @param clusterId The id of the cluster to send the players to.
   * @param players The player or players to send to an instance of the server cluster.
   * @return The asynchronous, future result of this attempt to relocate players. Returns a value
   *         when the request finishes. <code>true</code> if the relocation is successful (does not
   *         guarantee the players make it to their destination, but does successfully find a server
   *         and send them there).<code>false</code> if the relocation fails, such as if no valid
   *         servers exist for the given cluster.
   * @throws IllegalArgumentException on a <code>null</code> parameter, an empty cluster id, or a
   *         cluster id that is not configured.
   */
  public ListenableFuture<Boolean> sendPlayers(String clusterId, UUID... players)
      throws IllegalArgumentException {
    if (players == null || players.length < 1) {
      throw new IllegalArgumentException("players cannot be null or empty");
    }

    ServerSelectionMode mode = config.getSelectionMode(clusterId);
    if (mode == null) {
      throw new IllegalArgumentException("could not find configuration for the given cluster id");
    }

    Set<UUID> pSet = new HashSet<UUID>();
    for (UUID p : players) {
      if (p != null && plugin.getServer().getPlayer(p).isOnline()) {
        pSet.add(p);
      }
    }

    if (pSet.isEmpty()) {
      SettableFuture<Boolean> ret = SettableFuture.create();
      ret.set(true);
      return ret;
    }

    return relocator.sendPlayers(clusterId, mode, pSet);
  }

  /**
   * Gets whether this instance of ServerClusters is acting in the role of server on the
   * ServerClusters decentralized, peer-to-peer network.
   * <p>
   * In the p2p-server role, this instance will send out heartbeat messages about its status to
   * connected servers, declare itself as a member of a cluster, and allow players to be sent to its
   * open slots as an instance of that cluster.
   * <p>
   * If not in the server role, this instance will still listen for heartbeats from connected
   * servers and be able to send players to clusters on the ServerClusters network.
   * 
   * @return <code>true</code> if this instance is a ServerClusters p2p server.
   */
  public boolean isP2PServer() {
    return config.actAsServer();
  }

  /**
   * Asynchronously updates the total number of player slots on this gameserver. Only affects new
   * connections; does not affect players currently online or players in the process of connecting
   * to this gameserver.
   * <p>
   * This number minus the number of players currently online defines how many players other servers
   * can send here and affects whether they will pick this server.
   * <p>
   * May not take effect immediately. If the number of slots is reduced and there are still players
   * connecting to this gameserver to occupy open slots, this process will not complete until they
   * finish connecting or timeout. No new player connections will be authorized, but existing ones
   * will be allowed to complete. It is not safe to assume that players have stopped legitimately
   * connecting until the asynchronous future object is done.
   * <p>
   * Does not affect players currently online this server in any way. For example, it is fully
   * possible to make sure no players are sent here, such as during a match, by setting the total
   * number of slots to <code>0</code> and then waiting for this process to complete. Doing so will
   * not kick any players that are currently online.
   * <p>
   * In addition to overwriting the total number of player slots, this overrides any existing
   * uncompleted attempt to update the total slots, causing its future objects to return
   * <code>false</code>.
   * <p>
   * Does nothing if p2p-server functionality is disabled.
   * 
   * @param totalSlots The total number of player slots that connected servers can fill with
   *        players.
   * @return A future object that will update when this operation is complete. Returns
   *         <code>true</code> when the number of slots is successfully altered and any existing
   *         player connections are resolved. <code>false</code> if the process is interrupted
   *         before definitively completing or if p2p-server functionality is disabled.
   * @throws IllegalArgumentException on a negative value.
   */
  public ListenableFuture<Boolean> updateTotalSlots(int totalSlots) throws IllegalArgumentException {
    if (!isP2PServer()) {
      SettableFuture<Boolean> ret = SettableFuture.create();
      ret.set(false);
      return ret;
    }
    return slotManager.setTotalSlots(totalSlots);
  }

  /**
   * Gets the id of the cluster that this server is a part of, if any.
   * 
   * @return The id of this server's cluster. <code>null</code> if this server is not configured to
   *         be part of a cluster or if p2p-server functionality is disabled.
   */
  public String getClusterId() {
    if (!isP2PServer()) {
      return config.getServerConfig().getClusterId();
    }
    return null;
  }

  /**
   * Stops this server from sending heartbeats to connected servers.
   * <p>
   * As a result, this server will stop being considered as an instance of its cluster. Connected
   * servers will no longer attempt to send players to this server, regardless of how many open
   * slots there are.
   * <p>
   * Does not stop this server from receiving heartbeats or finding instances of clusters to send
   * players to.
   * <p>
   * Does nothing if p2p-server functionality is disabled.
   */
  public void stopHeartbeat() {
    if (!isP2PServer()) {
      return;
    }
    heartbeats.shutdown();
  }

  /**
   * Gets the plugin instance for the <code>ServerClusters</code> utility.
   * 
   * @return <code>ServerClusters</code> main plugin class.
   */
  public PluginMain getPlugin() {
    return plugin;
  }

}
