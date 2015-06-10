package io.brutus.minecraft.serverclusters.bungee;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import io.brutus.minecraft.serverclusters.networkstatus.NetworkStatus;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;
import io.brutus.minecraft.serverclusters.sendplayer.PlayerRelocationClient;
import io.brutus.minecraft.serverclusters.sendplayer.PlayerSender;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

/**
 * Selects a server from a default cluster(s) for players when they first log in.
 */
public class FirstJoinServerSelecter {

  private Plugin plugin;
  private PlayerRelocationClient relocator;
  private BungeeConfiguration config;

  private Map<UUID, String> destinations;
  private Map<UUID, LoginEvent> heldEvents;

  /**
   * Class constructor.
   * 
   * @param plugin The plugin main.
   * @param networkStatus The status of the network.
   * @param messager The pub/sub messaging instance for use in making reservation requests and
   *        listening for responses.
   * @param config The main bungee configuration.
   * @throws IllegalArgumentException On a <code>null</code> parameter.
   */
  public FirstJoinServerSelecter(Plugin plugin, NetworkStatus networkStatus,
      PubSubMessager messager, BungeeConfiguration config) throws IllegalArgumentException {
    if (plugin == null || networkStatus == null || messager == null || config == null) {
      throw new IllegalArgumentException("parameters cannot be null");
    }

    this.plugin = plugin;
    this.config = config;

    this.relocator =
        new PlayerRelocationClient(config.getBungeeId(), networkStatus, new StoragePlayerSender(),
            messager, config);

    this.destinations = new ConcurrentHashMap<UUID, String>();
    this.heldEvents = new ConcurrentHashMap<UUID, LoginEvent>();

    plugin.getProxy().getPluginManager().registerListener(plugin, new PlayerProxyJoinListener());
  }

  /**
   * Listens for players joining the proxy.
   */
  public class PlayerProxyJoinListener implements Listener {

    // gets an instance of the default server cluster for logging-in players
    @EventHandler
    public void onLogin(LoginEvent event) {

      String host = event.getConnection().getVirtualHost().getHostString();
      final UUID playerId = event.getConnection().getUniqueId();

      String clusterId = config.getForcedHostDefaultClusterId(host);
      if (clusterId == null || clusterId.isEmpty()) {
        // no default cluster defined for the host, let's bungeecord carry out vanilla behavior
        return;
      }

      event.registerIntent(plugin);
      heldEvents.put(playerId, event);

      Set<UUID> idSet = new HashSet<UUID>();
      idSet.add(playerId);

      ServerSelectionMode mode = config.getSelectionMode(clusterId);
      if (mode == null) {
        mode = ServerSelectionMode.RANDOM;
        plugin.getLogger().warning(
            "Players are being sent to cluster '" + clusterId
                + "', but it is not configured. Defaulting to random instance selection...");
      }

      try {
        final ListenableFuture<Boolean> fut =
            relocator.sendPlayersToCluster(clusterId, mode, idSet);

        fut.addListener(new Runnable() {
          @Override
          public void run() {
            // stops holding the login event so it the player can be sent to their destination, if
            // one was found for them.
            LoginEvent heldEvent = heldEvents.remove(playerId);
            if (heldEvent != null) {
              heldEvent.completeIntent(plugin);
            }
          }
        }, MoreExecutors.sameThreadExecutor());

      } catch (ConcurrentModificationException e) {
        System.out.println("[" + getClass().getSimpleName() + "] Attempted to send a player '"
            + event.getConnection().getName() + "' who was already in the process of being sent.");

        // kicks the player if they already have a sending in progress
        heldEvents.remove(playerId);
        event.completeIntent(plugin);

        event.setCancelled(true);
        event.setCancelReason("Too many login requests. Wait a few seconds and try again.");
      }
    }

    // uses the destination server that was obtained when the player is about to connect to their
    // first server
    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {

      // not connecting for the first time, ignores
      if (event.getPlayer().getServer() != null) {
        return;
      }

      UUID uid = event.getPlayer().getUniqueId();
      String destinationStr = destinations.remove(uid);
      ServerInfo destinationServer = plugin.getProxy().getServerInfo(destinationStr);

      if (destinationServer != null) {
        event.setTarget(destinationServer);
      }
    }

    // cleanup
    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
      UUID uid = event.getPlayer().getUniqueId();
      destinations.remove(uid);
      LoginEvent heldEvent = heldEvents.remove(uid);
      if (heldEvent != null) {
        heldEvent.completeIntent(plugin);
      }
    }

  }

  /**
   * A player-sender that simply stores players' destinations so they can be used later.
   */
  private class StoragePlayerSender implements PlayerSender {

    @Override
    public void sendPlayer(UUID playerId, String destinationServer) throws IllegalStateException,
        IllegalArgumentException {
      destinations.put(playerId, destinationServer);
    }

  }

}
