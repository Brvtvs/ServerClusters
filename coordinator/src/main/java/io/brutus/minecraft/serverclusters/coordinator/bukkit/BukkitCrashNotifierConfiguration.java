package io.brutus.minecraft.serverclusters.coordinator.bukkit;

import java.util.ArrayList;
import java.util.List;

import io.brutus.minecraft.serverclusters.notifications.NotifierConfiguration;
import io.brutus.minecraft.simpleconfig.Configuration;
import io.brutus.minecraft.simpleconfig.YamlConfigAccessor;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit configuration for crash notifications.
 */
public class BukkitCrashNotifierConfiguration extends Configuration implements
    NotifierConfiguration {

  private static final String SUBDIRECTORY = "";
  private static final String FILE_NAME = "crash-notifications.yml";

  private boolean loggingEnabled;
  private boolean emailEnabled;
  private List<String> adminEmails;

  public BukkitCrashNotifierConfiguration(JavaPlugin plugin) {
    super(new YamlConfigAccessor(plugin, FILE_NAME, SUBDIRECTORY));
    load();
  }

  public void reload() {
    refresh();
    load();
  }

  private void load() {
    FileConfiguration config = getConfig();

    loggingEnabled = config.getBoolean("logging.enabled");

    ConfigurationSection emailSec = config.getConfigurationSection("email-notifications");
    emailEnabled = emailSec.getBoolean("enabled");
    adminEmails = emailSec.getStringList("notify");
    if (adminEmails == null) {
      adminEmails = new ArrayList<String>(0);
    }
  }

  @Override
  public boolean isLoggingEnabled() {
    return loggingEnabled;
  }

  @Override
  public boolean isEmailNotificationEnabled() {
    return emailEnabled && !adminEmails.isEmpty();
  }

  @Override
  public List<String> getAdminEmailAddresses() {
    return new ArrayList<String>(adminEmails);
  }

}
