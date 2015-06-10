package io.brutus.minecraft.serverclusters.bungee;

import java.util.List;

import io.brutus.minecraft.serverclusters.networkstatus.NetworkStatus;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

/**
 * A command that displays the contents of the local network cache.
 */
public class NetworkStatusCommand extends Command {

  private NetworkStatus status;

  public NetworkStatusCommand(NetworkStatus status) {
    super("networkstatus", "serverclustsers.networkstatus", "ns");
    this.status = status;
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    List<String> result = status.toStringList();
    for (String str : result) {
      sender.sendMessage(new TextComponent(ChatColor.GREEN + str));
    }
  }

}
