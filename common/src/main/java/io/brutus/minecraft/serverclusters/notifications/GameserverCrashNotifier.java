package io.brutus.minecraft.serverclusters.notifications;

import io.brutus.minecraft.serverclusters.networkstatus.NetworkChangeListener;

/**
 * Notifies administrators when a connected gameserver server may have crashed.
 */
public class GameserverCrashNotifier implements NetworkChangeListener {

  private AdminNotifier notifier;

  public GameserverCrashNotifier(AdminNotifier notifier) {
    this.notifier = notifier;
  }

  @Override
  public void onServerJoin(String serverId, String clusterId, String ip, int port) {}

  @Override
  public void onServerWillShutdown(String serverId, String clusterId, String ip, int port) {}

  @Override
  public void onServerUnresponsive(String serverId, String clusterId, String ip, int port) {

    String notificationMsg =
        "Server '" + serverId + " in cluster '" + clusterId + "' (" + ip + ":" + port
            + ") may have crashed!";

    notifier.sendNotification("Possible Server Crash (" + serverId + ")", notificationMsg);
  }

}
