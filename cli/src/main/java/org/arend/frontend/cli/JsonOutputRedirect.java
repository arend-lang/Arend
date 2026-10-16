package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
import org.arend.cli.ArendOptions;
import org.arend.query.ConsoleQueryTool;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
 * <p>Not reentrant: open at most one at a time. The CLI opens it around library loading as
 * well as dispatch, so loader chatter cannot leak into the JSON document, and
 * {@link Dispatch#execute(CommandContext, CommandLine, JsonOutputRedirect)} takes the open
 * instance rather than making its own.
 */
public final class JsonOutputRedirect implements AutoCloseable {
  private final boolean myActive;
  private final PrintStream myRealStdout;
  private final PrintStream myRealStderr;
  /** The log file stream, or null when inactive or when the file could not be opened. */
  private final PrintStream myLog;
  private final Path myLogPath;
  /** The query tool that triggered the split; null when inactive. */
  private final ConsoleQueryTool myTool;
  private boolean myClosed;

  private JsonOutputRedirect(boolean active, PrintStream realStdout, PrintStream realStderr,
                             PrintStream log, Path logPath, ConsoleQueryTool tool) {
    myActive = active;
    myRealStdout = realStdout;
    myRealStderr = realStderr;
    myLog = log;
    myLogPath = logPath;
    myTool = tool;
  }

  /**
   * Starts the split when {@code --json} accompanies a query flag, else returns an inactive
   * instance. When the log file cannot be opened the split still happens -- JSON on the real
   * stdout -- but diagnostics fall back to stderr rather than being dropped.
   */
  public static JsonOutputRedirect open(CommandLine cmdLine, String clientCwd) {
    ConsoleQueryTool tool = ArendOptions.queryTool(cmdLine);
    // `--json` without a query flag is a no-op, and so is a query flag without `--json`.
    if (tool == null || !cmdLine.hasOption("json")) {
      return new JsonOutputRedirect(false, System.out, System.err, null, null, null);
    }

    PrintStream realStdout = System.out;
    PrintStream realStderr = System.err;
    Path logPath = ArendOptions.jsonLogPath(cmdLine, clientCwd);
    try {
      PrintStream log = new PrintStream(Files.newOutputStream(logPath), true, StandardCharsets.UTF_8);
      System.setOut(log);
      System.setErr(log);
      return new JsonOutputRedirect(true, realStdout, realStderr, log, logPath, tool);
    } catch (IOException e) {
      realStderr.println(ArendOptions.jsonLogFailed(tool, logPath, e.getMessage()));
      System.setOut(realStderr);
      return new JsonOutputRedirect(true, realStdout, realStderr, null, null, tool);
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
      myRealStderr.println(ArendOptions.jsonLogWritten(myTool, myLogPath));
    }
  }

}
