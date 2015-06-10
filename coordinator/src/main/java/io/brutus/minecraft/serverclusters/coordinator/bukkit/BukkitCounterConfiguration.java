package io.brutus.minecraft.serverclusters.coordinator.bukkit;

import io.brutus.minecraft.serverclusters.uid.IdCounterConfiguration;
import io.brutus.minecraft.simpleconfig.Configuration;
import io.brutus.minecraft.simpleconfig.YamlConfigAccessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Bukkit config for unique server id counters and id formats.
 */
public class BukkitCounterConfiguration extends Configuration implements IdCounterConfiguration {

  private static final String SUBDIRECTORY = "";
  private static final String FILE_NAME = "id-allocation.yml";
  private static final String COUNTERS_SECTION = "counters";

  private JavaPlugin plugin;

  private String format;
  private Map<String, AtomicLong> counters;

  public BukkitCounterConfiguration(JavaPlugin plugin) {
    super(new YamlConfigAccessor(plugin, FILE_NAME, SUBDIRECTORY));
    this.plugin = plugin;

    load();
  }

  @Override
  public String getIdFormat() {
    return format;
  }

  @Override
  public ListenableFuture<Long> getCounter(final String clusterId) {
    final SettableFuture<Long> callback = SettableFuture.create();

    plugin.getServer().getScheduler().runTask(plugin, new Runnable() {

      @Override
      public void run() {
        AtomicLong counter = counters.get(clusterId);
        long ret;
        if (counter == null) {
          counter = new AtomicLong(1);
          counters.put(clusterId, counter);
          ret = 1;
        } else {
          ret = counter.incrementAndGet();
        }

        // first saves the new counter position to disk and does so from the main thread. running on
        // the main thread should also ensure that the saves to disk are in the correct order.
        getConfig().set(COUNTERS_SECTION + "." + clusterId, ret);
        getAccessor().saveConfig();

        callback.set(ret);
      }
    });
    return callback;
  }

  /**
   * Reloads the configuration from disk.
   */
  public void reload() {
    refresh();
    load();
  }

  private void load() {
    FileConfiguration config = getConfig();

    format = config.getString("id-format");

    counters = new ConcurrentHashMap<String, AtomicLong>();
    ConfigurationSection countSec = config.getConfigurationSection(COUNTERS_SECTION);
    if (countSec != null) {
      for (String cluster : countSec.getKeys(false)) {
        Long counter = countSec.getLong(cluster);
        if (counter != null) {
          counters.put(cluster, new AtomicLong(counter));
        } else {
          plugin.getLogger().warning(
              "A cluster id of '" + cluster + "' was found, but does not have a valid counter.");
        }
      }
    }
  }

}
