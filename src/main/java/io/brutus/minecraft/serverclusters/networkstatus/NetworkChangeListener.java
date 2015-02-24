package io.brutus.minecraft.serverclusters.networkstatus;

/**
 * A listener to changes in the network's status, specifically when servers change to/from being
 * valid and responsive to/from being invalid and/or unresponsive.
 */
public interface NetworkChangeListener {

  /**
   * Called when a server first starts, recovers from a crash, or simply becomes reachable again.
   * <p>
   * If the service running this has just started, this will be called many times in succession as
   * the network's current state is initially discovered. After that, this will be called when a
   * server first announces itself or becomes reachable after a period of being unresponsive.
   * <p>
   * If a server was assumed to be offline and triggers {@link #onServerUnresponsive(String)}, this
   * will be called if it becomes reachable again.
   * 
   * @param serverId The id of the server that has become reachable.
   * @param ip The IP address of the server that has become reachable.
   * @param port The port of the server that has become reachable.
   */
  void onServerJoin(String serverId, String ip, int port);

  /**
   * Called when a server is about to shutdown according to plan.
   * <p>
   * If a server crashes or otherwise does not shutdown cleanly,
   * {@link #onServerUnresponsive(String)} will be called instead.
   * <p>
   * A shutdown message does not guarantee the server shuts down or restarts cleanly without any
   * problems. All it guarantees is that a server made it to the point where it was about to shut
   * down and did so without crashing. If it crashes after this point, or fails to restart, or has
   * any other similar issue, it would still have send a shutdown message.
   * 
   * @param serverId The id of the server shutting down.
   */
  void onServerWillShutdown(String serverId);

  /**
   * Called when a server becomes unresponsive and should be assumed to be crashed, unreachable, or
   * otherwise invalid until further notice.
   * <p>
   * <b>Not</b> called if the server shuts down according to plan, see
   * {@link #onShutdownNotification(ShutdownNotification)}.
   * <p>
   * If the server later becomes responsive again, {@link #onServerJoin(String, String, int)} will
   * be called.
   * 
   * @param serverId The id of the server that has become unresponsive.
   */
  void onServerUnresponsive(String serverId);

}
