package io.brutus.minecraft.serverclusters.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.jodah.expiringmap.ExpiringMap;
import net.jodah.expiringmap.ExpiringMap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap.ExpirationPolicy;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import io.brutus.minecraft.serverclusters.NetworkStatus;
import io.brutus.minecraft.serverclusters.ServerClustersConfig;
import io.brutus.minecraft.serverclusters.protocol.Heartbeat;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

/**
 * A cache of data about connected servers, updated and maintained by their incoming heartbeat and
 * shutdown messages.
 */
public class NetworkCache implements NetworkStatus, ExpirationListener<String, ServerStatus> {

  private ServerClustersConfig config;

  private Multimap<String, ServerStatus> clusters; // <cluster id, server statuses within cluster>
  private ExpiringMap<String, ServerStatus> servers; // <server id, server status>

  public NetworkCache(ServerClustersConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("config cannot be null");
    }

    this.config = config;

    HashMultimap<String, ServerStatus> notThreadSafe = HashMultimap.create();
    clusters = Multimaps.synchronizedSetMultimap(notThreadSafe);

    servers =
        ExpiringMap.builder().expiration(config.getServerTimeout(), TimeUnit.MILLISECONDS)
            .expirationPolicy(ExpirationPolicy.ACCESSED).expirationListener(this).build();
  }

  @Override
  public void onHeartbeat(Heartbeat hb) throws IllegalArgumentException {
    if (hb == null) {
      throw new IllegalArgumentException("heartbeat cannot be null");
    }

    ServerStatus status = servers.get(hb.getServerId());

    if (status == null) {
      status = new ServerStatus(hb.getServerId(), hb.getClusterId(), hb.getOpenSlots());
      servers.put(status.getId(), status);
      clusters.put(status.getClusterId(), status);
    } else {
      status.updateOpenSlots(hb.getOpenSlots());
    }
  }

  @Override
  public List<String> getServers(String clusterId, ServerSelectionMode mode, int numPlayers)
      throws IllegalArgumentException {
    if (clusterId == null || clusterId.equals("")) {
      throw new IllegalArgumentException("cluster id cannot be null or empty");
    }
    if (mode == null) {
      throw new IllegalArgumentException("server selecter cannot be null");
    }
    if (numPlayers < 0) {
      throw new IllegalArgumentException("number of players cannot be negative");
    }

    List<String> ret = new ArrayList<String>();

    Collection<ServerStatus> clusteredServers = clusters.get(clusterId);
    if (clusteredServers == null || clusteredServers.isEmpty()) {
      return ret;
    }

    List<ServerStatus> servers = new ArrayList<ServerStatus>();
    for (ServerStatus server : clusteredServers) {
      if (hasTimedOut(server) || server.getOpenSlots() < numPlayers) { // ignores invalid servers
        continue;
      }
      servers.add(server);
    }

    if (servers.isEmpty()) {
      return ret;
    }

    Collections.sort(servers, mode);

    for (ServerStatus status : servers) {
      ret.add(status.getId());
    }

    return ret;
  }

  @Override
  public void expired(String serverId, ServerStatus status) {
    clusters.remove(status.getClusterId(), status);
  }

  @Override
  public String[] toStringArray() {
    List<String> ret = new LinkedList<String>();

    ret.add("[NetworkStatus] Clusters: ");

    for (String clusterId : clusters.keySet()) { // for each tracked cluster
      String str = "  " + clusterId + ": ";
      boolean anyServers = false;

      for (ServerStatus status : clusters.get(clusterId)) {
        if (hasTimedOut(status)) {
          continue;
        }
        anyServers = true;
        str = str + status.getId() + " (" + status.getOpenSlots() + " open slots), ";
      }
      if (anyServers) {
        // if the cluster has any servers, adds it to the status and removes the last floating
        // comma.
        ret.add(str.substring(0, str.length() - 2));
      }
    }

    return ret.toArray(new String[ret.size()]);
  }

  private boolean hasTimedOut(ServerStatus server) {
    return (System.currentTimeMillis() - server.getLastUpdated()) > config.getServerTimeout();
  }

}
