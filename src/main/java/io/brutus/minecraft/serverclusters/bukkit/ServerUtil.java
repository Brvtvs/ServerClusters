package io.brutus.minecraft.serverclusters.bukkit;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Static methods to abstract some common, and most likely interchangeable, references to a
 * Minecraft server implementation.
 * <p>
 * These are very likely to change if implementations change and are not at all inherent to a
 * specific implementation. Keeping them separate simply enhances code reusability and adaptability.
 */
public class ServerUtil {

  private static JavaPlugin plugin;

  /**
   * Gets the IP that this server is bound to.
   * 
   * @return This server's IP.
   */
  public static String getServerIp() {
    if (plugin == null) {
      getPlugin();
    }
    return plugin.getServer().getIp();
  }

  /**
   * Gets the port that this server is running on.
   * 
   * @return This server's port.
   */
  public static int getServerPort() {
    if (plugin == null) {
      getPlugin();
    }
    return plugin.getServer().getPort();
  }

  /**
   * Sends a player to a connected server.
   * 
   * @param playerId The player to send.
   * @param destinationServer The id of the server to send the player to, as it is known to the
   *        network proxy.
   * @throws IllegalStateException on being unable to get a plugin instance to send the required
   *         message on.
   * @throws IllegalArgumentException on a <code>null</code> parameter or an empty server name.
   */
  public static void sendPlayer(final UUID playerId, final String destinationServer)
      throws IllegalStateException, IllegalArgumentException {
    if (playerId == null || destinationServer == null) {
      throw new IllegalArgumentException("params cannot be null");
    }
    if (destinationServer.equals("")) {
      throw new IllegalArgumentException("server name cannot be empty");
    }

    if (plugin == null) {
      getPlugin();
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

  /**
   * Gets whether there is a player online with the given unique id.
   * 
   * @param id The id to search with.
   * @return <code>true</code> if the player is currently online.
   */
  public static boolean isPlayerOnline(UUID id) {
    Player p = Bukkit.getPlayer(id);
    if (p == null || !p.isOnline()) {
      return false;
    }
    return true;
  }

  /**
   * Gets whether there is a player online with the exact given name (not case sensitive).
   * 
   * @param name The name to search with.
   * @return <code>true</code> if the player is currently online.
   */
  public static boolean isPlayerOnline(String name) {
    Player p = Bukkit.getPlayerExact(name);
    if (p == null || !p.isOnline()) {
      return false;
    }
    return true;
  }

  /**
   * Gets a set of the unique ids of all the players on the server.
   * <p>
   * This method is NOT thread safe. If you need to run this from a thread other than the server's
   * main thread, you need to sync it first, such as with {@link #sync(Runnable)}.
   * 
   * @return The unique ids of all online players.
   */
  public static Set<UUID> getOnlinePlayerIds() {
    Set<UUID> ret = new HashSet<UUID>();

    if (plugin == null) {
      getPlugin();
    }

    for (Player player : plugin.getServer().getOnlinePlayers()) {
      ret.add(player.getUniqueId());
    }

    return ret;
  }

  /**
   * Sends a message to one or a group of players.
   * <p>
   * This method is thread safe and synchronizes to the main thread before the message is sent.
   * 
   * @param message The message to send.
   * @param targets The ids of players to send the messages to.
   */
  public static void sendMessage(final String message, Collection<UUID> targets) {
    if (message == null || message.isEmpty() || targets == null || targets.isEmpty()) {
      return;
    }
    if (plugin == null) {
      getPlugin();
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

  /**
   * Synchronizes a task to this server's main thread.
   * 
   * @param task The task to run on the main thread.
   * @throws IllegalStateException on being unable to get a plugin instance to synchronize with.
   * @throws IllegalArgumentException on a <code>null</code> task.
   */
  public static void sync(Runnable task) throws IllegalStateException, IllegalArgumentException {
    if (task == null) {
      throw new IllegalArgumentException("cannot synchronize a null task");
    }
    if (plugin == null) {
      getPlugin();
    }
    Bukkit.getScheduler().runTask(plugin, task);
  }

  private static void getPlugin() throws IllegalStateException {
    JavaPlugin jPlugin =
        (JavaPlugin) Bukkit.getServer().getPluginManager().getPlugin("ServerClusters");
    if (jPlugin == null) {
      throw new IllegalStateException("plugin to send messages on could not be found");
    }
    jPlugin.getServer().getMessenger().registerOutgoingPluginChannel(jPlugin, "BungeeCord");
    plugin = jPlugin;
  }

}
