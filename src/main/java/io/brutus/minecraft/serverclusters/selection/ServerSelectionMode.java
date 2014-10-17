package io.brutus.minecraft.serverclusters.selection;

import java.util.Comparator;

import io.brutus.minecraft.serverclusters.cache.ServerStatus;

/**
 * The supported selection modes for picking an ideal server out of a cluster.
 */
public enum ServerSelectionMode implements Comparator<ServerStatus> {

  LOAD_BALANCING(
      new LoadBalancingServerSelecter(),
      "Picks the server with the most open slots on it in order to distribute load as evenly as possible across the cluster."), MATCHMAKING(
      new MatchmakingServerSelecter(),
      "Picks the server with the least open slots, but still enough for the number of players that want to join, in order to fill servers up and make matches as fast as possible."), RANDOM(
      new RandomServerSelecter(),
      "Picks a server at random, as long as it has enough for how many players want to join.");

  private final String desc;
  private final Comparator<ServerStatus> selecter;

  private ServerSelectionMode(Comparator<ServerStatus> selecter, String description) {
    this.selecter = selecter;
    this.desc = description;
  }

  @Override
  public int compare(ServerStatus status1, ServerStatus status2) {
    return selecter.compare(status1, status2);
  }

  /**
   * Gets a description of how this mode works.
   * 
   * @return This mode's purpose and function.
   */
  public String getDescription() {
    return desc;
  }

}
