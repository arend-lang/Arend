package org.arend.frontend.cli.ai;

import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterFlag;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.EmptyScope;
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer;
import org.arend.typechecking.error.local.GoalError;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central decision point for {@code -ai} mode output.
 *
 * <p>Splits writes into three buckets:
 * <ul>
 *   <li><b>log-only</b>: progress narration, stage banners, cache statistics. Goes
 *       to the per-invocation {@link InvocationLog} file in <em>split mode</em>;
 *       echoed to stdout in <em>proxy mode</em>.</li>
 *   <li><b>granularity-filtered</b>: errors and warnings. Body printed to stdout
 *       only if the error pertains to the current invocation's target; otherwise
 *       only counted. Always written full to the log file.</li>
 *   <li><b>always-shown</b>: goals, unresolved-name suggestions, the closing
 *       summary. Stdout in every mode; also logged.</li>
 * </ul>
 *
 * <p>Mode is set at construction:
 * <ul>
 *   <li>{@link Mode#PROXY}: emits everything to stdout (Phase 1 — no visible change
 *       while callsites get migrated).</li>
 *   <li>{@link Mode#SPLIT}: applies the split rules above (Phase 3+).</li>
 * </ul>
 *
 * <p>Single-threaded use is assumed (one router per invocation; the daemon worker
 * creates a fresh router per request).
 */
public final class AiOutputRouter {
  public enum Mode { PROXY, SPLIT }

  private final Granularity granularity;
  private final Mode mode;
  private final InvocationLog log;
  private final PrintStream stdout;
  private final PrintStream stderr;

  private final Map<ModuleLocation, Counts> perModule = new LinkedHashMap<>();
  private int suggestionCount = 0;

  public AiOutputRouter(Granularity granularity, Mode mode, InvocationLog log,
                        PrintStream stdout, PrintStream stderr) {
    this.granularity = granularity;
    this.mode = mode;
    this.log = log;
    this.stdout = stdout;
    this.stderr = stderr;
  }

  public Granularity granularity() { return granularity; }
  public InvocationLog log() { return log; }
  public Mode mode() { return mode; }

  // ───────── log-only writes ─────────

  /** Banner-style line (e.g. {@code "--- AI: .sig + reindex ---"}). */
  public void stage(String banner) {
    logLine(banner);
    if (mode == Mode.PROXY) stdout.println(banner);
  }

  /** Informational line (e.g. {@code "[INFO] Persisted 12 modules"}). */
  public void info(String line) {
    logLine(line);
    if (mode == Mode.PROXY) stdout.println(line);
  }

  /** Raw narration with no stdout echo. Used for things like per-module {@code [OK]/[!]} lines. */
  public void log(String line) {
    logLine(line);
  }

  // ───────── granularity-filtered ─────────

  /**
   * Report an error-level diagnostic.
   * <p>In SPLIT mode the full body lands on stdout only if the error matches the
   * current target (MODULE granularity → same module; DEFINITION → same module +
   * same definition; LIBRARY → never). Off-target errors are counted toward the
   * closing summary and written to the log.
   * <p>PROXY mode always emits the body to stderr (legacy behavior).
   */
  public void error(GeneralError err) {
    String text = render(err);
    logLine(text);
    countError(err);
    if (mode == Mode.PROXY || matchesTarget(err)) {
      stderr.println(text);
      stderr.flush();
    }
  }

  /** Report a warning-level diagnostic. Same filter rules as {@link #error}. */
  public void warning(GeneralError err) {
    String text = render(err);
    logLine(text);
    countWarning(err);
    if (mode == Mode.PROXY || matchesTarget(err)) {
      stdout.println(text);
      stdout.flush();
    }
  }

  // ───────── always-shown ─────────

  /**
   * Goal diagnostic ({@code {?}}). In MODULE/DEFINITION granularity, goals matching
   * the target are printed full to stdout; off-target goals contribute only to the
   * closing summary. LIBRARY granularity aggregates everything (no individual goal
   * bodies on stdout). PROXY mode always prints. Always logged.
   */
  public void goal(GeneralError err) {
    String text = render(err);
    logLine(text);
    countGoal(err);
    if (mode == Mode.PROXY || matchesTarget(err)) {
      stdout.println(text);
      stdout.flush();
    }
  }

  /**
   * Unresolved-reference candidate block. Granularity rules match {@link #error}:
   * at LIBRARY granularity the block goes to the log only (the agent is doing a
   * whole-library pass and doesn't want per-module noise); at MODULE / DEFINITION
   * granularity it goes to stdout only when {@code affectedModule} matches the
   * target. Null {@code affectedModule} means "no module info" → always show
   * (PROXY-style fallback).
   */
  public void resolveSuggestion(String text, @Nullable ModulePath affectedModule) {
    logLine(text);
    suggestionCount++;
    boolean show;
    if (mode == Mode.PROXY) {
      show = true;
    } else if (affectedModule == null) {
      show = true;
    } else if (granularity.kind == Granularity.Kind.LIBRARY) {
      show = false;
    } else {
      show = affectedModule.equals(granularity.targetModule);
    }
    if (show) {
      stdout.println(text);
      stdout.flush();
    }
  }

  /** Convenience overload for legacy callers that don't have an affected module. */
  public void resolveSuggestion(String text) {
    resolveSuggestion(text, null);
  }

  // ───────── module-level result tracking ─────────

  /**
   * Per-module typecheck result ({@code [OK] Foo}, {@code [!] Bar}). The summary
   * uses these; in split mode the line itself goes to the log only.
   */
  public void moduleResult(ModuleLocation module, GeneralError.Level result) {
    counts(module).result = result;
    String line = "[" + resultChar(result) + "] " + module.getModulePath();
    logLine(line);
    if (mode == Mode.PROXY) stdout.println(line);
  }

  // ───────── accessors for the summary emitter (Phase 6) ─────────

  public Map<ModuleLocation, Counts> perModule() { return perModule; }
  public int suggestionCount() { return suggestionCount; }

  /** Helper: aggregate error count across all modules. */
  public int totalErrors() {
    int sum = 0;
    for (Counts c : perModule.values()) sum += c.errors;
    return sum;
  }

  /** Helper: aggregate goal count across all modules. */
  public int totalGoals() {
    int sum = 0;
    for (Counts c : perModule.values()) sum += c.goals;
    return sum;
  }

  /** Helper: aggregate warning count across all modules. */
  public int totalWarnings() {
    int sum = 0;
    for (Counts c : perModule.values()) sum += c.warnings;
    return sum;
  }

  // ───────── closing summary ─────────

  /**
   * Emit the final verdict for this {@code -ai} invocation. Shape depends on
   * {@link Granularity}; always ends with the log file path.
   */
  public void summary() {
    if (mode == Mode.PROXY) {
      writeSummaryBody();
      return;
    }
    writeSummaryBody();
  }

  private void writeSummaryBody() {
    int e = totalErrors();
    int g = totalGoals();
    int w = totalWarnings();
    StringBuilder out = new StringBuilder();
    switch (granularity.kind) {
      case LIBRARY -> {
        if (e == 0 && g == 0) {
          out.append("OK. No errors or goals.");
          if (w > 0) out.append(" ").append(w).append(" warning").append(w == 1 ? "" : "s").append(" (see log).");
          out.append("\n");
        } else {
          // Column width: longest module-path among rows we're about to emit.
          int width = 0;
          for (var entry : perModule.entrySet()) {
            Counts c = entry.getValue();
            boolean inErrorTable = c.errors > 0;
            boolean inGoalTable = c.errors == 0 && c.goals > 0;
            if (inErrorTable || inGoalTable) {
              width = Math.max(width, entry.getKey().getModulePath().toString().length());
            }
          }
          if (e > 0) {
            out.append("Modules with errors:\n");
            for (var entry : perModule.entrySet()) {
              if (entry.getValue().errors == 0) continue;
              appendRow(out, entry.getKey().getModulePath().toString(), width);
              appendCounts(out, entry.getValue());
              out.append('\n');
            }
          }
          if (g > 0) {
            out.append("Modules with goals:\n");
            for (var entry : perModule.entrySet()) {
              if (entry.getValue().goals == 0 || entry.getValue().errors != 0) continue;
              appendRow(out, entry.getKey().getModulePath().toString(), width);
              out.append(entry.getValue().goals).append(entry.getValue().goals == 1 ? " goal" : " goals")
                  .append('\n');
            }
          }
        }
      }
      case MODULE, DEFINITION -> {
        int targetErrors = 0, targetGoals = 0, targetWarnings = 0;
        int otherErrors = 0, otherGoals = 0;
        int otherModulesWithErrors = 0, otherModulesWithGoals = 0;
        for (var entry : perModule.entrySet()) {
          boolean isTarget = entry.getKey().getModulePath().equals(granularity.targetModule);
          if (isTarget) {
            targetErrors += entry.getValue().errors;
            targetGoals += entry.getValue().goals;
            targetWarnings += entry.getValue().warnings;
          } else {
            if (entry.getValue().errors > 0) { otherErrors += entry.getValue().errors; otherModulesWithErrors++; }
            if (entry.getValue().goals > 0)  { otherGoals  += entry.getValue().goals;  otherModulesWithGoals++; }
          }
        }
        if (e == 0 && g == 0) {
          out.append("OK. No errors or goals.");
          if (w > 0) out.append(" ").append(w).append(" warning").append(w == 1 ? "" : "s").append(" (see log).");
          out.append("\n");
        } else {
          if (targetErrors == 0 && targetGoals == 0) {
            out.append("OK for target. ");
            if (otherErrors > 0) out.append(otherErrors).append(" error").append(otherErrors == 1 ? "" : "s")
                .append(" in ").append(otherModulesWithErrors).append(" other module")
                .append(otherModulesWithErrors == 1 ? "" : "s").append(". ");
            if (otherGoals > 0) out.append(otherGoals).append(" goal").append(otherGoals == 1 ? "" : "s")
                .append(" in ").append(otherModulesWithGoals).append(" other module")
                .append(otherModulesWithGoals == 1 ? "" : "s").append(". ");
            out.append("Details in log.\n");
          } else {
            if (otherErrors > 0 || otherGoals > 0) {
              out.append("Other modules: ");
              if (otherErrors > 0) out.append(otherErrors).append(" error").append(otherErrors == 1 ? "" : "s")
                  .append(" in ").append(otherModulesWithErrors).append(" module")
                  .append(otherModulesWithErrors == 1 ? "" : "s");
              if (otherErrors > 0 && otherGoals > 0) out.append(", ");
              if (otherGoals > 0) out.append(otherGoals).append(" goal").append(otherGoals == 1 ? "" : "s")
                  .append(" in ").append(otherModulesWithGoals).append(" module")
                  .append(otherModulesWithGoals == 1 ? "" : "s");
              out.append(" (full list in log).\n");
            }
          }
        }
      }
    }
    if (log != null) {
      out.append("Full log: ").append(log.path()).append('\n');
    }
    stdout.print(out);
    stdout.flush();
    if (log != null) {
      // Mirror the summary into the log too, so a single file captures the whole run.
      log.writeLine("");
      log.writeLine("=== SUMMARY ===");
      log.writeLine(out.toString());
    }
  }

  /**
   * Emit {@code "  <module-path><pad> — "} where {@code pad} brings the visible
   * column up to {@code width} characters. {@code width} is the longest module
   * path in the section, computed once before the loop.
   */
  private static void appendRow(StringBuilder out, String modulePath, int width) {
    out.append("  ").append(modulePath);
    for (int i = modulePath.length(); i < width; i++) out.append(' ');
    out.append("  — ");
  }

  private static void appendCounts(StringBuilder out, Counts c) {
    boolean first = true;
    if (c.errors > 0) { out.append(c.errors).append(" error").append(c.errors == 1 ? "" : "s"); first = false; }
    if (c.goals > 0)  { if (!first) out.append(", "); out.append(c.goals).append(" goal").append(c.goals == 1 ? "" : "s"); first = false; }
    if (c.warnings > 0) { if (!first) out.append(", "); out.append(c.warnings).append(" warning").append(c.warnings == 1 ? "" : "s"); }
  }

  // ───────── internals ─────────

  private static String render(GeneralError err) {
    PrettyPrinterConfigWithRenamer cfg = new PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE);
    if (err instanceof GoalError) {
      cfg.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE);
    }
    return err.getDoc(cfg).toString();
  }

  private void logLine(String line) {
    if (log != null) log.writeLine(line);
  }

  private void countError(GeneralError err) {
    forEachAffectedModule(err, m -> counts(m).errors++);
  }

  private void countGoal(GeneralError err) {
    forEachAffectedModule(err, m -> counts(m).goals++);
  }

  private void countWarning(GeneralError err) {
    forEachAffectedModule(err, m -> counts(m).warnings++);
  }

  private void forEachAffectedModule(GeneralError err, java.util.function.Consumer<ModuleLocation> sink) {
    final boolean[] any = { false };
    err.forAffectedDefinitions((referable, child) -> {
      if (referable instanceof LocatedReferable located) {
        ModuleLocation m = located.getLocation();
        if (m != null) {
          sink.accept(m);
          any[0] = true;
        }
      }
    });
    if (!any[0]) {
      // Unaffiliated diagnostic — still count it under a sentinel so totals add up.
      sink.accept(ORPHAN);
    }
  }

  private Counts counts(ModuleLocation module) {
    return perModule.computeIfAbsent(module, k -> new Counts());
  }

  /**
   * Returns true if the diagnostic's affected modules include the current target.
   * For LIBRARY granularity, always false (no target to compare against).
   * Used by Phase 5 to gate stdout emission of error bodies.
   */
  public boolean matchesTarget(GeneralError err) {
    if (granularity.kind == Granularity.Kind.LIBRARY) return false;
    final boolean[] hit = { false };
    err.forAffectedDefinitions((referable, child) -> {
      if (hit[0]) return;
      if (referable instanceof LocatedReferable located) {
        ModuleLocation m = located.getLocation();
        if (m == null) return;
        if (!matchesModule(m.getModulePath())) return;
        if (granularity.kind == Granularity.Kind.MODULE) { hit[0] = true; return; }
        // DEFINITION: also require the long name to match.
        if (referable instanceof TCDefReferable tcd) {
          LongName name = longNameOf(tcd);
          if (name != null && name.equals(granularity.targetDefinition)) hit[0] = true;
        }
      }
    });
    return hit[0];
  }

  private boolean matchesModule(@Nullable ModulePath path) {
    return path != null && granularity.targetModule != null
        && path.equals(granularity.targetModule);
  }

  private static @Nullable LongName longNameOf(TCDefReferable tcd) {
    LocatedReferable cur = tcd;
    java.util.List<String> parts = new java.util.ArrayList<>();
    while (cur != null && cur.getLocatedReferableParent() != null) {
      parts.add(0, cur.getRefName());
      cur = cur.getLocatedReferableParent();
    }
    return parts.isEmpty() ? null : new LongName(parts);
  }

  private static char resultChar(GeneralError.Level result) {
    if (result == null) return ' ';
    return switch (result) {
      case ERROR -> '✗';
      case GOAL -> '◯';
      case WARNING, WARNING_UNUSED -> '⚠';
      default -> '·';
    };
  }

  /** Sentinel for diagnostics with no affected module. */
  private static final ModuleLocation ORPHAN =
      new ModuleLocation("<orphan>", ModuleLocation.LocationKind.GENERATED, new ModulePath());

  /** Per-module diagnostic counters and last-seen result. */
  public static final class Counts {
    public int errors = 0;
    public int goals = 0;
    public int warnings = 0;
    public GeneralError.Level result;
  }
}
