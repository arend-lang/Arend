package org.arend.frontend.cli;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterFlag;
import org.arend.ext.util.Pair;
import org.arend.frontend.library.BinaryLoader;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.scope.EmptyScope;
import org.arend.server.ArendServer;
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer;
import org.arend.typechecking.error.local.GoalError;
import org.arend.util.FileUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-run state shared across CLI command handlers: what used to be the fields and local
 * variables of {@code ConsoleMain.run()}. {@link CliSetup} populates it before any handler runs.
 *
 * <p>The fields are public on purpose. This is one method's locals given a name, not an
 * abstraction over them, and accessors would only obscure that.
 */
public class CommandContext {
  // ───────── server and library state: populated by CliSetup, then mostly read-only ─────────

  public LibraryManager libraryManager;
  public CliServerRequester requester;

  /** Loads {@code .arc} caches in bulk before a typecheck; see {@code BinaryLoader}. */
  public BinaryLoader binaryLoader;
  public ArendServer server;
  public final List<SourceLibrary> requestedLibraries = new ArrayList<>();
  public final Set<Pair<ModulePath, LongName>> requestedModules = new LinkedHashSet<>();
  public final List<Path> libDirs = new ArrayList<>();
  public boolean doubleCheck;
  public boolean recompile;

  /** If true, persist typechecked modules as {@code .arc} binary caches after typechecking. */
  public boolean serialize;

  // ───────── per-run bookkeeping consulted across phases ─────────

  public boolean exitWithError;
  public final Map<ModuleLocation, GeneralError.Level> moduleResults = new LinkedHashMap<>();

  /**
   * Drops everything reported while it is set. Used around passes whose diagnostics are not the
   * user's business — typechecking a dependency library's uncached modules, where any error will
   * be reported again by the pass that actually asked for it.
   */
  public boolean suppressErrorOutput;

  // ───────── progress line ─────────

  private boolean myProgressActive;
  private int myLastProgressLength;

  /**
   * Overwrites the single stderr line that says which module is being typechecked. A cold run
   * over a library the size of arend-lib is two minutes of silence without it, with no way to
   * tell a slow module from a hang.
   *
   * <p>It is a partial line, so anything else that writes has to call {@link #finishProgressLine}
   * first or land in the middle of it. Both error reporters here do; so must every banner.
   */
  public void reportModuleProgress(int checked, int total, ModulePath modulePath) {
    String line = "[" + (checked + 1) + "/" + total + "] Typechecking " + modulePath;
    StringBuilder sb = new StringBuilder().append('\r').append(line);
    int pad = myLastProgressLength - line.length();
    if (pad > 0) {
      sb.repeat(" ", pad);
    }
    System.err.print(sb);
    System.err.flush();
    myLastProgressLength = line.length();
    myProgressActive = true;
  }

  /** Terminates the progress line if one is open, so the next write starts on a fresh line. */
  public void finishProgressLine() {
    if (myProgressActive) {
      System.err.println();
      myProgressActive = false;
      myLastProgressLength = 0;
    }
  }

  // ───────── reporters ─────────

  /**
   * Plain stderr reporter; flips {@link #exitWithError} on an ERROR, like {@link #dispatchError}.
   *
   * <p>Only on an ERROR: it also carries recoverable warnings — an unreadable binary cache, say,
   * a condition the very same run then repairs — and failing on those exits 1 for a run in which
   * nothing was wrong. Callers that must fail on a lower level set the flag themselves.
   */
  public final ErrorReporter systemErrErrorReporter = error -> {
    finishProgressLine();
    System.err.println(error);
    System.err.flush();
    if (error.level == GeneralError.Level.ERROR) exitWithError = true;
  };

  /** Goal-aware reporter that also records each module's worst diagnostic level. */
  public final ErrorReporter errorReporter = new ErrorReporter() {
    @Override
    public void report(GeneralError error) {
      if (suppressErrorOutput) return;
      error.forAffectedDefinitions((referable, err) -> {
        if (referable instanceof LocatedReferable) {
          updateSourceResult(((LocatedReferable) referable).getLocation(), err.level);
        }
      });
      dispatchError(error);
    }
  };

  /**
   * Discards everything that belongs to the command just finished, so this context can serve
   * another one. Anything left behind is a diagnostic, an exit code or a target from the
   * previous command leaking into the next.
   *
   * <p>The reset lives here rather than at the call site: a field added to this class and
   * forgotten in a reset method somewhere else is a leak nobody would find.
   */
  public void beginCommand() {
    exitWithError = false;
    moduleResults.clear();
    suppressErrorOutput = false;
    requestedModules.clear();
  }

  public void updateSourceResult(ModuleLocation module, GeneralError.Level result) {
    if (module == null) return;
    GeneralError.Level prevResult = moduleResults.get(module);
    if (prevResult == null || result.ordinal() > prevResult.ordinal()) {
      moduleResults.put(module, result);
    }
  }

  /** Render one diagnostic to stdout/stderr, goal-aware. */
  private void dispatchError(GeneralError error) {
    if (error.level == GeneralError.Level.ERROR) exitWithError = true;
    PrettyPrinterConfigWithRenamer ppConfig = new PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE);
    if (error instanceof GoalError) {
      ppConfig.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE);
    }
    String errorText = error.getDoc(ppConfig).toString();
    finishProgressLine();
    if (error.isSevere()) {
      System.err.println(errorText);
      System.err.flush();
    } else {
      System.out.println(errorText);
      System.out.flush();
    }
  }

  /** Parse a "{@code Module.Path[:Def.Long.Name]}" string. Reports illegality via {@link #systemErrErrorReporter}. */
  public Pair<ModulePath, LongName> parseFullName(String fullName) {
    ModulePath modulePath;
    LongName longName = null;
    int index = fullName.indexOf(':');
    if (index >= 0) {
      longName = LongName.fromString(fullName.substring(index + 1));
      if (!FileUtils.isCorrectDefinitionName(longName)) {
        systemErrErrorReporter.report(FileUtils.illegalDefinitionName(longName.toString()));
        return null;
      }
      fullName = fullName.substring(0, index);
    }
    modulePath = ModulePath.fromString(fullName);
    if (!FileUtils.isCorrectModulePath(modulePath)) {
      systemErrErrorReporter.report(FileUtils.illegalModuleName(modulePath.toString()));
      return null;
    }
    return new Pair<>(modulePath, longName);
  }
}
