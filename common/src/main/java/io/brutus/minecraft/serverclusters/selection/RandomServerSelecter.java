package io.brutus.minecraft.serverclusters.selection;

import io.brutus.minecraft.serverclusters.networkstatus.ServerStatus;

import java.util.Comparator;
import java.util.Random;

/**
 * A server selecter that performs pseudo-random comparisons.
 */
public class RandomServerSelecter implements Comparator<ServerStatus> {

  private final Random rand;

  public RandomServerSelecter() {
    this.rand = new Random();
  }

  @Override
  public int compare(ServerStatus status1, ServerStatus status2) throws IllegalArgumentException {
    if (status1 == null || status2 == null) {
      throw new IllegalArgumentException("statuses cannot be null");
    }
    boolean oneOrTwo = rand.nextBoolean();
    if (oneOrTwo) {
      return 1;
    } else {
      return -1;
    }
  }

}
