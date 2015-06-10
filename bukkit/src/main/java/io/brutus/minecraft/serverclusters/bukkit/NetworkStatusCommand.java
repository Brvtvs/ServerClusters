package io.brutus.minecraft.serverclusters.bukkit;

import io.brutus.minecraft.serverclusters.gameserver.ServerClusters;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * A maintenance command that shows the current status of the network cache.
 */
public class NetworkStatusCommand implements CommandExecutor {

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
    if (sender instanceof Player && !((Player) sender).isOp()) {
      return false;
    }

    for (String str : ServerClusters.getSingleton().getHumanReadableNetworkStatus()) {
      sender.sendMessage(ChatColor.GREEN + str);
    }
    return true;
  }
}
