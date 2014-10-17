package io.brutus.minecraft.serverclusters.bukkit;

import io.brutus.minecraft.serverclusters.NetworkStatus;
import io.brutus.minecraft.serverclusters.SlotManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * A maintenance command that shows the current status of the network cache.
 */
public class NetworkStatusCommand implements CommandExecutor {

  private final NetworkStatus network;
  private final SlotManager slots;
  private final String serverId;
  private final String clusterId;

  /**
   * Class constructor.
   * 
   * @param network The network's status.
   * @param slots The manager of local slots, if this gameserver is a p2p-server. Can be
   *        <code>null</code> to exclude this information.
   * @param serverId The id of this gameserver.
   * @param clusterId The cluster this gameserver is a part of. Can be <code>null</code> to exclude
   *        this information.
   * @throws IllegalArgumentException on a <code>null</code> network status or server id.
   */
  public NetworkStatusCommand(NetworkStatus network, SlotManager slots, String serverId,
      String clusterId) throws IllegalArgumentException {
    if (network == null || serverId == null) {
      throw new IllegalArgumentException("network and server idcannot be null");
    }
    this.network = network;
    this.serverId = serverId;
    this.slots = slots;
    this.clusterId = clusterId;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
    if (sender instanceof Player && !((Player) sender).isOp()) {
      return false;
    }

    sender.sendMessage(network.toStringArray());
    String second = "Your server: " + serverId;
    if (slots != null && clusterId != null) {
      second =
          second + " (cluster: " + clusterId + ", " + slots.getOpenSlots() + " open slots, "
              + slots.getTotalSlots() + " total slots)";
    }
    sender.sendMessage(second);
    return true;
  }
}
