package io.brutus.minecraft.serverclusters.gameserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.brutus.minecraft.serverclusters.networkstatus.NetworkStatus;
import io.brutus.minecraft.serverclusters.networkstatus.ServerStatus;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;
import io.brutus.minecraft.serverclusters.sendplayer.PlayerRelocationClient;

/**
 * If enabled, periodically attempts send this server's players to a more ideal instance of the same
 * cluster, if one is found.
 */
public class InstanceConsolidator {

  // how often to attempt consolidations, in milliseconds
  private static final long PERIOD_MILLIS = 20000;

  private final ServerClustersConfiguration config;
  private final NetworkStatus network;
  private final SlotManager slots;
  private final PlayerRelocationClient relocationClient;

  private volatile boolean alive;

  /**
   * Class constructor.
   * 
   * @param config The ServerClusters configuration.
   * @param network The network's status.
   * @param slots The slot manager for this server.
   * @param relocationClient The client with which to relocate players to a more full instance.
   * @throws IllegalArgumentException On a <code>null</code> parameter.
   */
  public InstanceConsolidator(ServerClustersConfiguration config, NetworkStatus network,
      SlotManager slots, PlayerRelocationClient relocationClient) throws IllegalArgumentException {
    if (config == null || network == null || slots == null || relocationClient == null) {
      throw new IllegalArgumentException("params cannot be null");
    }
    if (!config.attemptInstanceConsolidations()) {
      throw new IllegalArgumentException("instance consolidation is not enabled!");
    }

    this.config = config;
    this.network = network;
    this.slots = slots;
    this.relocationClient = relocationClient;

    start();
  }

  /**
   * Kills this consolidator and relinquishes its resources.
   */
  public void destroy() {
    alive = false;
  }

  private void start() {

    alive = true;

    new Thread() {

      @Override
      public void run() {
        while (alive) {

          int openSlots = slots.getOpenSlots();
          int numPlayers = slots.getTotalSlots() - openSlots;

          // this server is not full and not empty, tries to consolidate
          if (numPlayers > 0 && openSlots > 0) {

            List<ServerStatus> instances =
                network.getServers(config.getClusterId(), ServerSelectionMode.MATCHMAKING,
                    numPlayers);

            List<ServerStatus> moreIdeal = null;

            for (ServerStatus status : instances) {
              // looks for an instance that is more ideal than this one for matchmaking, but still
              // has enough room for all of this instance's players.
              if (status.getOpenSlots() < openSlots && status.getOpenSlots() >= numPlayers) {

                // this likely runs many times fruitlessly. Avoids making new collection unless
                // actually necessary.
                if (moreIdeal == null) {
                  moreIdeal = new ArrayList<ServerStatus>();
                }

                moreIdeal.add(status);
              }
            }

            // at least one server that meets these criteria exists. Attempts to relocate
            // players to one of the more ideal servers.
            if (moreIdeal != null) {
              final List<ServerStatus> finalList = moreIdeal;
              final ServerUtils serverUtils = ServerClusters.getSingleton().getServerUtils();
              serverUtils.sync(new Runnable() {
                @Override
                public void run() {
                  Set<UUID> allPlayers = serverUtils.getOnlinePlayerIds();
                  serverUtils.sendMessage(
                      "Attempting to send you to a more full lobby, one moment...", allPlayers);
                  relocationClient.sendPlayersToServers(finalList, allPlayers);
                }
              });
            }
          }

          try {
            Thread.sleep(PERIOD_MILLIS);
          } catch (InterruptedException e) {
            System.out.println("[ServerClusters] The heartbeat thread was interrupted.");
            e.printStackTrace();
            alive = false;
          }

        }
      }
    }.start();

  }
}
