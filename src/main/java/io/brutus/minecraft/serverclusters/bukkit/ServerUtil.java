package io.brutus.minecraft.serverclusters.bukkit;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.Bukkit;
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
