package io.brutus.minecraft.serverclusters.gameserver;

import java.util.Arrays;

import io.brutus.minecraft.serverclusters.config.SharedConfiguration;
import io.brutus.minecraft.serverclusters.protocol.PlayerNameReservationRequest;
import io.brutus.minecraft.serverclusters.protocol.PlayerUuidReservationRequest;
import io.brutus.minecraft.serverclusters.protocol.ReservationRequest;
import io.brutus.minecraft.serverclusters.protocol.ReservationResponse;
import io.brutus.minecraft.serverclusters.protocol.ServerIdReservationRequest;
import io.brutus.minecraft.serverclusters.protocol.ReservationRequest.TargetType;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import io.brutus.networking.pubsubmessager.Subscriber;

/**
 * Serves requests for players to be relocated to this gameserver.
 * <p>
 * If a request is received and an open slot exists on this server, it will be reserved for the
 * requesting player for a limited period of time. Otherwise, the request will be denied.
 */
public class PlayerRelocationServer {

  private final String thisNodeId;

  private final SlotManager slotManager;

  private final PubSubMessager messager;
  private final byte[] requestChannel;
  private final byte[] responseChannel;
  private final RequestSubscriber sub;

  /**
   * Class constructor.
   * 
   * @param thisNodeId The unique id of the network node that this relocation-server is running on.
   *        Essential to uniquely identify and route messages.
   * @param messager The messager to listen to requests on and send responses on.
   * @param slotManager The slot manager for this gameserver.
   * @param config The network configuration.
   * @throws IllegalArgumentException On a <code>null</code> or empty parameter.
   */
  public PlayerRelocationServer(String thisNodeId, PubSubMessager messager,
      SlotManager slotManager, SharedConfiguration config) throws IllegalArgumentException {
    if (thisNodeId == null || thisNodeId.isEmpty()) {
      throw new IllegalArgumentException("node id cannot be null or empty");
    } else if (messager == null) {
      throw new IllegalArgumentException("pub/sub messager cannot be null");
    } else if (slotManager == null) {
      throw new IllegalArgumentException("slot manager cannot be null");
    } else if (config == null) {
      throw new IllegalArgumentException("config cannot be null");
    }

    this.thisNodeId = thisNodeId;
    this.slotManager = slotManager;

    this.messager = messager;

    this.requestChannel = config.getReservationRequestChannel();
    this.responseChannel = config.getReservationResponseChannel();
    if (requestChannel == null || requestChannel.length < 1 || responseChannel == null
        || responseChannel.length < 1) {
      throw new IllegalArgumentException("reservation channels cannot be null or empty");
    }

    sub = new RequestSubscriber();
    messager.subscribe(requestChannel, sub);
  }

  /**
   * Stops this from serving any more relocations and kills its connections. Cannot be reversed.
   */
  public void destroy() {
    messager.unsubscribe(requestChannel, sub);
  }

  private void onRequestMessage(byte[] message) {

    ReservationRequest rr = null;
    try {
      rr = ReservationRequest.fromBytes(message);

    } catch (Exception e) {
      System.out.println("[ServerClusters " + getClass().getSimpleName()
          + "] Received a message on the reservation-request channel that could not be parsed.");
      e.printStackTrace();
      return;
    }

    TargetType type = rr.getTargetType();

    final ServerUtils serverUtils = ServerClusters.getSingleton().getServerUtils();

    if (type == TargetType.SERVER_ID) { // targeted by server id
      ServerIdReservationRequest serverRequest = (ServerIdReservationRequest) rr;

      if (serverRequest.getTargetServer().equals(thisNodeId)) { // for this server
        tryReservation(serverRequest);
      }

    } else if (type == TargetType.PLAYER_UUID) { // targeted by player id
      final PlayerUuidReservationRequest uidRequest = (PlayerUuidReservationRequest) rr;

      // syncs to main thread before accessing Minecraft server API
      serverUtils.sync(new Runnable() {
        @Override
        public void run() {
          // if the player is online this server, this request is for this server
          if (serverUtils.isPlayerOnline(uidRequest.getTargetPlayerUniqueId())) {
            tryReservation(uidRequest);
          }
        }
      });


    } else if (type == TargetType.PLAYER_NAME) { // targeted by player name
      final PlayerNameReservationRequest nameRequest = (PlayerNameReservationRequest) rr;

      // syncs to main thread before accessing Minecraft server API
      serverUtils.sync(new Runnable() {
        @Override
        public void run() {
          // if the player is online this server, this request is for this server
          if (serverUtils.isPlayerOnline(nameRequest.getTargetPlayerName())) {
            tryReservation(nameRequest);
          }
        }
      });

    }
    // if no conditions are met, the incoming request is not meant for this server.
  }

  private void tryReservation(ReservationRequest rr) {

    // TODO debug
    System.out.println("[ServerClusters " + getClass().getSimpleName()
        + "] Received reservation request of id " + rr.getRequestId() + " from "
        + rr.getRequestingServer() + " for " + rr.getPlayers().size() + " players.");

    boolean reserved = slotManager.getReservation(rr.getPlayers());
    byte[] response =
        ReservationResponse.createMessage(rr.getRequestingServer(), thisNodeId, rr.getRequestId(),
            reserved);

    // TODO debug
    System.out.println("[ServerClusters " + getClass().getSimpleName()
        + "] Sending a reservation response message of id " + rr.getRequestId() + " to "
        + rr.getRequestingServer() + ". Approved: " + reserved);

    messager.publish(responseChannel, response);

  }

  /**
   * Listens for incoming requests from the pub/sub messager.
   */
  private class RequestSubscriber implements Subscriber {

    @Override
    public void onMessage(byte[] channel, byte[] message) {
      if (Arrays.equals(channel, requestChannel)) {
        onRequestMessage(message);
      }
    }

  }
}
