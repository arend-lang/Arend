package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.query.ConsoleQueryTool;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The {@code --json} output split for the query tools ({@code -ss}/{@code -ps}/{@code -fu}/
 * {@code -ch}/{@code -sc}).
 *
 * <p>In JSON mode stdout must carry ONLY the JSON document, so {@link System#out} and
 * {@link System#err} are redirected to a log file for the duration: every diagnostic
 * (library-loading chatter, the query echo, {@code [WARN]}/{@code [ERROR]}) lands there,
 * while the document itself is written to {@link #stdout()} -- the real stdout captured
 * before the swap. {@link #close()} restores both streams and points at the log on the
 * real stderr.
 *
 * <p>{@link #open} returns an inactive instance when {@code --json} is absent or no query
 * flag is present, so callers can wrap unconditionally; an inactive redirect swaps nothing
 * and reports the ambient {@code System.out} as {@link #stdout()}.
 *
 * <p>Not reentrant: open at most one at a time. The local CLI path opens it around library
 * loading + dispatch, the daemon path around dispatch alone (its libraries are already warm),
 * and {@link Dispatch#execute(CommandContext, CommandLine, JsonOutputRedirect)} takes the
 * open instance rather than making its own.
 */
public final class JsonOutputRedirect implements AutoCloseable {
  private final boolean myActive;
  private final PrintStream myRealStdout;
  private final PrintStream myRealStderr;
  /** The log file stream, or null when inactive or when the file could not be opened. */
  private final PrintStream myLog;
  private final Path myLogPath;
  /** The query flag that triggered the split, e.g. {@code "-ss"}; null when inactive. */
  private final String myFlag;
  private boolean myClosed;

  private JsonOutputRedirect(boolean active, PrintStream realStdout, PrintStream realStderr,
                             PrintStream log, Path logPath, String flag) {
    myActive = active;
    myRealStdout = realStdout;
    myRealStderr = realStderr;
    myLog = log;
    myLogPath = logPath;
    myFlag = flag;
  }

  /**
   * Starts the split when {@code --json} accompanies a query flag, else returns an inactive
   * instance. When the log file cannot be opened the split still happens -- JSON on the real
   * stdout -- but diagnostics fall back to stderr rather than being dropped.
   */
  public static JsonOutputRedirect open(CommandLine cmdLine) {
    String flag = null;
    for (ConsoleQueryTool tool : ConsoleMain.QUERY_TOOLS) {
      if (cmdLine.hasOption(tool.shortName())) {
        flag = "-" + tool.shortName();
        break;
      }
    }
    // `--json` without a query flag is a no-op, and so is a query flag without `--json`.
    if (flag == null || !cmdLine.hasOption("json")) {
      return new JsonOutputRedirect(false, System.out, System.err, null, null, null);
    }

    PrintStream realStdout = System.out;
    PrintStream realStderr = System.err;
    Path logPath = resolveLogPath(cmdLine);
    try {
      PrintStream log = new PrintStream(Files.newOutputStream(logPath), true, StandardCharsets.UTF_8);
      System.setOut(log);
      System.setErr(log);
      return new JsonOutputRedirect(true, realStdout, realStderr, log, logPath, flag);
    } catch (IOException e) {
      realStderr.println("[WARN] cannot open " + flag + " log file " + logPath + " ("
          + e.getMessage() + "); routing diagnostics to stderr instead");
      System.setOut(realStderr);
      return new JsonOutputRedirect(true, realStdout, realStderr, null, null, flag);
    }
  }

  /** True when the caller should emit JSON rather than the human-readable listing. */
  public boolean active() {
    return myActive;
  }

  /**
   * The stream the JSON document goes to: the real stdout when the split is on (System.out
   * having been redirected away), the ambient System.out otherwise.
   */
  public PrintStream stdout() {
    return myRealStdout;
  }

  /** Restores {@code System.out}/{@code System.err}. Idempotent. */
  @Override
  public void close() {
    if (!myActive || myClosed) return;
    myClosed = true;
    System.setOut(myRealStdout);
    System.setErr(myRealStderr);
    if (myLog != null) {
      myLog.close();
      myRealStderr.println("[INFO] " + myFlag + " diagnostics written to " + myLogPath);
    }
  }

  /**
   * The file diagnostics are written to: an explicit {@code --log-file <path>} when given,
   * else {@code <java.io.tmpdir>/arend-symbol-search.log}, overwritten each run.
   */
  private static Path resolveLogPath(CommandLine cmdLine) {
    String custom = cmdLine.getOptionValue("log-file");
    if (custom != null && !custom.isEmpty()) return Paths.get(custom);
    return Paths.get(System.getProperty("java.io.tmpdir"), "arend-symbol-search.log");
  }
}
