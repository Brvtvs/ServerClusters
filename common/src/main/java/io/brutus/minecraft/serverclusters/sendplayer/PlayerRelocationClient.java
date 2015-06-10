package io.brutus.minecraft.serverclusters.sendplayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import io.brutus.minecraft.serverclusters.config.SharedConfiguration;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkStatus;
import io.brutus.minecraft.serverclusters.networkstatus.ServerStatus;
import io.brutus.minecraft.serverclusters.protocol.ReservationRequest;
import io.brutus.minecraft.serverclusters.protocol.ReservationResponse;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import io.brutus.networking.pubsubmessager.Subscriber;

/**
 * Makes attempts to relocate players to server clusters on the network.
 * <p>
 * Based on the target type, attempts to find the best gameserver instance for players that has
 * enough open slots for them. When an appropriate instance is found, a request is made for a
 * reservation on the desired slots. If a response is received approving that reservation, the
 * players are then sent to the target server.
 * <p>
 * Supports sending players together in a group. Also supports multiple targeting parameters, such
 * as targeting a given cluster, or else at a specific player somewhere on the network.
 */
public class PlayerRelocationClient {

  private final String thisNodeId;

  private final NetworkStatus networkStatus;
  private final PlayerSender playerSender;

  private final PubSubMessager messager;
  private final byte[] requestChannel;
  private final byte[] responseChannel;
  private final long responseTimeout;
  private final ResponseSubscriber sub;

  private final ExecutorService threadPool;
  private final AtomicInteger requestCounter;
  private final Map<Integer, ServerGroupRelocationAttempt> clusterAttempts;
  private final Map<Integer, PlayerRelocationAttempt> playerAttempts;

  private final Set<UUID> inProgress;

  /**
   * Class constructor.
   * 
   * @param thisNodeId The unique id of the network node that this relocation-server is running on.
   *        Essential to uniquely identify and route messages.
   * @param networkStatus The status of the network, from which to pull potential target servers
   *        from.
   * @param playerSender The service for sending players to gameservers.
   * @param messager The messager to listen to requests on and send responses on.
   * @param config The network configuration.
   * @throws IllegalArgumentException On a <code>null</code> or empty parameter.
   */
  public PlayerRelocationClient(String thisNodeId, NetworkStatus networkStatus,
      PlayerSender playerSender, PubSubMessager messager, SharedConfiguration config)
      throws IllegalArgumentException {

    if (thisNodeId == null || thisNodeId.isEmpty()) {
      throw new IllegalArgumentException("node id cannot be null or empty");
    } else if (networkStatus == null) {
      throw new IllegalArgumentException("network status cannot be null");
    } else if (playerSender == null) {
      throw new IllegalArgumentException("player sender cannot be null");
    } else if (messager == null) {
      throw new IllegalArgumentException("pub/sub messager cannot be null");
    } else if (config == null) {
      throw new IllegalArgumentException("config cannot be null");
    }
    this.thisNodeId = thisNodeId;

    this.networkStatus = networkStatus;
    this.playerSender = playerSender;

    this.messager = messager;

    this.requestChannel = config.getReservationRequestChannel();
    this.responseChannel = config.getReservationResponseChannel();
    if (requestChannel == null || requestChannel.length < 1 || responseChannel == null
        || responseChannel.length < 1) {
      throw new IllegalArgumentException("reservation channels cannot be null or empty");
    }

    this.responseTimeout = config.getReservationResponseTimeout();
    if (responseTimeout < 1) {
      throw new IllegalArgumentException("response timeout must be positive");
    }

    sub = new ResponseSubscriber();
    messager.subscribe(responseChannel, sub);

    threadPool = Executors.newCachedThreadPool();
    requestCounter = new AtomicInteger(Integer.MIN_VALUE);
    clusterAttempts = new ConcurrentHashMap<Integer, ServerGroupRelocationAttempt>();
    playerAttempts = new ConcurrentHashMap<Integer, PlayerRelocationAttempt>();

    inProgress = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
  }

  /**
   * Stops this from handling any more relocations and kills its connections. Cannot be reversed.
   */
  public void destroy() {
    messager.unsubscribe(responseChannel, sub);
    threadPool.shutdown();
  }

