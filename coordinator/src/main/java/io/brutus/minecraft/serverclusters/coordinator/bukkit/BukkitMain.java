package io.brutus.minecraft.serverclusters.coordinator.bukkit;

import io.brutus.minecraft.pubsub.PubSub;
import io.brutus.minecraft.serverclusters.config.LocalConfiguration;
import io.brutus.minecraft.serverclusters.config.SharedConfiguration;
import io.brutus.minecraft.serverclusters.config.SharedConfigurationLoader;
import io.brutus.minecraft.serverclusters.config.SharedConfigurationProvider;
import io.brutus.minecraft.serverclusters.networkstatus.HeartbeatSubscription;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkCache;
import io.brutus.minecraft.serverclusters.networkstatus.NetworkStatus;
import io.brutus.minecraft.serverclusters.notifications.AdminNotifier;
import io.brutus.minecraft.serverclusters.notifications.GameserverCrashNotifier;
import io.brutus.minecraft.serverclusters.uid.IdProvider;
import io.brutus.networking.pubsubmessager.PubSubMessager;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit plugin main for ServerClusters coordinator node.
 */
public class BukkitMain extends JavaPlugin {

  private LocalConfiguration localConfig;
  private SharedConfigurationLoader sharedConfig;

  private PubSubMessager messager;

  private SharedConfigurationProvider configProvider;
  private IdProvider idProvider;
  private AdminNotifier adminNotifier;
  private GameserverCrashNotifier crashNotifier;

  private HeartbeatSubscription heartbeats;
  private NetworkStatus networkStatus;

  @Override
  public void onEnable() {

    this.localConfig = new BukkitLocalConfiguration(this);
    this.sharedConfig = new BukkitSharedConfiguration(this);
    SharedConfiguration sConfig = sharedConfig.getConfigurationMessage();

    messager = PubSub.getSingleton().getMessager(localConfig.getMessagerInstanceName());

    this.configProvider = new SharedConfigurationProvider(messager, localConfig, sharedConfig);
    this.configProvider.publishConfiguration();
    this.idProvider =
        new IdProvider(messager, sConfig.getIdRequestChannel(), sConfig.getIdResponseChannel(),
            new BukkitCounterConfiguration(this));
    this.adminNotifier =
        new AdminNotifier(getDataFolder(), new BukkitCrashNotifierConfiguration(this));
    crashNotifier = new GameserverCrashNotifier(adminNotifier);

    heartbeats =
        new HeartbeatSubscription(messager, sConfig.getHeartbeatChannel(),
            sConfig.getShutdownChannel());
    this.networkStatus = new NetworkCache(sConfig.getServerTimeout(), null);
    heartbeats.registerListener((NetworkCache) networkStatus);

    // lets the crash notifier listen to the network-status cache for servers becoming unresponsive
    networkStatus.registerListener(crashNotifier);

    getCommand("networkstatus").setExecutor(this);
    getCommand("serverclustersconfig").setExecutor(this);

    getLogger().info("has been enabled.");
  }

  @Override
  public void onDisable() {
    if (configProvider != null) {
      configProvider.destroy();
    }
    if (idProvider != null) {
      idProvider.destroy();
    }
    if (heartbeats != null) {
      heartbeats.destroy();
    }
    getLogger().info("has been disabled.");
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
    if (command.getName().equalsIgnoreCase("networkstatus")) {
      if (networkStatus == null) {
        sender.sendMessage(ChatColor.RED + "Sorry, no network status data is available right now.");
      } else {
        for (String str : networkStatus.toStringList()) {
          sender.sendMessage(ChatColor.GREEN + str);
        }
      }
    } else if (command.getName().equalsIgnoreCase("serverclustersconfig")) {
      if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
        return false;
      }

      sharedConfig.reload();
      configProvider.publishConfiguration();
      sender.sendMessage(ChatColor.GREEN + "Config reloaded.");
    }
    return true;
  }

}
