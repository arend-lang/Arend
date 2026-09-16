package org.arend.naming.scope;

import org.arend.term.group.ConcreteNamespaceCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NamespaceCommandSink {
  private final boolean myGuess;
  private boolean myKnown;
  private ConcreteNamespaceCommand myCommand;

  public NamespaceCommandSink() {
    this(false);
  }

  private NamespaceCommandSink(boolean guess) {
    myGuess = guess;
  }

  public static @NotNull NamespaceCommandSink forGuess() {
    return new NamespaceCommandSink(true);
  }

  public boolean isGuess() {
    return myGuess;
  }

  public @Nullable ConcreteNamespaceCommand getCommand() {
    return myCommand;
  }

  public boolean isKnown() {
    return myKnown;
  }

  public void setCommand(@Nullable ConcreteNamespaceCommand command) {
    myKnown = true;
    myCommand = command;
  }

  public void setNoCommand() {
    setCommand(null);
  }

  public void reset() {
    myKnown = false;
    myCommand = null;
  }

  @Override
  public String toString() {
    String answer = !myKnown ? "unknown" : myCommand == null ? "no command" : myCommand.toString();
    return myGuess ? answer + " (guess)" : answer;
  }
}
