package io.brutus.minecraft.serverclusters;

import io.brutus.minecraft.pubsub.PubSub;
import io.brutus.minecraft.serverclusters.bukkit.ServerUtil;
import io.brutus.minecraft.serverclusters.protocol.ReservationRequest;
import io.brutus.minecraft.serverclusters.protocol.ReservationResponse;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import io.brutus.networking.pubsubmessager.Subscriber;

import java.util.Arrays;
import java.util.HashSet;
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

  private final boolean actAsServer;
  private final String thisServerId;

  private final NetworkStatus network;
  private final SlotManager slotManager;

  private final PubSubMessager messager;
  private final byte[] requestChannel;
  private final byte[] responseChannel;
  private final long responseTimeout;

  private final ExecutorService threadPool;
  private final AtomicInteger requestCounter;
  private final Map<Integer, PlayerRelocationAttempt> attempts;

  public PlayerRelocator(ServerClustersConfig config, NetworkStatus network, SlotManager slotManager)
      throws IllegalArgumentException {

    if (config == null || network == null) {
      throw new IllegalArgumentException("params cannot be null");
    }

    this.actAsServer = config.actAsServer();
    this.responseTimeout = config.getResponseTimeout();
    this.thisServerId = config.getGameServerId();

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

    if (actAsServer) { // does things not necessary unless fulfilling other instances' requests
      if (slotManager == null) {
        throw new IllegalArgumentException(
            "slot manager cannot be null when server functions are enabled");
      }
      messager.subscribe(requestChannel, this);
    }

    threadPool = Executors.newCachedThreadPool();
    requestCounter = new AtomicInteger();
    attempts = new ConcurrentHashMap<Integer, PlayerRelocationAttempt>();
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
   * Players will be relocated asynchronously (not immediately). It will take some time before an
   * instance is found and approved and the players are relocated to it. During that time, the
   * players will still be able to run around, play, and interact with this gameserver they are
   * currently on.
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
   * @param mode The mode with which to order the available servers by quality.
   * @param players The player or players to send to an instance of the server cluster.
   * @return The asynchronous, future result of this attempt to relocate players. Returns a value
   *         when the request finishes. <code>true</code> if the relocation is successful (does not
   *         guarantee the players make it to their destination, but does successfully find a server
   *         and send them there).<code>false</code> if the relocation fails, such as if no valid
   *         servers exist for the given cluster.
   * @throws IllegalArgumentException on a <code>null</code> parameter, an empty cluster id, an
   *         empty set, or a cluster id that is not configured.
   */
  public ListenableFuture<Boolean> sendPlayers(String clusterId, ServerSelectionMode mode,
      Set<UUID> players) throws IllegalArgumentException {
    if (clusterId == null || clusterId.equals("")) {
      throw new IllegalArgumentException("cluster id cannot be null or empty");
    }
    if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("must have at least one player");
    }

    PlayerRelocationAttempt attempt = new PlayerRelocationAttempt(clusterId, mode, players);
    attempts.put(attempt.getId(), attempt);
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
    if (!actAsServer) {
      return;
    }

    ReservationRequest rr = null;
    try {
      rr = ReservationRequest.fromBytes(message);

    } catch (Exception e) {
      System.out
          .println("Received a message on the reservation-request channel that could not be parsed.");
      e.printStackTrace();
      return;
    }

    if (!rr.getTargetServer().equals(thisServerId)) { // not for this server
      return;
    }

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

    PlayerRelocationAttempt attempt = attempts.get(rr.getRequestId());
    if (attempt == null) {
      return;
    }

    attempt.onResponse(rr);
  }

  /**
   * Private helper runnable class that attempts to get a reservation on a destination instances for
   * a player or group of players.
   */
  private class PlayerRelocationAttempt implements Runnable {

    private static final int MAX_TRIES = 20; // for sanity: does not try forever.
    private static final long WAIT_INTERVAL = 50;

    private final int id;
    private final SettableFuture<Boolean> callback;
    private final Set<UUID> players;
    private final String clusterId;
    private final ServerSelectionMode mode;

    private final Set<String> serversTried;
    private String currentServer;
    private List<String> servers;

    private volatile boolean wakeUp;
    private volatile boolean complete;

    private PlayerRelocationAttempt(String clusterId, ServerSelectionMode mode, Set<UUID> players) {
      this.id = requestCounter.getAndIncrement();
      this.clusterId = clusterId;
      this.mode = mode;
      this.players = players;
      this.callback = SettableFuture.create();

      this.serversTried = new HashSet<String>();
    }

    @Override
    public void run() {
      int tries = 0;
      // continuously retries connecting the player(s) to servers in the cluster until it finds
      // definitive failure
      while (!complete && tries++ <= MAX_TRIES) {

        // gets a list of servers to try
        servers = network.getServers(clusterId, mode, players.size());

        // TODO debug
        System.out.println("[ServerClusters] Upon requesting an instance of cluster " + clusterId
            + " for " + players.size() + " players in " + mode.name()
            + " mode, received these servers in return: {" + servers.toString() + "}");

        if (servers.isEmpty()) {
          complete(false);
          return;
        }

        boolean foundNew = false;
        for (String server : servers) { // does not retry
          if (!serversTried.contains(server)) {
            currentServer = server;
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
            + " to " + currentServer + " for " + players.size() + " players.");

        messager.publish(requestChannel,
            ReservationRequest.createMessage(currentServer, thisServerId, id, players));

        long timeWaited = 0;
        while (timeWaited < responseTimeout) {
          try {
            Thread.sleep(WAIT_INTERVAL);
          } catch (Exception e) {
            System.out.println("[ServerClusters] The response timeout thread was interrupted.");
          }
          timeWaited += WAIT_INTERVAL;
          if (wakeUp) {
            wakeUp = false;
            break;
          }
        }
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
      attempts.remove(this);
      callback.set(successful);
    }

    private ListenableFuture<Boolean> start() {
      threadPool.execute(this);
      return this.callback;
    }

    private int getId() {
      return id;
    }

  }

}
