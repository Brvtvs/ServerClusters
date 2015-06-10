package io.brutus.minecraft.serverclusters.bungee;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.brutus.minecraft.pubsub.PubSub;
import io.brutus.minecraft.serverclusters.networkstatus.HeartbeatSubscription;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkCache;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkChangeListener;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkStatus;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * Bungee plugin main for ServerClusters network-proxy node.
 * <p>
 * Listens to heartbeat messages and adds responsive servers to BungeeCord's server list.
 * <p>
 * Does not break compatability with normal, statically configured servers in any way. Just
 * adds/removes ServerClusters servers on top of the configured servers.
 */
public class BungeeMain extends Plugin implements NetworkChangeListener {

  private static final long REMOVAL_DELAY_SECONDS = 60;
  private static final TextComponent KICK_REASON = new TextComponent(
      "This instance is restarting, please try reconnecting.");

  private BungeeConfiguration config;
  private PubSubMessager messager;
  private HeartbeatSubscription heartbeats;
  private NetworkStatus networkStatus;

  private Set<String> dynamicServers;

  private volatile boolean initialized;

  @Override
  public void onEnable() {
    final BungeeMain thisPlugin = this;

    // runs asynchronously to be able to create threads
    getProxy().getScheduler().runAsync(this, new Runnable() {
      @Override
      public void run() {
        config = new BungeeConfiguration(thisPlugin);

        dynamicServers = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        messager = PubSub.getSingleton().getMessager(config.getMessagerInstanceName());

        heartbeats =
            new HeartbeatSubscription(messager, config.getHeartbeatChannel(), config
                .getShutdownChannel());

        networkStatus = new NetworkCache(config.getServerTimeout(), null);

        heartbeats.registerListener((NetworkCache) networkStatus);
        networkStatus.registerListener(thisPlugin);

        new FirstJoinServerSelecter(thisPlugin, networkStatus, messager, config);

        getProxy().getPluginManager().registerCommand(thisPlugin,
            new NetworkStatusCommand(networkStatus));

        initialized = true;
      }
    });

    // while we cannot create threads from the main thread, we still want to wait for initialization
    // to finish before other plugins load. In other words, this is a hacky workaround for the
    // SecurityManager. (note that instructing this thread to sleep does NOT work, which is why an
    // empty loop is necessary)
    while (!initialized) {
    }

    getLogger().info("initialization complete.");
  }

  @Override
  public void onDisable() {
    if (heartbeats != null) {
      heartbeats.destroy();
    }
    if (dynamicServers != null) {
      for (String serverId : dynamicServers) {
        removeServer(serverId);
      }
      dynamicServers.clear();
    }
    if (config != null) {
      config.destroy();
    }
    initialized = false;
  }

  // when a server first declares itself or recovers after a long downtime, adds it to the
  // bungeecord's list of servers.
  @Override
  public void onServerJoin(String serverId, String clusterId, String ip, int port) {
    if (dynamicServers.add(serverId)) {
      ServerInfo info =
          getProxy().constructServerInfo(serverId, new InetSocketAddress(ip, port),
              "ServerClusters node", false);
      getProxy().getServers().put(serverId, info);
    }
  }

  // schedules the removal of a server from bungeecord's list when it announces it will shut down
  @Override
  public void onServerWillShutdown(String serverId, String clusterId, String ip, int port) {
    if (dynamicServers.remove(serverId)) {
      removeServer(serverId);
    }
  }

  // schedules the removal of a server from bungeecord's list when it becomes unresponsive
  @Override
  public void onServerUnresponsive(String serverId, String clusterId, String ip, int port) {
    if (dynamicServers.remove(serverId)) {
      removeServer(serverId);
    }
  }

  /**
   * Removes a server from bungeecord.
   * <p>
   * Happens after a delay to make sure that servers have a good chance to relocate players, and
   * also gives servers a chance to become responsive again after brief downtimes without being
   * removed in the meantime.
   * <p>
   * Having the server in BungeeCord a few extra seconds does not negatively affect it in any way.
   * Removing servers that shutdown or crash is about long-term cleanup rather than any short-term
   * synchronization.
   * <p>
   * The removal will not happen if the server comes back before its scheduled removal.
   * 
   * @param serverId The id of the server to remove.
   */
  private void removeServer(final String serverId) {
    getProxy().getScheduler().schedule(this, new Runnable() {
      @Override
      public void run() {
        if (dynamicServers.contains(serverId)) {
          return;
        }
        ServerInfo info = getProxy().getServers().get(serverId);
        if (info != null) {
          // TODO relocate them to another server??
          for (ProxiedPlayer player : info.getPlayers()) {
            player.disconnect(KICK_REASON);
          }
        }
        getProxy().getServers().remove(serverId);
      }
    }, REMOVAL_DELAY_SECONDS, TimeUnit.SECONDS);
  }

}
