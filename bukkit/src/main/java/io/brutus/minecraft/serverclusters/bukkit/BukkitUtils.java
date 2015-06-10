package io.brutus.minecraft.serverclusters.bukkit;

import io.brutus.minecraft.serverclusters.gameserver.ServerUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit-specific server utilities.
 */
public class BukkitUtils implements ServerUtils {

  private JavaPlugin plugin;

  public BukkitUtils(JavaPlugin plugin) {
    this.plugin = plugin;
    plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
  }

  @Override
  public String getServerIp() {
    return plugin.getServer().getIp();
  }

  @Override
  public int getServerPort() {
    return plugin.getServer().getPort();
  }

  @Override
  public Logger getLogger() {
    return plugin.getLogger();
  }

  @Override
  public void sendPlayer(final UUID playerId, final String destinationServer)
      throws IllegalStateException, IllegalArgumentException {
    if (playerId == null || destinationServer == null) {
      throw new IllegalArgumentException("params cannot be null");
    }
    if (destinationServer.equals("")) {
      throw new IllegalArgumentException("server name cannot be empty");
    }

    sync(new Runnable() {

      @Override
      public void run() {
        Player player = plugin.getServer().getPlayer(playerId);

        if (player == null || !player.isOnline()) {
          return;
        }

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);

        try {
          out.writeUTF("Connect");
          out.writeUTF(destinationServer);
        } catch (IOException e) {
          System.out
              .println("There was an issue writing a message to send a player to a connected server");
          e.printStackTrace();
        }

        player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
      }

    });
  }

  @Override
  public boolean isPlayerOnline(UUID id) {
    Player p = Bukkit.getPlayer(id);
    if (p == null || !p.isOnline()) {
      return false;
    }
    return true;
  }

  @Override
  public boolean isPlayerOnline(String name) {
    Player p = Bukkit.getPlayerExact(name);
    if (p == null || !p.isOnline()) {
      return false;
    }
    return true;
  }

  @Override
  public Set<UUID> getOnlinePlayerIds() {
    Set<UUID> ret = new HashSet<UUID>();

    for (Player player : plugin.getServer().getOnlinePlayers()) {
      ret.add(player.getUniqueId());
    }

    return ret;
  }

  @Override
  public void sendMessage(final String message, Collection<UUID> targets) {
    if (message == null || message.isEmpty() || targets == null || targets.isEmpty()) {
      return;
    }

    final Set<UUID> players = new HashSet<UUID>(targets);
    sync(new Runnable() {
      @Override
      public void run() {
        for (UUID uid : players) {
          Player player = plugin.getServer().getPlayer(uid);
          if (player != null) {
            player.sendMessage(ChatColor.RED + message);
          }
        }
      }
    });
  }

  @Override
  public void sync(Runnable task) throws IllegalStateException, IllegalArgumentException {
    if (task == null) {
      throw new IllegalArgumentException("cannot synchronize a null task");
    }
    // does not try to schedule if the plugin is disabled, which would throw an exception
    if (plugin.isEnabled()) {
      Bukkit.getScheduler().runTask(plugin, task);
    } else {
      task.run();
    }
  }


}