  /**
   * Makes an asynchronous attempt to send players to an instance of the given Minecraft-server
   * cluster together.
   * <p>
   * Players will be sent as a group to the same instance of the cluster. Only servers with enough
   * slots for every player will be considered. If the players can be split up, call this method
   * separately for each player. Sending players as a group unnecessarily is less efficient, less
   * optimal, and more likely to fail.
   * <p>
   * Finds the best server in the cluster based on its configuration and the number of players being
   * sent. If the cluster is not configured, picks at random.
   * <p>
   * Players will be relocated asynchronously (not immediately and not particularly fast). It will
   * take some time before an instance is found and approved and the players are relocated to it.
   * During that time, the players will still be able to run around, play on, and interact with this
   * server.
   * <p>
   * Once this process has started, it cannot be stopped. Two sending attempts for the same player
   * cannot run at the same time.
   * <p>
   * Ignores attempts to send players who are not currently online this server.
   * <p>
   * Can fail if no appropriate instances for the cluster are found or for any of a number of other
   * reasons.
   * 
   * @param clusterId The id of the cluster to send the players to.
   * @param players The player or players to send to an instance of the server cluster.
   * @return The asynchronous, future result of this attempt to relocate players. Returns a value
   *         when the request finishes. <code>true</code> if the relocation is successful (does not
   *         guarantee the players make it to their destination, but does successfully find a server
   *         and try to send them there). <code>false</code> if the relocation fails, such as if no
   *         valid servers exist for the given cluster.
   * @throws IllegalArgumentException on a <code>null</code> parameter or an empty cluster id.
   * @throws ConcurrentModificationException On sending a player that is already in the process of
   *         being sent.
   */
  public ListenableFuture<Boolean> sendPlayersToCluster(String clusterId, ServerSelectionMode mode,
      Set<UUID> players) throws IllegalArgumentException, ConcurrentModificationException {
    if (clusterId == null || clusterId.equals("")) {
      throw new IllegalArgumentException("cluster id cannot be null or empty");
    } else if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("must have at least one player");
    } else if (mode == null) {
      throw new IllegalArgumentException("server selection mode cannot be null");
    }

