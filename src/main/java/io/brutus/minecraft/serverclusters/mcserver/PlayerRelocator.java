package io.brutus.minecraft.serverclusters.mcserver;

import io.brutus.minecraft.pubsub.PubSub;
import io.brutus.minecraft.serverclusters.bukkit.ServerUtil;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkStatus;
import io.brutus.minecraft.serverclusters.networkstatus.ServerStatus;
import io.brutus.minecraft.serverclusters.protocol.PlayerNameReservationRequest;
import io.brutus.minecraft.serverclusters.protocol.PlayerUuidReservationRequest;
import io.brutus.minecraft.serverclusters.protocol.ReservationRequest;
import io.brutus.minecraft.serverclusters.protocol.ReservationRequest.TargetType;
import io.brutus.minecraft.serverclusters.protocol.ReservationResponse;
import io.brutus.minecraft.serverclusters.protocol.ServerIdReservationRequest;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import io.brutus.networking.pubsubmessager.Subscriber;

import java.util.Arrays;
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

/**
 * Handles player-slot-reservation messaging and fulfilling attempts to send players to server
 * clusters.
 */
public class PlayerRelocator implements Subscriber {

  private final String thisServerId;

  private final NetworkStatus network;
  private final SlotManager slotManager;

  private final PubSubMessager messager;
  private final byte[] requestChannel;
  private final byte[] responseChannel;
  private final long responseTimeout;

  private final ExecutorService threadPool;
  private final AtomicInteger requestCounter;
  private final Map<Integer, ServerGroupRelocationAttempt> clusterAttempts;
  private final Map<Integer, PlayerRelocationAttempt> playerAttempts;

  public PlayerRelocator(ServerClustersConfig config, NetworkStatus network, SlotManager slotManager)
      throws IllegalArgumentException {

    if (config == null || network == null || slotManager == null) {
      throw new IllegalArgumentException("params cannot be null");
    }

    this.responseTimeout = config.getResponseTimeout();
    this.thisServerId = config.getServerId();

    this.slotManager = slotManager;
    this.network = network;

    this.messager = PubSub.getSingleton().getMessager(config.getMessagerInstanceName());
    if (messager == null) {
      throw new IllegalStateException("a messager for the configured name could not be found");
    }

    requestChannel = config.getReservationRequestChannel();
    responseChannel = config.getReservationResponseChannel();
    if (requestChannel == null || requestChannel.length < 1 || responseChannel == null
        || responseChannel.length < 1) {
      throw new IllegalArgumentException("reservation channels cannot be null or empty");
    }

    messager.subscribe(responseChannel, this);
    messager.subscribe(requestChannel, this);

    threadPool = Executors.newCachedThreadPool();
    requestCounter = new AtomicInteger(Integer.MIN_VALUE);
    clusterAttempts = new ConcurrentHashMap<Integer, ServerGroupRelocationAttempt>();
    playerAttempts = new ConcurrentHashMap<Integer, PlayerRelocationAttempt>();
  }

  /**
   * Stops this from handling any more relocations and kills its connections. Cannot be reversed.
   */
  public void destroy() {
    messager.unsubscribe(responseChannel, this);
    messager.unsubscribe(requestChannel, this);
    threadPool.shutdown();
  }

  public ListenableFuture<Boolean> sendPlayersToCluster(String clusterId, ServerSelectionMode mode,
      Set<UUID> players) throws IllegalArgumentException {
    if (clusterId == null || clusterId.equals("")) {
      throw new IllegalArgumentException("cluster id cannot be null or empty");
    }
    if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("must have at least one player");
    }

