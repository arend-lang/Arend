package org.arend.naming.resolving;

import org.arend.ext.module.LongName;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.RedirectingReferable;
import org.arend.naming.scope.NamespaceCommandSink;
import org.arend.naming.scope.RecordingScope;
import org.arend.naming.scope.Scope;
import org.arend.server.ScopeUsage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ScopeUsageCollector {
  private final Map<LongName, Set<ScopeUsage.UsedName>> myNames = new HashMap<>();
  private final Map<LongName, Set<String>> myUnknownNames = new HashMap<>();
  private final Map<LongName, Set<ScopeUsage.UsedName>> myGuessedNames = new HashMap<>();
  private LongName myCurrentLongName;
  private boolean myEnabled = true;
  private int mySuppression;

  public @Nullable LongName startGroup(@NotNull LongName longName) {
    LongName previous = myCurrentLongName;
    myCurrentLongName = longName;
    myNames.computeIfAbsent(longName, k -> new HashSet<>());
    myUnknownNames.computeIfAbsent(longName, k -> new HashSet<>());
    myGuessedNames.computeIfAbsent(longName, k -> new HashSet<>());
    return previous;
  }

  public void finishGroup(@Nullable LongName previous) {
    myCurrentLongName = previous;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void suppress() {
    mySuppression++;
  }

  public void release() {
    mySuppression--;
  }

  private boolean isRecording() {
    return myEnabled && mySuppression == 0 && myCurrentLongName != null;
  }

  public @NotNull Scope record(@NotNull Scope scope) {
    return new RecordingScope(scope, this);
  }

  public void recordLookup(@NotNull String name, @NotNull Referable referable, @NotNull NamespaceCommandSink sink) {
    if (!isRecording()) return;
    if (!sink.isKnown()) {
      myUnknownNames.get(myCurrentLongName).add(name);
    } else {
      myNames.get(myCurrentLongName).add(new ScopeUsage.UsedName(sink.getCommand(), name, RedirectingReferable.getOriginalReferable(referable)));
    }
  }

  public void recordUnknownName(@NotNull String name) {
    if (!isRecording()) return;
    myUnknownNames.get(myCurrentLongName).add(name);
  }

  public void recordGuess(@NotNull String name, @NotNull Referable referable, @NotNull NamespaceCommandSink sink) {
    if (!isRecording() || !sink.isKnown()) return;
    myGuessedNames.get(myCurrentLongName).add(new ScopeUsage.UsedName(sink.getCommand(), name, RedirectingReferable.getOriginalReferable(referable)));
  }

  public void recordModuleMember(@NotNull String name, @NotNull Referable referable) {
    if (!isRecording()) return;
    myNames.get(myCurrentLongName).add(new ScopeUsage.UsedName(null, name, RedirectingReferable.getOriginalReferable(referable)));
  }

  public @NotNull Map<LongName, ScopeUsage> getUsages() {
    Map<LongName, ScopeUsage> result = new HashMap<>(myNames.size());
    for (Map.Entry<LongName, Set<ScopeUsage.UsedName>> entry : myNames.entrySet()) {
      result.put(entry.getKey(), new ScopeUsage(entry.getValue(), myUnknownNames.get(entry.getKey()), myGuessedNames.get(entry.getKey()), null, null));
    }
    return result;
  }
}