    ServerGroupRelocationAttempt attempt =
        new ServerGroupRelocationAttempt(clusterId, mode, players);
    return attempt.start();
  }

  /**
   * Makes an asynchronous attempt to send players to the Minecraft server of a given player. Takes
   * the target player's unique id as an argument.
   * <p>
   * Players will be sent as a group to the same instance of the player. The request will fail and
   * not send any players if there are not enough open slots for all players being sent. If each
   * player should be sent individually and their attempt should succeed/fail individually, then
   * this method should be called separately for each player.
   * <p>
   * Players will be relocated asynchronously (not immediately and not particularly fast). It will
   * take some time before the target player is located, their server approves the relocation, and
   * the players are sent to it. During that time, the players being sent will still be able to run
   * around, play on, and interact with this server.
   * <p>
   * Once this process has started, it cannot be stopped. Two sending attempts for the same player
   * cannot run at the same time.
   * <p>
   * Can fail if the target player is not currently online an appropriate connected server or for
   * any of a number of other reasons. There is no guarantee that by the time the player(s) arrive
   * at the target player's server that the target player will still be online there.
   * <p>
   * Ignores attempts to send players who are not currently online this server.
   * 
   * @param targetPlayerId The player whose server to try to send the given players to.
   * @param players The players to try to send to the server of the target player.
   * @return The asynchronous, future result of this attempt to relocate players. Returns a value
   *         when the request finishes. <code>true</code> if the relocation is successful (does not
   *         guarantee the players make it to their destination, but successfully tries to send them
   *         to the target server). <code>false</code> if the relocation fails, such as if the
   *         target player is not online a connected server.
   * @throws IllegalArgumentException on a <code>null</code> parameter.
   * @throws ConcurrentModificationException On sending a player that is already in the process of
   *         being sent.
   */
  public ListenableFuture<Boolean> sendPlayersToPlayer(UUID targetPlayerId, Set<UUID> players)
      throws IllegalArgumentException, ConcurrentModificationException {
    if (targetPlayerId == null) {
      throw new IllegalArgumentException("player id cannot be null");
    } else if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("must have at least one player");
    }

    PlayerRelocationAttempt attempt = new PlayerRelocationAttempt(targetPlayerId, null, players);
    return attempt.start();
  }

  /**
   * Makes an asynchronous attempt to send players to the Minecraft server of a given player. Takes
   * the target player's name as an argument.
   * <p>
   * Players will be sent as a group to the same instance of the player. The request will fail and
   * not send any players if there are not enough open slots for all players being sent. If each
   * player should be sent individually and their attempt should succeed/fail individually, then
   * this method should be called separately for each player.
   * <p>
   * Players will be relocated asynchronously (not immediately and not particularly fast). It will
   * take some time before the target player is located, their server approves the relocation, and
   * the players are sent to it. During that time, the players being sent will still be able to run
   * around, play on, and interact with this server.
   * <p>
   * Once this process has started, it cannot be stopped. Two sending attempts for the same player
   * cannot run at the same time.
   * <p>
   * Can fail if the target player is not currently online an appropriate connected server or for
   * any of a number of other reasons. There is no guarantee that by the time the player(s) arrive
   * at the target player's server that the target player will still be online there.
   * <p>
   * Ignores attempts to send players who are not currently online this server.
   * 
   * @param targetPlayerName The name of the player whose server to try to send the given players to
   *        (not case sensitive).
   * @param players The players to try to send to the server of the target player.
   * @return The asynchronous, future result of this attempt to relocate players. Returns a value
   *         when the request finishes. <code>true</code> if the relocation is successful (does not
   *         guarantee the players make it to their destination, but successfully tries to send them
   *         to the target server). <code>false</code> if the relocation fails, such as if the
   *         target player is not online a connected server.
   * @throws IllegalArgumentException on a <code>null</code> parameter or an empty player name.
   * @throws ConcurrentModificationException On sending a player that is already in the process of
   *         being sent.
   */
  public ListenableFuture<Boolean> sendPlayersToPlayer(String targetPlayerName, Set<UUID> players)
      throws IllegalArgumentException, ConcurrentModificationException {
    if (targetPlayerName == null || targetPlayerName.equals("")) {
      throw new IllegalArgumentException("player name cannot be null or empty");
    } else if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("must have at least one player");
    }

    PlayerRelocationAttempt attempt = new PlayerRelocationAttempt(null, targetPlayerName, players);
    return attempt.start();
  }

  /**
   * Attempts to send a group of players to a list of specific servers.
   * <p>
   * Sending attempts will happen in same order as the list.
   * <p>
   * For internal use. Not safe for and has no use for external clients through the main API.
   * 
   * @param orderedServers A list of the servers to attempt to send the players to.
   * @param players The ids of the players to send.
   * @return The asynchronous, future result of this attempt to relocate players. Returns a value
   *         when the request finishes. <code>true</code> if the relocation is successful (does not
   *         guarantee the players make it to their destination, but does successfully find a server
   *         and try to send them there). <code>false</code> if the relocation fails, such as if the
   *         servers deny the requests.
   * @throws IllegalArgumentException on a <code>null</code> parameter or an empty collection.
   * @throws ConcurrentModificationException On sending a player that is already in the process of
   *         being sent.
   */
  public ListenableFuture<Boolean> sendPlayersToServers(List<ServerStatus> orderedServers,
      Set<UUID> players) throws IllegalArgumentException, ConcurrentModificationException {
    if (orderedServers == null || players == null) {
      throw new IllegalArgumentException("params cannot be null");
    } else if (orderedServers.isEmpty() || players.isEmpty()) {
      throw new IllegalArgumentException("collections cannot be empty");
    }

    ServerGroupRelocationAttempt attempt =
        new ServerGroupRelocationAttempt(orderedServers, players);
    return attempt.start();
  }

  private void onResponseMessage(byte[] message) {
    ReservationResponse rr = null;
    try {
      rr = ReservationResponse.fromBytes(message);

    } catch (Exception e) {
      System.out.println("[ServerClusters " + getClass().getSimpleName()
          + "] Received a message on the reservation-response channel that could not be parsed.");
      e.printStackTrace();
      return;
    }

    if (!rr.getTargetServer().equals(thisNodeId)) { // not for this server
      return;
    }

    ServerGroupRelocationAttempt clusterAttempt = clusterAttempts.get(rr.getRequestId());
    if (clusterAttempt != null) {
      clusterAttempt.onResponse(rr);
    }

    PlayerRelocationAttempt playerAttempt = playerAttempts.get(rr.getRequestId());
    if (playerAttempt != null) {
      playerAttempt.onResponse(rr);
    }
  }

  private class ResponseSubscriber implements Subscriber {

    @Override
    public void onMessage(byte[] channel, byte[] message) {
      if (Arrays.equals(channel, responseChannel)) {
        onResponseMessage(message);
      }
    }

  }

  /**
   * Private helper runnable class that attempts to get a reservation on the server of a given
   * player.
   * <p>
   * Defines the behavior of sending the request, waiting for the response, reacting to the
   * response, timeouts, etc.
   */
  private class PlayerRelocationAttempt implements Runnable {

    private static final long WAIT_INTERVAL = 50;

    private final int id;
    private final SettableFuture<Boolean> callback;

    private final Set<UUID> players;
    private final UUID targetId;
    private final String targetName;

    private volatile boolean complete;

    private PlayerRelocationAttempt(UUID targetId, String targetName, Set<UUID> players) {
      if (targetId == null && (targetName == null || targetName.equals(""))) {
        throw new IllegalArgumentException("must have either a target id or a target name");
      }
      this.id = requestCounter.getAndIncrement();

      this.players = players;
      for (UUID id : players) {
        if (inProgress.contains(id)) {
          throw new ConcurrentModificationException(
              "player "
                  + id.toString()
                  + " is already in the process of being relocated. Two requests cannot run concurrently for the same player.");
        }
      }

      this.targetId = targetId;
      this.targetName = targetName;
      this.callback = SettableFuture.create();

      playerAttempts.put(id, this);
    }

    @Override
    public void run() {

      // TODO debug
      System.out.println("[ServerClusters " + getClass().getSimpleName()
          + "] Sending a reservation request message of id " + id
          + " to the server of player (UUID:  " + targetId + ", name: " + targetName + ") for "
          + players.size() + " players.");

      if (targetId != null) {
        messager.publish(requestChannel,
            ReservationRequest.createMessageToPlayer(targetId, thisNodeId, id, players));
      } else {
        messager.publish(requestChannel,
            ReservationRequest.createMessageToPlayer(targetName, thisNodeId, id, players));
      }

      long timePassed = 0;
      while (!complete) {
        if (timePassed > responseTimeout) {
          complete(false);
          break;
        }
        try {
          Thread.sleep(WAIT_INTERVAL);
        } catch (InterruptedException e) {
          System.out
              .println("[ServerClusters PlayerRelocationClient] Interruption on a response timeout thread. Returning false...");
          e.printStackTrace();
          complete(false);
        }
        timePassed += WAIT_INTERVAL;
      }
    }

    private void onResponse(ReservationResponse response) {

      // TODO debug
      System.out.println("[ServerClusters " + getClass().getSimpleName()
          + "] Received reservation response of id " + response.getRequestId() + " from "
          + response.getRespondingServer() + ". Approved: " + response.isApproved());

      if (response.isApproved()) {
        for (UUID playerId : players) {
          playerSender.sendPlayer(playerId, response.getRespondingServer());
        }
        complete(true);
      } else {
        complete(false);
      }
    }

    private void complete(boolean successful) {
      inProgress.removeAll(players);
      complete = true;
      clusterAttempts.remove(id);
      callback.set(successful);
    }

    private ListenableFuture<Boolean> start() {
      inProgress.addAll(players);
      threadPool.execute(this);
      return this.callback;
    }

  }

  /**
   * Private helper runnable class that attempts to get a reservation on one of a group of instances
   * for a player or group of players.
   * <p>
   * Can attempt to send players to a cluster or a specified group of servers.
   * <p>
   * Defines the behavior of sending the request, waiting for the response, reacting to the
   * response, timeouts, etc.
   */
  private class ServerGroupRelocationAttempt implements Runnable {

    private static final int MAX_TRIES = 20; // for sanity: does not try forever.
    private static final long WAIT_INTERVAL = 50;

    private final int id;
    private final SettableFuture<Boolean> callback;
    private final Set<UUID> players;

    private String clusterId;
    private ServerSelectionMode mode;

    private Set<String> serversTried;
    private String currentServerId;
    private Iterator<ServerStatus> servers;

    private volatile boolean wakeUp;
    private volatile boolean complete;

    /**
     * Constructor that takes a cluster's id as its target.
     * 
     * @param clusterId The id of the cluster to try to send players to.
     * @param mode The mode with which to select servers in the cluster.
     * @param players The players to send.
     */
    private ServerGroupRelocationAttempt(String clusterId, ServerSelectionMode mode,
        Set<UUID> players) {
      this.id = requestCounter.getAndIncrement();
      this.clusterId = clusterId;
      this.mode = mode;

      this.players = players;
      for (UUID id : players) {
        if (inProgress.contains(id)) {
          throw new ConcurrentModificationException(
              "player "
                  + id.toString()
                  + " is already in the process of being relocated. Two requests cannot run concurrently for the same player.");
        }
      }

      this.callback = SettableFuture.create();

      this.serversTried = new HashSet<String>();

      clusterAttempts.put(id, this);
    }

    /**
     * Constructor that takes a list of specific servers to try in order.
     * 
     * @param servers The servers to try.
     * @param players The players to send.
     */
    private ServerGroupRelocationAttempt(List<ServerStatus> servers, Set<UUID> players) {
      this.id = requestCounter.getAndIncrement();
      this.players = players;
      this.servers = servers.iterator();
      this.callback = SettableFuture.create();

      this.serversTried = new HashSet<String>();

      clusterAttempts.put(id, this);
    }

    @Override
    public void run() {
      int tries = 0;
      // continuously retries connecting the player(s) to servers in the cluster until it finds
      // definitive failure
      while (!complete && tries++ <= MAX_TRIES) {

        // if targeting a cluster, gets a list of of the cluster's instance to try
        if (clusterId != null) {
          servers = networkStatus.getServers(clusterId, mode, players.size()).iterator();

        } // else just uses the predefined list of servers to try

        boolean foundNew = false;
        while (servers.hasNext()) {

          ServerStatus server = servers.next();

          // does not retry servers that already denied or failed to respond.
          if (server != null && !serversTried.contains(server.getServerId())) {
            currentServerId = server.getServerId();
            serversTried.add(currentServerId);
            foundNew = true;
            break;
          }
        }
        if (!foundNew) {
          complete(false);
          return;
        }

        // TODO debug
        System.out.println("[ServerClusters " + getClass().getSimpleName()
            + "] Sending a reservation request message of id " + id + " to " + currentServerId
            + " for " + players.size() + " players.");

        messager.publish(requestChannel,
            ReservationRequest.createMessageToServer(currentServerId, thisNodeId, id, players));

        long timeWaited = 0;
        while (timeWaited < responseTimeout) {
          try {
            Thread.sleep(WAIT_INTERVAL);
          } catch (Exception e) {
            System.out.println("[ServerClusters " + getClass().getSimpleName()
                + "] Interruption on a response timeout thread. Returning false...");
            e.printStackTrace();
            complete(false);
          }
          timeWaited += WAIT_INTERVAL;
          if (wakeUp) {
            wakeUp = false;
            break;
          }
        }
      }
      if (!complete) {
        complete(false);
      }
    }

    private void onResponse(ReservationResponse response) {

      // TODO debug
      System.out.println("[ServerClusters " + getClass().getSimpleName()
          + "] Received reservation response of id " + response.getRequestId() + " from "
          + response.getRespondingServer() + ". Approved: " + response.isApproved());

      if (response.isApproved()) {
        for (UUID playerId : players) {
          playerSender.sendPlayer(playerId, response.getRespondingServer());
        }
        complete(true);
      }
      wakeUp = true;
    }

    private void complete(boolean successful) {
      inProgress.removeAll(players);
      complete = true;
      wakeUp = true;
      clusterAttempts.remove(id);
      callback.set(successful);
    }

    private ListenableFuture<Boolean> start() {
      inProgress.addAll(players);
      threadPool.execute(this);
      return this.callback;
    }

  }


}
