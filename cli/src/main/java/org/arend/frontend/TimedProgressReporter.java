package org.arend.frontend;

import org.arend.ext.util.Pair;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.ProgressReporter;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimedProgressReporter implements ProgressReporter<List<? extends Concrete.ResolvableDefinition>> {
  private final Map<TCDefReferable, Pair<Long,Long>> myTimes = new HashMap<>();

  public static @NotNull String timeToString(long time) {
    if (time < 10000) {
      return time + "ms";
    }
    if (time < 60000) {
      return time / 1000 + ("." + (time / 100 % 10)) + "s";
    }

    long seconds = time / 1000;
    return (seconds / 60) + "m" + (seconds % 60) + "s";
  }

  @Override
  public void beginProcessing(int numberOfItems) {}

  @Override
  public void beginItem(@NotNull List<? extends Concrete.ResolvableDefinition> item) {
    for (Concrete.ResolvableDefinition definition : item) {
      myTimes.compute(definition.getData(), (r,pair) -> new Pair<>(System.currentTimeMillis(), pair == null ? 0 : pair.proj2));
    }
  }

  @Override
  public void endItem(@NotNull List<? extends Concrete.ResolvableDefinition> item) {
    for (Concrete.ResolvableDefinition definition : item) {
      myTimes.compute(definition.getData(), (r,pair) -> pair == null ? new Pair<>(0L, 0L) : new Pair<>(pair.proj1, pair.proj2 + (System.currentTimeMillis() - pair.proj1)));
    }
  }

  public void print() {
    if (myTimes.isEmpty()) return;

    System.out.println();
    List<Pair<TCDefReferable,Long>> list = new ArrayList<>(myTimes.size());
    for (Map.Entry<TCDefReferable, Pair<Long, Long>> entry : myTimes.entrySet()) {
      list.add(new Pair<>(entry.getKey(), entry.getValue().proj2));
    }
    list.sort((o1, o2) -> Long.compare(o2.proj2, o1.proj2));
    for (Pair<TCDefReferable, Long> pair : list) {
      System.out.println(pair.proj1.getRefLongName() + ": " + timeToString(pair.proj2));
    }
  }
}
