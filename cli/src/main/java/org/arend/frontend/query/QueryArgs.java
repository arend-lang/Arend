package org.arend.frontend.query;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Shared sub-argument parsing for the query tools' {@code parseArgs} loops. A
 * tool declares the tokens it accepts on a {@link Tokens} builder -- bare
 * {@link Tokens#flag flags} (e.g. {@code with-tests}) and {@code key=value}
 * {@link Tokens#param params} (e.g. {@code limit=50}), each an action closing
 * over the tool's own {@code Options} -- plus a {@link Tokens#positional}
 * handler for everything else. {@link Tokens#parse} runs the single dispatch
 * loop; a {@link ArgError} thrown by any action (or by the value helper
 * {@link #intValue}) is caught, tagged, and printed as
 * {@code [ERROR] -xx <message>}, and {@code parse} returns {@code false} so the
 * caller returns {@code null}. Only the leaf value parsing is centralized;
 * positional arity and post-parse validation stay in each tool.
 */
public final class QueryArgs {
  private QueryArgs() {}

  /** A malformed option value. The message is tool-agnostic; {@link Tokens#parse} prepends the tag. */
  public static final class ArgError extends RuntimeException {
    public ArgError(String message) { super(message); }
  }

  /** Parses a non-negative-int option value (e.g. the {@code 50} of {@code limit=50}); throws {@link ArgError} on a bad number. */
  static int intValue(String name, String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new ArgError("Bad " + name + ": " + value);
    }
  }

  /**
   * A tool's declared sub-argument grammar. Register {@link #flag flags} and
   * {@link #param params}, set a {@link #positional} handler, then call
   * {@link #parse}. Flags are matched by exact token; a {@code key=value} token
   * whose {@code key} (the text before the first {@code =}) is a registered
   * param is dispatched to it with the value; everything else goes to the
   * positional handler. Flags are matched first, so a valued token that is
   * itself a fixed flag (e.g. {@code aliases=true}) is recognized as that flag.
   */
  public static final class Tokens {
    private final Map<String, Runnable> flags = new HashMap<>();
    private final Map<String, Consumer<String>> params = new HashMap<>();
    private Consumer<String> positional = arg -> {
      throw new ArgError("Unexpected argument: '" + arg + "'");
    };

    public Tokens flag(String name, Runnable action) {
      flags.put(name, action);
      return this;
    }

    public Tokens param(String key, Consumer<String> action) {
      params.put(key, action);
      return this;
    }

    public Tokens positional(Consumer<String> action) {
      this.positional = action;
      return this;
    }

    /** Registers the shared {@code self} flag (drop dependencies) onto {@code opts}. */
    public Tokens self(QueryOptions opts) {
      return flag("self", () -> opts.self = true);
    }

    /** Registers the shared {@code limit=N} param onto {@code opts}. */
    public Tokens limit(QueryOptions opts) {
      return param("limit", v -> opts.limit = intValue("limit", v));
    }

    /**
     * Runs the dispatch loop over {@code args}. Returns {@code true} on success;
     * on a {@link ArgError} from any action, prints {@code [ERROR] <tag> <message>}
     * to {@code System.err} and returns {@code false}.
     */
    public boolean parse(String tag, String[] args) {
      try {
        for (String arg : args) {
          Runnable flag = flags.get(arg);
          if (flag != null) {
            flag.run();
            continue;
          }
          int eq = arg.indexOf('=');
          if (eq > 0) {
            Consumer<String> param = params.get(arg.substring(0, eq));
            if (param != null) {
              param.accept(arg.substring(eq + 1));
              continue;
            }
          }
          positional.accept(arg);
        }
        return true;
      } catch (ArgError e) {
        System.err.println("[ERROR] " + tag + " " + e.getMessage());
        return false;
      }
    }
  }
}
