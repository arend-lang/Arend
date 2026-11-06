package org.arend.frontend.query.guide;

import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.ConsoleHelp;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.query.ConsoleQueryTool;
import org.arend.frontend.query.JsonUtils;
import org.arend.frontend.query.QueryArgs;
import org.arend.frontend.query.QueryOptions;
import org.arend.frontend.source.StreamRawSource;
import org.arend.repl.Repl;
import org.arend.server.ArendServer;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The {@code -gc} tool: checks a library's guide -- the per-module markdown pages in
 * {@code <library>/.aiGuide} -- against the library's source, and records the fingerprints the
 * check compares with ({@code -gc stamp}). See {@link GuideChecker} for what is compared and
 * {@link ConsoleHelp#guideCheckHelp()} for the grammar.
 */
public final class GuideCheckTool extends ConsoleQueryTool {
  /** Singleton shared by the CLI (its {@code -gc} flag) and the REPL (its {@code :gc} command). */
  public static final GuideCheckTool INSTANCE = new GuideCheckTool();

  /** The guide's directory, relative to the library root (the directory of {@code arend.yaml}). */
  public static final String DEFAULT_GUIDE_DIRECTORY = ".aiGuide";

  private GuideCheckTool() {}

  @Override public String shortName() { return "gc"; }

  @Override public String longName() { return "guide-check"; }

  @Override public String argName() { return "stamp|option"; }

  @Override public boolean argumentsOptional() { return true; }

  @Override public String cliDescription() {
    return "check the library's .aiGuide pages against its source, or record their fingerprints (`stamp`). Pass `-gc --help` for full grammar.";
  }

  @Override public void printHelp() { ConsoleHelp.printGuideCheck(); }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Check the library guide against the source, or stamp it (`:? gc` for the full grammar)";
  }

  @Override
  public @Nls @NotNull String help(@NotNull Repl api) {
    return ConsoleHelp.guideCheckHelp();
  }

  @Override protected String replLabel() { return "Guide check"; }

  // ---- options ------------------------------------------------------------

  /** What a run does. */
  public enum Action { CHECK, STAMP, FIX, TASKS }

  public static final class Options extends QueryOptions {
    public Action action = Action.CHECK;
    public boolean listUndocumented = false;
    public boolean strict = false;
    public boolean dryRun = false;
    public boolean helpers = false;
    public @Nullable String dir = null;
    public @Nullable String out = null;
    public final List<String> scope = new ArrayList<>();
    public final List<String> pages = new ArrayList<>();
  }

  @Override
  public @Nullable ToolRunner parseArgs(String[] args) {
    Options opts = new Options();
    boolean ok = new QueryArgs.Tokens()
        .flag("check", () -> opts.action = Action.CHECK)
        .flag("stamp", () -> opts.action = Action.STAMP)
        .flag("fix", () -> opts.action = Action.FIX)
        .flag("tasks", () -> opts.action = Action.TASKS)
        .flag("dry-run", () -> opts.dryRun = true)
        .flag("undocumented", () -> opts.listUndocumented = true)
        .flag("strict", () -> opts.strict = true)
        .flag("helpers", () -> opts.helpers = true)
        .param("dir", v -> opts.dir = v)
        .param("out", v -> opts.out = v)
        .param("in", v -> {
          for (String s : v.split(",")) if (!s.isBlank()) opts.scope.add(s.trim());
        })
        .param("page", v -> {
          for (String s : v.split(",")) if (!s.isBlank()) opts.pages.add(s.trim());
        })
        .self(opts)
        .limit(opts)
        .parse("-gc", args == null ? new String[0] : args);
    if (ok && opts.dryRun && opts.action != Action.FIX) {
      System.err.println("[ERROR] -gc dry-run only applies to fix.");
      return null;
    }
    if (ok && opts.out != null && opts.action != Action.TASKS) {
      System.err.println("[ERROR] -gc out= only applies to tasks.");
      return null;
    }
    return ok ? new ToolRunner(opts) : null;
  }

  // ---- run ------------------------------------------------------------------

  public record ToolRunner(Options options) implements ConsoleToolRunner {
    @Override public int run(QueryContext ctx) {
      ctx.applyTo(options);
      List<SourceLibrary> libraries = new ArrayList<>();
      for (SourceLibrary lib : ctx.requestedLibraries()) {
        if (!ctx.excludeLibraries().contains(lib.getLibraryName())) libraries.add(lib);
      }
      if (libraries.isEmpty()) {
        System.err.println("[ERROR] -gc: no library to check.");
        return 1;
      }
      if (options.dir != null && libraries.size() > 1) {
        System.err.println("[ERROR] -gc: dir= names one guide, but " + libraries.size() + " libraries are requested.");
        return 1;
      }

      List<String> jsonParts = new ArrayList<>();
      int exit = 0;
      int checked = 0;
      for (SourceLibrary lib : libraries) {
        Path root = guideRoot(ctx, lib);
        if (root == null || !Files.isDirectory(root)) {
          if (options.dir != null || libraries.size() == 1) {
            System.err.println("[ERROR] -gc: no guide for library " + lib.getLibraryName()
                + (root == null ? "" : " at " + root) + ".");
            exit = 1;
          }
          continue;
        }
        checked++;
        try {
          exit |= runOne(ctx, lib, root, jsonParts);
        } catch (IOException e) {
          System.err.println("[ERROR] -gc: " + e.getMessage());
          exit = 1;
        }
      }
      if (options.json) {
        ctx.out().println(jsonParts.size() == 1 ? jsonParts.getFirst() : "[" + String.join(",", jsonParts) + "]");
      }
      if (checked == 0 && exit == 0) {
        System.err.println("[ERROR] -gc: none of the requested libraries has a " + DEFAULT_GUIDE_DIRECTORY + " directory.");
        return 1;
      }
      return exit;
    }

    private @Nullable Path guideRoot(QueryContext ctx, SourceLibrary lib) {
      if (options.dir != null) return ctx.resolvePath(options.dir);
      if (lib instanceof FileSourceLibrary f && f.getSourceBasePath() != null) {
        Path libRoot = f.getSourceBasePath().toAbsolutePath().normalize().getParent();
        return libRoot == null ? null : libRoot.resolve(DEFAULT_GUIDE_DIRECTORY);
      }
      return null;
    }

    private int runOne(QueryContext ctx, SourceLibrary lib, Path root, List<String> jsonParts) throws IOException {
      List<GuidePage> pages = GuidePage.readAll(root);
      Map<ModulePath, ModuleDefinitions> modules = new HashMap<>();
      Set<ModulePath> unreadable = new HashSet<>();
      collectModules(ctx.server(), lib, modules, unreadable);
      GuideChecker checker = new GuideChecker(root, pages, modules, unreadable, options.helpers);
      GuideChecker.Scope scope = GuideChecker.Scope.parse(options.scope).withPages(options.pages);

      if (options.action == Action.FIX) {
        GuideChecker.FixResult r = checker.fix(scope, options.dryRun);
        printFix(ctx, lib, root, r, jsonParts);
        return 0;
      }

      if (options.action == Action.TASKS) {
        GuideChecker.Report report = checker.check(scope, true);
        List<GuideTasks.Packet> packets = GuideTasks.build(checker, report, mp -> sourceFile(lib, mp));
        printTasks(ctx, root, packets, jsonParts);
        return 0;
      }

      if (options.action == Action.STAMP) {
        GuideChecker.StampResult r = checker.stamp(scope);
        if (options.json) {
          jsonParts.add("{\"library\":" + JsonUtils.str(lib.getLibraryName()) + ",\"guide\":" + JsonUtils.str(root.toString())
              + ",\"stamped\":{\"pages\":" + r.pages() + ",\"filesWritten\":" + r.filesWritten()
              + ",\"rowsWritten\":" + r.rowsWritten() + ",\"rowsKept\":" + r.rowsKept()
              + ",\"skipped\":" + r.skipped() + ",\"dropped\":" + r.dropped() + "}}");
        } else {
          PrintStream out = ctx.out();
          out.println("Stamped " + r.rowsWritten() + " entr" + (r.rowsWritten() == 1 ? "y" : "ies") + " on "
              + r.pages() + " page" + (r.pages() == 1 ? "" : "s") + " of " + root
              + " (" + r.filesWritten() + " fingerprint file" + (r.filesWritten() == 1 ? "" : "s") + " written"
              + (r.rowsKept() > 0 ? ", " + r.rowsKept() + " rows outside the selection kept" : "")
              + (r.dropped() > 0 ? ", " + r.dropped() + " rows of removed entries dropped" : "") + ").");
          if (r.skipped() > 0) {
            out.println(r.skipped() + " name" + (r.skipped() == 1 ? " was" : "s were")
                + " not stamped: gone, ambiguous, or on a page whose module is missing. Run `-gc` to list them.");
          }
        }
        return 0;
      }

      GuideChecker.Report report = checker.check(scope, options.listUndocumented);
      int failing = report.failing(options.strict);
      if (options.json) jsonParts.add(json(lib, root, report, failing));
      else printText(ctx.out(), ctx.workingDirectory(), lib, root, report, modules.size());
      return failing > 0 ? 1 : 0;
    }

    private static void collectModules(ArendServer server, SourceLibrary lib,
                                       Map<ModulePath, ModuleDefinitions> modules, Set<ModulePath> unreadable) {
      String name = lib.getLibraryName();
      for (ModulePath mp : lib.findModules(false)) {
        Path file = sourceFile(lib, mp);
        String stamp = fileStamp(file);
        CachedModule cached = stamp == null ? null : PARSED.get(file);
        if (cached != null && cached.stamp().equals(stamp)) {
          modules.put(mp, cached.defs());
          continue;
        }
        ConcreteGroup group;
        try {
          // Parse afresh rather than take the server's group: on a warm server (a daemon, the
          // REPL) that group has been through name resolution, which rewrites references in place
          // and changes how some of them print -- and fingerprints must not depend on whether a
          // daemon was used. Syntax errors are not -gc's to report; the typecheck reports them.
          group = lib.getSource(mp, false) instanceof StreamRawSource source
              ? source.loadGroup(error -> {})
              : server.getRawGroup(new ModuleLocation(name, ModuleLocation.LocationKind.SOURCE, mp));
        } catch (RuntimeException e) {
          // The parser can still throw on some malformed input after reporting the syntax error.
          // One module that cannot be read must not cost the report on all the others.
          System.err.println("[WARN] -gc: could not read module " + mp + ": " + e);
          group = null;
        }
        if (group == null) {
          unreadable.add(mp);
        } else {
          ModuleDefinitions defs = ModuleDefinitions.collect(mp, group);
          modules.put(mp, defs);
          if (stamp != null) PARSED.put(file, new CachedModule(stamp, defs));
        }
      }
    }

    /**
     * What a module parsed to, by source file, for as long as this process lives: a daemon serves
     * every -gc request from here and parses only the files that changed since. Only the
     * fingerprints are kept, never the group, so nothing a later resolution does can reach them.
     */
    private record CachedModule(String stamp, ModuleDefinitions defs) {}

    private static final Map<Path, CachedModule> PARSED = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The file's size and content checksum, or null when it cannot be read. Content, not mtime:
     * reading a file costs far less than parsing it, and an edit within one clock tick that keeps
     * the size would otherwise go unseen.
     */
    private static @Nullable String fileStamp(@Nullable Path file) {
      if (file == null) return null;
      try {
        byte[] bytes = Files.readAllBytes(file);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bytes);
        return bytes.length + ":" + Long.toHexString(crc.getValue());
      } catch (IOException e) {
        return null;
      }
    }

    private static @Nullable Path sourceFile(SourceLibrary lib, ModulePath mp) {
      if (!(lib instanceof FileSourceLibrary f) || f.getSourceBasePath() == null) return null;
      return f.getSourceBasePath().toAbsolutePath().normalize().resolve(String.join("/", mp.toList()) + ".ard");
    }

    // ---- output -------------------------------------------------------------

    private void printFix(QueryContext ctx, SourceLibrary lib, Path root, GuideChecker.FixResult r, List<String> jsonParts) {
      Map<String, Integer> counts = new TreeMap<>();
      for (GuideChecker.FixAction a : r.actions()) counts.merge(a.action(), 1, Integer::sum);
      if (options.json) {
        StringBuilder sb = new StringBuilder("{\"library\":").append(JsonUtils.str(lib.getLibraryName()))
            .append(",\"guide\":").append(JsonUtils.str(root.toString()))
            .append(",\"dryRun\":").append(options.dryRun)
            .append(",\"pagesWritten\":").append(r.pagesWritten())
            .append(",\"pagesDeleted\":").append(r.pagesDeleted())
            .append(",\"rowsDropped\":").append(r.rowsDropped())
            .append(",\"actions\":[");
        boolean first = true;
        for (GuideChecker.FixAction a : r.actions()) {
          if (!first) sb.append(',');
          first = false;
          sb.append("{\"action\":").append(JsonUtils.str(a.action())).append(",\"page\":").append(JsonUtils.str(a.page()))
              .append(",\"line\":").append(a.line()).append(",\"name\":").append(JsonUtils.str(a.name()))
              .append(",\"detail\":").append(JsonUtils.str(a.detail())).append('}');
        }
        jsonParts.add(sb.append("]}").toString());
        return;
      }
      PrintStream out = ctx.out();
      Path cwd = ctx.workingDirectory();
      int shown = options.capped(r.actions().size());
      for (int i = 0; i < shown; i++) {
        GuideChecker.FixAction a = r.actions().get(i);
        out.println(display(cwd, root.resolve(a.page())) + ":" + a.line() + ": " + a.action() + " " + a.name() + "\n    " + a.detail());
      }
      if (shown < r.actions().size()) out.println("... " + options.truncationNote(shown, r.actions().size()));
      List<String> parts = new ArrayList<>();
      for (Map.Entry<String, Integer> c : counts.entrySet()) parts.add(c.getValue() + " " + c.getKey());
      out.println((options.dryRun ? "Would make " : "Made ") + r.actions().size() + " repair" + (r.actions().size() == 1 ? "" : "s")
          + (parts.isEmpty() ? "" : ": " + String.join(", ", parts))
          + (options.dryRun ? " (dry run: nothing written)." : "; " + r.pagesWritten() + " pages written, "
              + r.pagesDeleted() + " deleted, " + r.rowsDropped() + " fingerprints dropped."));
    }

    private void printTasks(QueryContext ctx, Path root, List<GuideTasks.Packet> packets, List<String> jsonParts) throws IOException {
      Map<String, Integer> totals = new TreeMap<>();
      for (GuideTasks.Packet p : packets) for (Map.Entry<String, Integer> c : p.counts().entrySet()) totals.merge(c.getKey(), c.getValue(), Integer::sum);
      if (options.out != null) {
        Path dir = ctx.resolvePath(options.out);
        GuideTasks.writeAll(packets, root, dir);
        if (options.json) {
          jsonParts.add("{\"guide\":" + JsonUtils.str(root.toString()) + ",\"out\":" + JsonUtils.str(dir.toString())
              + ",\"packets\":" + packets.size() + "}");
          return;
        }
        ctx.out().println("Wrote " + packets.size() + " packets to " + display(ctx.workingDirectory(), dir) + " (index.json lists them).");
      } else if (options.json) {
        StringBuilder sb = new StringBuilder("{\"guide\":").append(JsonUtils.str(root.toString())).append(",\"packets\":[");
        boolean first = true;
        for (GuideTasks.Packet p : packets) {
          if (!first) sb.append(',');
          first = false;
          sb.append(GuideTasks.json(p, root));
        }
        jsonParts.add(sb.append("]}").toString());
        return;
      } else {
        PrintStream out = ctx.out();
        int shown = options.capped(packets.size());
        for (int i = 0; i < shown; i++) {
          GuideTasks.Packet p = packets.get(i);
          List<String> parts = new ArrayList<>();
          for (Map.Entry<String, Integer> c : p.counts().entrySet()) parts.add(c.getValue() + " " + c.getKey());
          out.println(p.page() + (p.newPage() ? " (new page)" : "") + ": " + String.join(", ", parts));
        }
        if (shown < packets.size()) out.println("... " + options.truncationNote(shown, packets.size()));
      }
      List<String> parts = new ArrayList<>();
      for (Map.Entry<String, Integer> c : totals.entrySet()) parts.add(c.getValue() + " " + c.getKey());
      ctx.out().println("Total: " + packets.size() + " pages, " + String.join(", ", parts) + ".");
    }

    private void printText(PrintStream out, Path cwd, SourceLibrary lib, Path root, GuideChecker.Report report,
                           int moduleCount) {
      out.println("Guide " + display(cwd, root) + ": " + report.pages() + " page" + (report.pages() == 1 ? "" : "s")
          + ", " + report.entries() + " entr" + (report.entries() == 1 ? "y" : "ies") + ", checked against "
          + moduleCount + " modules of " + lib.getLibraryName() + ".");
      int shown = options.capped(report.findings().size());
      for (int i = 0; i < shown; i++) {
        GuideChecker.Finding f = report.findings().get(i);
        StringBuilder sb = new StringBuilder();
        sb.append(f.page() == null ? f.module() : display(cwd, root.resolve(f.page())) + (f.line() > 0 ? ":" + f.line() : ""));
        sb.append(": ").append(f.problem().label()).append(' ').append(f.name());
        if (f.kind() != null) sb.append(" [").append(f.kind()).append(']');
        sb.append("\n    ").append(f.detail());
        if (f.previous() != null && !f.previous().isEmpty()) sb.append("\n    was: ").append(oneLine(f.previous()));
        if (f.signature() != null && !f.signature().isEmpty()) sb.append("\n    now: ").append(oneLine(f.signature()));
        out.println(sb);
      }
      if (shown < report.findings().size()) {
        out.println("... " + (report.findings().size() - shown) + " more" + options.truncationNote(shown, report.findings().size()));
      }

      List<String> failingParts = new ArrayList<>(), otherParts = new ArrayList<>();
      for (GuideChecker.Problem p : GuideChecker.Problem.values()) {
        int n = report.counts().getOrDefault(p, 0);
        if (n == 0) continue;
        boolean fails = p.failing || options.strict && (p == GuideChecker.Problem.UNDOCUMENTED || p == GuideChecker.Problem.NO_PAGE);
        (fails ? failingParts : otherParts).add(n + " " + p.label());
      }
      StringBuilder summary = new StringBuilder("Summary: ");
      summary.append(failingParts.isEmpty() ? "the guide is up to date" : String.join(", ", failingParts) + " to fix");
      if (!otherParts.isEmpty()) summary.append("; also ").append(String.join(", ", otherParts));
      boolean hidden = !options.listUndocumented && (report.counts().containsKey(GuideChecker.Problem.UNDOCUMENTED)
          || report.counts().containsKey(GuideChecker.Problem.NO_PAGE));
      if (hidden) summary.append(" (pass `undocumented` to list those)");
      summary.append('.');
      out.println(summary);
    }

    private static String json(SourceLibrary lib, Path root, GuideChecker.Report report, int failing) {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"library\":").append(JsonUtils.str(lib.getLibraryName()))
          .append(",\"guide\":").append(JsonUtils.str(root.toString()))
          .append(",\"pages\":").append(report.pages())
          .append(",\"entries\":").append(report.entries())
          .append(",\"failing\":").append(failing)
          .append(",\"counts\":{");
      boolean first = true;
      for (Map.Entry<GuideChecker.Problem, Integer> e : report.counts().entrySet()) {
        if (!first) sb.append(',');
        sb.append(JsonUtils.str(e.getKey().label())).append(':').append(e.getValue());
        first = false;
      }
      sb.append("},\"findings\":[");
      first = true;
      for (GuideChecker.Finding f : report.findings()) {
        if (!first) sb.append(',');
        first = false;
        sb.append("{\"problem\":").append(JsonUtils.str(f.problem().label()))
            .append(",\"failing\":").append(f.problem().failing);
        if (f.page() != null) {
          sb.append(",\"page\":").append(JsonUtils.str(f.page()))
              .append(",\"file\":").append(JsonUtils.str(root.resolve(f.page()).toString()))
              .append(",\"line\":").append(f.line());
        }
        sb.append(",\"module\":").append(JsonUtils.str(f.module()))
            .append(",\"name\":").append(JsonUtils.str(f.name()));
        if (f.kind() != null) sb.append(",\"kind\":").append(JsonUtils.str(f.kind()));
        sb.append(",\"detail\":").append(JsonUtils.str(f.detail()));
        if (f.previous() != null && !f.previous().isEmpty()) sb.append(",\"was\":").append(JsonUtils.str(f.previous()));
        if (f.signature() != null && !f.signature().isEmpty()) sb.append(",\"signature\":").append(JsonUtils.str(f.signature()));
        sb.append('}');
      }
      return sb.append("]}").toString();
    }

    /** {@code path} relative to the user's working directory when it is inside it, so it can be clicked. */
    private static String display(Path cwd, Path path) {
      Path abs = path.toAbsolutePath().normalize();
      return abs.startsWith(cwd) ? cwd.relativize(abs).toString() : abs.toString();
    }

    private static String oneLine(String s) {
      return Fingerprints.normalize(s);
    }
  }
}
