package io.brutus.minecraft.serverclusters.bukkit;

import io.brutus.minecraft.serverclusters.ServerClusters;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main Bukkit plugin class for ServerClusters.
 */
public class PluginMain extends JavaPlugin {

  private ServerClusters sc;

  @Override
  public void onEnable() {
    sc = ServerClusters.getSingleton(); // initializes the singleton

    getLogger().info("has enabled.");
  }

  @Override
  public void onDisable() {
    sc.stopHeartbeat();

    getLogger().info("has disabled.");
  }

}
