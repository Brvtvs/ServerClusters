package io.brutus.minecraft.serverclusters.bukkit;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.jodah.expiringmap.ExpiringMap;
import net.jodah.expiringmap.ExpiringMap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap.ExpirationPolicy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import io.brutus.minecraft.serverclusters.gameserver.SlotManager;

/**
 * A slot manager that uses Bukkit events to handle player logins, joins, and quits.
 */
public class BukkitSlotManager implements SlotManager, Listener, ExpirationListener<UUID, Long> {

  private final BukkitMain plugin;

  private volatile int totalSlots;
  private final AtomicInteger onlinePlayers;

  private final boolean strictReservations;
  private final Map<UUID, Long> reservations;

  private SettableFuture<Boolean> future;

  public BukkitSlotManager(BukkitMain plugin, int totalSlots, long reservationTimeout,
      boolean strictReservations) throws IllegalArgumentException {
    if (plugin == null) {
      throw new IllegalArgumentException("plugin cannot be null");
    }
    if (totalSlots < 0) {
      throw new IllegalArgumentException("total slots cannot be negative");
    }
    if (reservationTimeout < 1) {
      throw new IllegalArgumentException("reservation timeout must be positive");
    }

    this.plugin = plugin;
    this.strictReservations = strictReservations;
    this.totalSlots = totalSlots;
    this.onlinePlayers = new AtomicInteger(plugin.getServer().getOnlinePlayers().size());

    this.reservations =
        ExpiringMap.builder().expirationPolicy(ExpirationPolicy.CREATED)
            .expiration(reservationTimeout, TimeUnit.MILLISECONDS).expirationListener(this).build();
  }

  @Override
  public ListenableFuture<Boolean> setTotalSlots(int totalSlots) throws IllegalArgumentException {
    if (totalSlots < 0) {
      throw new IllegalArgumentException("total slots cannot be negative");
    }

    if (future != null) { // informs client that previous attempt was overwritten
      future.set(false);
      future = null;
    }

    this.totalSlots = totalSlots;

    SettableFuture<Boolean> ret = SettableFuture.create();

    // needs to wait for reservations to resolve before finishing.
    if (totalSlots < onlinePlayers.get() + reservations.size() && reservations.size() > 0) {
      future = ret;
    } else { // else no need to wait; finishes immediately.
      ret.set(true);
    }
    return ret;
  }

  @Override
  public int getTotalSlots() {
    return totalSlots;
  }

  @Override
  public int getOpenSlots() {
    int ret = totalSlots - onlinePlayers.get() - reservations.size();
    if (ret < 0) {
      return 0;
    }
    return ret;
  }

  @Override
  public boolean getReservation(Set<UUID> players) throws IllegalArgumentException {
    if (players == null || players.isEmpty()) {
      throw new IllegalArgumentException("must pass in at least 1 player");
    }

    if (getOpenSlots() < players.size()) {
      return false;
    }

    long now = System.currentTimeMillis();
    for (UUID pid : players) {
      reservations.put(pid, now);
    }

    return true;
  }

  @Override
  public void expired(UUID player, Long reservationMade) {
    checkFuture();
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
    Long reservationMade = reservations.remove(event.getUniqueId());

    if (event.getLoginResult() != Result.ALLOWED) {
      return;
    }

    if (reservationMade == null) {
      if (strictReservations) {
        // no reservation, gtfo
        event
            .disallow(Result.KICK_OTHER,
                "You are not currently allowed to go there! Please contact a staff member and report this bug.");

      } else if (getOpenSlots() < 1) {
        // okay, so you don't have a reservation. We would seat you but it is just too busy tonight
        event.disallow(Result.KICK_FULL,
            "Sorry, there is no room there right now. Please try again.");
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(PlayerJoinEvent event) {
    plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
      @Override
      public void run() {
        onlinePlayers.set(plugin.getServer().getOnlinePlayers().size());
        checkFuture();
      }
    }, 1L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
      @Override
      public void run() {
        onlinePlayers.set(plugin.getServer().getOnlinePlayers().size());
        checkFuture();
      }
    }, 1L);
  }

  /**
   * Checks whether the current state of this server's slots satisfy a client's unfinished attempt
   * to change the number of slots.
   */
  private void checkFuture() {
    if (future != null) {
      if (reservations.isEmpty() || totalSlots >= onlinePlayers.get() + reservations.size()) {
        future.set(true);
        future = null;
      }
    }
  }

}
