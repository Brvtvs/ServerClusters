package io.brutus.minecraft.serverclusters.bukkit;

import io.brutus.minecraft.serverclusters.gameserver.ServerClusters;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main Bukkit plugin class for ServerClusters.
 */
public class BukkitMain extends JavaPlugin {

  private BukkitConfiguration config;

  @Override
  public void onEnable() {

    config = new BukkitConfiguration(this);
    BukkitUtils serverUtils = new BukkitUtils(this);

    // initializes slot manager that will track this server's open slots and handle other server's
    // reservation requests
    BukkitSlotManager bukkitSlots =
        new BukkitSlotManager(this, config.getTotalSlots(),
            config.getReservationFulfillmentTimeout(), config.strictReservations());
    getServer().getPluginManager().registerEvents(bukkitSlots, this);

    getCommand("networkstatus").setExecutor(new NetworkStatusCommand());

    ServerClusters.onEnable(config, bukkitSlots, serverUtils);

    getLogger().info("has been enabled.");
  }

  @Override
  public void onDisable() {
    ServerClusters.onDisable();

    config.destroy();

    getLogger().info("has been disabled.");
  }

}