    ServerGroupRelocationAttempt attempt =
        new ServerGroupRelocationAttempt(clusterId, mode, players);
    return attempt.start();
  }

  public ListenableFuture<Boolean> sendPlayersToPlayer(UUID targetPlayerId, Set<UUID> players)
      throws IllegalArgumentException {
    if (targetPlayerId == null) {
      throw new IllegalArgumentException("player id cannot be null");
    }
    if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("must have at least one player");
    }

    PlayerRelocationAttempt attempt = new PlayerRelocationAttempt(targetPlayerId, null, players);
    return attempt.start();
  }

  public ListenableFuture<Boolean> sendPlayersToPlayer(String targetPlayerName, Set<UUID> players)
      throws IllegalArgumentException {
    if (targetPlayerName == null || targetPlayerName.equals("")) {
      throw new IllegalArgumentException("player name cannot be null or empty");
    }
    if (players == null || players.isEmpty()) {
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
   * For internal use. Has no use for external clients through the main API.
   * 
   * @param orderedServers A list of the servers to attempt to send the players to.
   * @param players The ids of the players to send.
   * @return The asynchronous, future result of this attempt to relocate players. Returns a value
   *         when the request finishes. <code>true</code> if the relocation is successful (does not
   *         guarantee the players make it to their destination, but does successfully find a server
   *         and try to send them there). <code>false</code> if the relocation fails, such as if the
   *         servers deny the requests.
   * @throws IllegalArgumentException on a <code>null</code> parameter or an empty collection.
   */
  public ListenableFuture<Boolean> sendPlayersToServers(List<ServerStatus> orderedServers,
      Set<UUID> players) {
    if (orderedServers == null || players == null) {
      throw new IllegalArgumentException("params cannot be null");
    } else if (orderedServers.isEmpty() || players.isEmpty()) {
      throw new IllegalArgumentException("collections cannot be empty");
    }

    ServerGroupRelocationAttempt attempt =
        new ServerGroupRelocationAttempt(orderedServers, players);
    return attempt.start();
  }

  @Override
  public void onMessage(byte[] channel, byte[] message) {
    if (Arrays.equals(channel, requestChannel)) {
      onRequestMessage(message);
    } else if (Arrays.equals(channel, responseChannel)) {
      onResponseMessage(message);
    }
  }

  private void onRequestMessage(byte[] message) {

    ReservationRequest rr = null;
    try {
      rr = ReservationRequest.fromBytes(message);

    } catch (Exception e) {
      System.out
          .println("Received a message on the reservation-request channel that could not be parsed.");
      e.printStackTrace();
      return;
    }

    TargetType type = rr.getTargetType();

    if (type == TargetType.SERVER_ID) { // targeted by server id
      ServerIdReservationRequest serverRequest = (ServerIdReservationRequest) rr;

      if (serverRequest.getTargetServer().equals(thisServerId)) { // for this server
        tryReservation(serverRequest);
      }

    } else if (type == TargetType.PLAYER_UUID) { // targeted by player id
      final PlayerUuidReservationRequest uidRequest = (PlayerUuidReservationRequest) rr;

      // syncs to main thread before accessing Minecraft server API
      ServerUtil.sync(new Runnable() {
        @Override
        public void run() {
          // if the player is online this server, this request is for this server
          if (ServerUtil.isPlayerOnline(uidRequest.getTargetPlayerUniqueId())) {
            tryReservation(uidRequest);
          }
        }
      });


    } else if (type == TargetType.PLAYER_NAME) { // targeted by player name
      final PlayerNameReservationRequest nameRequest = (PlayerNameReservationRequest) rr;

      // syncs to main thread before accessing Minecraft server API
      ServerUtil.sync(new Runnable() {
        @Override
        public void run() {
          // if the player is online this server, this request is for this server
          if (ServerUtil.isPlayerOnline(nameRequest.getTargetPlayerName())) {
            tryReservation(nameRequest);
          }
        }
      });

    }
    // if no conditions are met, the incoming request is not meant for this server.
  }

  private void tryReservation(ReservationRequest rr) {

    // TODO debug
    System.out.println("[ServerClusters] Received reservation request of id " + rr.getRequestId()
        + " from " + rr.getRequestingServer() + " for " + rr.getPlayers().size() + " players.");

    boolean reserved = slotManager.getReservation(rr.getPlayers());
    byte[] response =
        ReservationResponse.createMessage(rr.getRequestingServer(), thisServerId,
            rr.getRequestId(), reserved);

    // TODO debug
    System.out.println("[ServerClusters] Sending a reservation response message of id "
        + rr.getRequestId() + " to " + rr.getRequestingServer() + ". Approved: " + reserved);

    messager.publish(responseChannel, response);

  }

  private void onResponseMessage(byte[] message) {
    ReservationResponse rr = null;
    try {
      rr = ReservationResponse.fromBytes(message);

    } catch (Exception e) {
      System.out
          .println("Received a message on the reservation-response channel that could not be parsed.");
      e.printStackTrace();
      return;
    }

    if (!rr.getTargetServer().equals(thisServerId)) { // not for this server
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
      this.targetId = targetId;
      this.targetName = targetName;
      this.callback = SettableFuture.create();

      playerAttempts.put(id, this);
    }

    @Override
    public void run() {

      // TODO debug
      System.out.println("[ServerClusters] Sending a reservation request message of id " + id
          + " to the server of player (UUID:  " + targetId + ", name: " + targetName + ") for "
          + players.size() + " players.");

      if (targetId != null) {
        messager.publish(requestChannel,
            ReservationRequest.createMessageToPlayer(targetId, thisServerId, id, players));
      } else {
        messager.publish(requestChannel,
            ReservationRequest.createMessageToPlayer(targetName, thisServerId, id, players));
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
              .println("[ServerClusters] Interruption on a response timeout thread. Returning false...");
          e.printStackTrace();
          complete(false);
        }
        timePassed += WAIT_INTERVAL;
      }
    }

    private void onResponse(ReservationResponse response) {

      // TODO debug
      System.out.println("[ServerClusters] Received reservation response of id "
          + response.getRequestId() + " from " + response.getRespondingServer() + ". Approved: "
          + response.isApproved());

      if (response.isApproved()) {
        for (UUID playerId : players) {
          ServerUtil.sendPlayer(playerId, response.getRespondingServer());
        }
        complete(true);
      } else {
        complete(false);
      }
    }

    private void complete(boolean successful) {
      complete = true;
      clusterAttempts.remove(id);
      callback.set(successful);
    }

    private ListenableFuture<Boolean> start() {
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
          servers = network.getServers(clusterId, mode, players.size()).iterator();

          // TODO debug
          System.out.println("[ServerClusters] Upon requesting an instance of cluster " + clusterId
              + " for " + players.size() + " players in " + mode.name()
              + " mode, received these servers in return: {" + servers.toString() + "}");

        } // else just uses the predefined list of servers to try

        boolean foundNew = false;
        while (servers.hasNext()) {

          ServerStatus server = servers.next();

          // does not retry servers that already denied or failed to respond.
          if (server != null && !serversTried.contains(server.getId())) {
            currentServerId = server.getId();
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
        System.out.println("[ServerClusters] Sending a reservation request message of id " + id
            + " to " + currentServerId + " for " + players.size() + " players.");

        messager.publish(requestChannel,
            ReservationRequest.createMessageToServer(currentServerId, thisServerId, id, players));

        long timeWaited = 0;
        while (timeWaited < responseTimeout) {
          try {
            Thread.sleep(WAIT_INTERVAL);
          } catch (Exception e) {
            System.out
                .println("[ServerClusters] Interruption on a response timeout thread. Returning false...");
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
      System.out.println("[ServerClusters] Received reservation response of id "
          + response.getRequestId() + " from " + response.getRespondingServer() + ". Approved: "
          + response.isApproved());

      if (response.isApproved()) {
        for (UUID playerId : players) {
          ServerUtil.sendPlayer(playerId, response.getRespondingServer());
        }
        complete(true);
      }
      wakeUp = true;
    }

    private void complete(boolean successful) {
      complete = true;
      wakeUp = true;
      clusterAttempts.remove(id);
      callback.set(successful);
    }

    private ListenableFuture<Boolean> start() {
      threadPool.execute(this);
      return this.callback;
    }

  }

}
