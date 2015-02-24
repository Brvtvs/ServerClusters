package io.brutus.minecraft.serverclusters.selection;

import java.util.Comparator;

import io.brutus.minecraft.serverclusters.networkstatus.ServerStatus;

/**
 * A server selecter that gives lower values to servers with fewer open slots.
 * <p>
 * This comparator will order server collections so that the most desirable servers for matchmaking
 * are first when sorted into ascending order (the default).
 */
public class MatchmakingServerSelecter implements Comparator<ServerStatus> {

  @Override
  public int compare(ServerStatus status1, ServerStatus status2) throws IllegalArgumentException {
    if (status1 == null || status2 == null) {
      throw new IllegalArgumentException("statuses cannot be null");
    }

    return (status1.getOpenSlots() - status2.getOpenSlots());
  }
}
