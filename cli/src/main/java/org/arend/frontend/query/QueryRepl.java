package org.arend.frontend.query;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/** A REPL that answers the query tools' {@code :} commands: the CLI's, which knows its libraries. */
public interface QueryRepl {
  /**
   * Runs {@code body} against the REPL's query context, with what it prints going to the REPL;
   * prints {@code unavailableMsg} instead when the REPL has no libraries to query.
   */
  void runQueryCommand(@NotNull String unavailableMsg, @NotNull Consumer<ConsoleQueryTool.QueryContext> body);
}
