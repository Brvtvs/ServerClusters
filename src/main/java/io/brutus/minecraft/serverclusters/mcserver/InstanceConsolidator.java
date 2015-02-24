package io.brutus.minecraft.serverclusters.mcserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.brutus.minecraft.serverclusters.bukkit.ServerUtil;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkStatus;
import io.brutus.minecraft.serverclusters.networkstatus.ServerStatus;
import io.brutus.minecraft.serverclusters.selection.ServerSelectionMode;

/**
 * If enabled, periodically attempts send this server's players to a more ideal instance of the same
 * cluster, if one is found.
 */
public class InstanceConsolidator {

  // how often to attempt consolidations, in milliseconds
  private static final long PERIOD_MILLIS = 20000;

  private final ServerClustersConfig config;
  private final NetworkStatus network;
  private final SlotManager slots;
  private final PlayerRelocator relocator;

  private volatile boolean alive;

  public InstanceConsolidator(ServerClustersConfig config, NetworkStatus network,
      SlotManager slots, PlayerRelocator relocator) throws IllegalArgumentException {
    if (config == null || network == null || slots == null || relocator == null) {
      throw new IllegalArgumentException("params cannot be null");
    }
    if (!config.attemptInstanceConsolidations()) {
      throw new IllegalArgumentException("instance consolidation is not enabled!");
    }

    this.config = config;
    this.network = network;
    this.slots = slots;
    this.relocator = relocator;

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
              ServerUtil.sync(new Runnable() {
                @Override
                public void run() {
                  Set<UUID> allPlayers = ServerUtil.getOnlinePlayerIds();
                  ServerUtil.sendMessage(
                      "Attempting to send you to a more full lobby, one moment...", allPlayers);
                  relocator.sendPlayersToServers(finalList, allPlayers);
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
