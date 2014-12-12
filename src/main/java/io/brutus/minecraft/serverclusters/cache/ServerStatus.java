package io.brutus.minecraft.serverclusters.cache;

/**
 * Status of a remote server. Contains information about its cluster, its number of open player
 * slots, and the last time the data was updated.
 * <p>
 * This implementation is thread safe.
 * <p>
 * Equality and hashing are judged based solely on server and cluster ids, not the ephemeral current
 * status of the server.
 */
public class ServerStatus {

  private final String id;
  private final String clusterId;

  private volatile int openSlots;
  private volatile long lastUpdated;

  /**
   * Class constructor.
   * 
   * @param id The id of the server this status is for.
   * @param clusterId The id of the cluster the server is a part of.
   * @param openSlots The number of open player slots the server currently has.
   * @throws IllegalArgumentException on a <code>null</code> or empty string, or on a negative
   *         number of open slots.
   */
  public ServerStatus(String id, String clusterId, int openSlots) throws IllegalArgumentException {
    if (id == null || id.equals("")) {
      throw new IllegalArgumentException("the server's id cannot be null or empty");
    }
    if (clusterId == null || clusterId.equals("")) {
      throw new IllegalArgumentException("the cluster id cannot be null or empty");
    }
    updateOpenSlots(openSlots);

    this.id = id;
    this.clusterId = clusterId;
  }

  /**
   * Gets the string id of this server, that identifies it on the network to other servers, proxies,
   * etc.
   * 
   * @return This server's id.
   */
  public final String getId() {
    return id;
  }

  /**
   * Gets the id of the cluster that this server is a part of.
   * 
   * @return This server's cluster.
   */
  public final String getClusterId() {
    return clusterId;
  }

  /**
   * Gets the millisecond timestamp of the last time this server's status was updated.
   * 
   * @return When this server status was last updated.
   */
  public final long getLastUpdated() {
    return lastUpdated;
  }

  /**
   * Gets the number of open player slots this server has, the number of players it could
   * theoretically accommodate.
   * 
   * @return This server's open slots.
   */
  public final int getOpenSlots() {
    return openSlots;
  }

  /**
   * Updates this server's number of open player slots. This number may affect how it compares to
   * other servers in its cluster when trying to select one of them to send players to.
   * 
   * @param openSlots The number of open player slots available on this server.
   * @throws IllegalArgumentException on a negative number of slots.
   */
  final void updateOpenSlots(int openSlots) throws IllegalArgumentException {
    if (openSlots < 0) {
      throw new IllegalArgumentException("the number of open slots cannot be less than 0");
    }
    this.openSlots = openSlots;
    lastUpdated = System.currentTimeMillis();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((clusterId == null) ? 0 : clusterId.hashCode());
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ServerStatus other = (ServerStatus) obj;
    if (clusterId == null) {
      if (other.clusterId != null)
        return false;
    } else if (!clusterId.equals(other.clusterId))
      return false;
    if (id == null) {
      if (other.id != null)
        return false;
    } else if (!id.equals(other.id))
      return false;
    return true;
  }



}
