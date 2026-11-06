package org.arend.frontend.query.guide;

import org.arend.ext.module.ModulePath;
import org.arend.frontend.query.guide.ModuleDefinitions.Def;
import org.arend.frontend.query.guide.ModuleDefinitions.Resolution;
import org.arend.term.group.AccessModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Compares a guide with the library it describes, records the fingerprints later comparisons are
 * made against, and makes the repairs that need no judgment. Knows nothing about the CLI: it is
 * given the parsed pages and the definitions of every module, so it can be tested on its own.
 */
final class GuideChecker {
  /** What can be wrong with an entry. Failing problems make {@code -gc} exit with 1. */
  enum Problem {
    /** The entry names a definition the module no longer has. */
    GONE(true),
    /** The definition's statement changed since the entry was stamped. */
    SIGNATURE(true),
    /** A definition whose body is its meaning ({@code \func}, {@code \data}, ...) changed. */
    DEFINITION(true),
    /** The description mentions definitions of the module that changed or disappeared. */
    REVIEW(true),
    /** The entry was never stamped, so there is nothing to compare it with. */
    UNSTAMPED(true),
    /** The page describes a module that does not exist. */
    PAGE_GONE(true),
    /** The name matches several definitions of the module; it cannot be fingerprinted. */
    AMBIGUOUS(false),
    /** The page's module failed to parse; its entries could not be checked. */
    UNREADABLE(false),
    /** A public definition the module's page does not describe. */
    UNDOCUMENTED(false),
    /** A module with public definitions and no page. */
    NO_PAGE(false);

    final boolean failing;

    Problem(boolean failing) {
      this.failing = failing;
    }

    String label() {
      return name().replace('_', '-');
    }
  }

  /**
   * One problem.
   *
   * @param page            the page's path relative to the guide root, or null for {@link Problem#NO_PAGE}
   * @param line            1-based line on the page; 0 when not tied to a line
   * @param name            the name as written in the guide, or the definition's long name
   * @param detail          what changed, what to look at, or where the definition may have gone
   * @param signature       the definition's current signature, when it helps to rewrite the entry
   * @param previous        the signature recorded when the entry was stamped, when it changed
   * @param entry           the entry's description
   * @param defLine         the definition's line in the module's source; 0 when there is none
   * @param candidates      for a name that is gone or ambiguous: the definitions it may mean
   * @param changedMentions for {@link Problem#REVIEW}: the mentioned definitions that changed
   */
  record Finding(Problem problem, @Nullable String page, int line, String module, String name,
                 @Nullable String kind, String detail, @Nullable String signature,
                 @Nullable String previous, @Nullable String entry, int defLine,
                 List<Candidate> candidates, List<Candidate> changedMentions) {
    Finding(Problem problem, @Nullable String page, int line, String module, String name, @Nullable String kind,
            String detail, @Nullable String signature) {
      this(problem, page, line, module, name, kind, detail, signature, null, null, 0, List.of(), List.of());
    }
  }

  /** A definition offered as what a name may mean, with its signature (null when gone). */
  record Candidate(String module, String name, @Nullable String signature) {
    String qualified() {
      return module + ":" + name;
    }
  }

  /** Selects the modules (and, for stamping, the entries) a run applies to. Empty means everything. */
  record Scope(List<String> modulePrefixes, Set<String> exactModules, Set<String> entries) {
    static final Scope ALL = new Scope(List.of(), Set.of(), Set.of());

    /** Adds {@code page=} values: modules selected exactly, without their submodules. */
    Scope withPages(List<String> modules) {
      Set<String> exact = new LinkedHashSet<>(exactModules);
      exact.addAll(modules);
      return new Scope(modulePrefixes, exact, entries);
    }

    /**
     * Parses {@code in=} values: {@code A.B} selects the module and its submodules;
     * {@code A.B:Name} selects the module and, when stamping, only that entry.
     */
    static Scope parse(List<String> specs) {
      List<String> prefixes = new ArrayList<>();
      Set<String> entries = new LinkedHashSet<>();
      for (String spec : specs) {
        int colon = spec.indexOf(':');
        if (colon > 0) {
          prefixes.add(spec.substring(0, colon));
          entries.add(spec);
        } else {
          prefixes.add(spec);
        }
      }
      return new Scope(prefixes, Set.of(), entries);
    }

    boolean contains(ModulePath module) {
      if (modulePrefixes.isEmpty() && exactModules.isEmpty()) return true;
      String m = module.toString();
      if (exactModules.contains(m)) return true;
      for (String p : modulePrefixes) {
        if (m.equals(p) || m.startsWith(p + ".")) return true;
      }
      return false;
    }

    /** Whether stamping should (re)write the row {@code longName} of {@code module}'s page. */
    boolean stamps(ModulePath module, String longName) {
      if (!contains(module)) return false;
      if (entries.isEmpty()) return true;
      // An entry selection applies to its own module only; other selected modules are stamped whole.
      boolean moduleHasSelection = false;
      for (String e : entries) {
        if (e.startsWith(module + ":")) {
          moduleHasSelection = true;
          if (e.equals(module + ":" + longName)) return true;
        }
      }
      return !moduleHasSelection;
    }
  }

  /** The outcome of a check. */
  record Report(List<Finding> findings, EnumMap<Problem, Integer> counts, int pages, int entries) {
    int failing(boolean strict) {
      int n = 0;
      for (Map.Entry<Problem, Integer> e : counts.entrySet()) {
        Problem p = e.getKey();
        if (p.failing || strict && (p == Problem.UNDOCUMENTED || p == Problem.NO_PAGE)) n += e.getValue();
      }
      return n;
    }
  }

  /** The outcome of stamping. */
  record StampResult(int pages, int filesWritten, int rowsWritten, int rowsKept, int skipped, int dropped) {}

  private final Path myGuideRoot;
  private final List<GuidePage> myPages;
  private final Map<ModulePath, ModuleDefinitions> myModules;
  private final Set<ModulePath> myUnreadable;
  private final boolean myDocumentHelpers;
  // Built on first use: where a definition that vanished from its module may have gone.
  private Map<String, List<Located>> myBySigHash;
  private Map<String, List<Located>> myByShortName;

  record Located(ModulePath module, Def def) {
    @Override public String toString() { return module + ":" + def.longName(); }

    Candidate candidate() {
      return new Candidate(module.toString(), def.longName(), def.signature());
    }
  }

  /**
   * @param documentHelpers whether definitions in {@code \where} blocks are expected to be
   *                        documented too; by default only top-level definitions and class members
   *                        are, as the guide was written ("skip purely internal helpers")
   */
  GuideChecker(Path guideRoot, List<GuidePage> pages, Map<ModulePath, ModuleDefinitions> modules,
               Set<ModulePath> unreadable, boolean documentHelpers) {
    myGuideRoot = guideRoot;
    myPages = pages;
    myModules = modules;
    myUnreadable = unreadable;
    myDocumentHelpers = documentHelpers;
  }

  Map<ModulePath, ModuleDefinitions> modules() {
    return myModules;
  }

  Path guideRoot() {
    return myGuideRoot;
  }

  List<GuidePage> pages() {
    return myPages;
  }

  // ---- check --------------------------------------------------------------

  /**
   * @param listUndocumented whether {@link Problem#UNDOCUMENTED} and {@link Problem#NO_PAGE}
   *                         problems are listed; they are always counted
   */
  Report check(Scope scope, boolean listUndocumented) throws IOException {
    List<Finding> findings = new ArrayList<>();
    EnumMap<Problem, Integer> counts = new EnumMap<>(Problem.class);
    Set<ModulePath> paged = new HashSet<>();
    int pages = 0, entries = 0;

    for (GuidePage page : myPages) {
      if (!scope.contains(page.modulePath)) continue;
      pages++;
      paged.add(page.modulePath);
      String module = page.modulePath.toString();
      ModuleDefinitions md = myModules.get(page.modulePath);
      if (md == null) {
        boolean unreadable = myUnreadable.contains(page.modulePath);
        Problem p = unreadable ? Problem.UNREADABLE : Problem.PAGE_GONE;
        String detail;
        if (unreadable) {
          detail = "the module did not parse, so its " + page.entries.size() + " entries were not checked";
        } else {
          Map.Entry<ModulePath, Integer> best = mostOfItIsNowIn(page);
          int names = nameCount(page);
          detail = "the library has no module " + module + (best == null
              ? "; the page's " + names + " names describe nothing"
              : "; " + best.getValue() + " of the page's " + names + " names are now in " + best.getKey());
        }
        add(findings, counts, new Finding(p, page.relativePath, 1, module, module, null, detail, null));
        continue;
      }

      Map<String, FingerprintFile.Row> rows = FingerprintFile.read(FingerprintFile.pathFor(myGuideRoot, page));
      Set<Def> documented = Collections.newSetFromMap(new IdentityHashMap<>());
      for (GuidePage.Entry entry : page.entries) {
        String textHash = textHash(entry.text());
        for (String name : entry.names()) {
          entries++;
          Resolution r = md.resolve(name);
          if (r.isAmbiguous()) {
            List<Candidate> cands = new ArrayList<>();
            for (Def d : r.candidates()) cands.add(new Candidate(module, d.longName(), d.signature()));
            add(findings, counts, new Finding(Problem.AMBIGUOUS, page.relativePath, entry.line(), module, name, null,
                "matches " + describe(r.candidates()) + "; qualify the name", null, null, entry.text(), 0, cands, List.of()));
            continue;
          }
          if (r.def() == null) {
            Trace trace = trace(page.modulePath, name, rows);
            List<Candidate> cands = new ArrayList<>();
            for (Located l : trace.where()) cands.add(l.candidate());
            FingerprintFile.Row old = trace.row();
            add(findings, counts, new Finding(Problem.GONE, page.relativePath, entry.line(), module, name,
                old == null ? null : old.kind(), trace.describe(page.modulePath), null,
                old == null ? null : old.signature(), entry.text(), 0, cands, List.of()));
            continue;
          }
          Def def = r.def();
          documented.add(def);
          FingerprintFile.Row row = rows.get(def.longName());
          if (row == null) {
            add(findings, counts, new Finding(Problem.UNSTAMPED, page.relativePath, entry.line(), module,
                def.longName(), def.kindLabel(), "no fingerprint recorded; run `-gc stamp` once the entry is checked",
                def.signature(), null, entry.text(), def.line(), List.of(), List.of()));
            continue;
          }
          String edited = row.text().equals(textHash) ? ""
              : " (the entry was edited after it was stamped: if it is up to date now, stamp it)";
          if (!Objects.equals(row.sig(), def.sigHash())) {
            add(findings, counts, new Finding(Problem.SIGNATURE, page.relativePath, entry.line(), module,
                def.longName(), def.kindLabel(), "the statement changed since the entry was stamped" + edited,
                def.signature(), row.signature(), entry.text(), def.line(), List.of(), List.of()));
          } else if (!Objects.equals(row.def(), def.defHash())) {
            add(findings, counts, new Finding(Problem.DEFINITION, page.relativePath, entry.line(), module,
                def.longName(), def.kindLabel(), "the definition's body changed since the entry was stamped" + edited,
                def.signature(), null, entry.text(), def.line(), List.of(), List.of()));
          }
          List<Candidate> changed = changedMentions(row.mentions(), md, page.modulePath);
          if (!changed.isEmpty()) {
            add(findings, counts, new Finding(Problem.REVIEW, page.relativePath, entry.line(), module,
                def.longName(), def.kindLabel(), "the description mentions " + describeMentions(changed) + edited,
                def.signature(), null, entry.text(), def.line(), List.of(), changed));
          }
        }
      }

      FingerprintFile.Row overview = rows.get(FingerprintFile.OVERVIEW);
      if (overview == null) {
        if (!page.overview.isEmpty()) {
          add(findings, counts, new Finding(Problem.UNSTAMPED, page.relativePath, 1, module,
              FingerprintFile.OVERVIEW, null, "no fingerprint recorded for the page's prose", null,
              null, page.overview, 0, List.of(), List.of()));
        }
      } else {
        List<Candidate> changed = changedMentions(overview.mentions(), md, page.modulePath);
        if (!changed.isEmpty()) {
          add(findings, counts, new Finding(Problem.REVIEW, page.relativePath, 1, module,
              FingerprintFile.OVERVIEW, null, "the page's prose mentions " + describeMentions(changed), null,
              null, page.overview, 0, List.of(), changed));
        }
      }

      for (Def def : md.defs) {
        if (documented.contains(def) || !worthDocumenting(def)) continue;
        Finding f = new Finding(Problem.UNDOCUMENTED, page.relativePath, 0, module, def.longName(),
            def.kindLabel(), "line " + def.line() + " of the module", def.signature(),
            null, null, def.line(), List.of(), List.of());
        if (listUndocumented) add(findings, counts, f);
        else count(counts, Problem.UNDOCUMENTED);
      }
    }

    List<ModulePath> modules = new ArrayList<>(myModules.keySet());
    modules.sort(Comparator.comparing(ModulePath::toString));
    for (ModulePath mp : modules) {
      if (paged.contains(mp) || !scope.contains(mp)) continue;
      int n = 0;
      for (Def def : myModules.get(mp).defs) if (worthDocumenting(def)) n++;
      if (n == 0) continue;
      Finding f = new Finding(Problem.NO_PAGE, null, 0, mp.toString(), mp.toString(), null,
          n + " public definition" + (n == 1 ? "" : "s") + ", no page", null);
      if (listUndocumented) add(findings, counts, f);
      else count(counts, Problem.NO_PAGE);
    }
    return new Report(findings, counts, pages, entries);
  }

  // ---- stamp --------------------------------------------------------------

  /**
   * Records the current fingerprints of every entry in {@code scope}. Rows outside the scope
   * are kept as they are; rows of entries no longer on the page are dropped. Names that are gone
   * or ambiguous are skipped: there is nothing to record for them.
   */
  StampResult stamp(Scope scope) throws IOException {
    int pages = 0, filesWritten = 0, rowsWritten = 0, rowsKept = 0, skipped = 0, dropped = 0;
    for (GuidePage page : myPages) {
      if (!scope.contains(page.modulePath)) continue;
      ModuleDefinitions md = myModules.get(page.modulePath);
      if (md == null) {
        skipped += page.entries.size();
        continue;
      }
      pages++;
      Path file = FingerprintFile.pathFor(myGuideRoot, page);
      LinkedHashMap<String, FingerprintFile.Row> old = FingerprintFile.read(file);
      LinkedHashMap<String, FingerprintFile.Row> rows = new LinkedHashMap<>();
      for (GuidePage.Entry entry : page.entries) {
        Map<Def, String> resolved = resolveEntry(md, entry);
        Map<String, String> mentions = mentions(entry.text(), md, resolved.keySet());
        String textHash = textHash(entry.text());
        for (String name : entry.names()) {
          Def def = md.resolve(name).def();
          if (def == null) {
            skipped++;
            continue;
          }
          if (rows.containsKey(def.longName())) continue;
          FingerprintFile.Row keep = old.get(def.longName());
          boolean selected = scope.stamps(page.modulePath, def.longName());
          if (!selected) {
            // Outside the selection: keep what is recorded, and leave the unrecorded unstamped.
            if (keep != null) {
              rows.put(def.longName(), keep);
              rowsKept++;
            }
          } else {
            rows.put(def.longName(), new FingerprintFile.Row(def.longName(), def.kindLabel(), def.sigHash(),
                def.defHash(), textHash, mentions, def.signature()));
            rowsWritten++;
          }
        }
      }
      if (!page.overview.isEmpty()) {
        FingerprintFile.Row keep = old.get(FingerprintFile.OVERVIEW);
        if (!scope.stamps(page.modulePath, FingerprintFile.OVERVIEW)) {
          if (keep != null) {
            rows.put(FingerprintFile.OVERVIEW, keep);
            rowsKept++;
          }
        } else {
          rows.put(FingerprintFile.OVERVIEW, new FingerprintFile.Row(FingerprintFile.OVERVIEW, "-", null, null,
              textHash(page.overview), mentions(page.overview, md, Set.of()), null));
          rowsWritten++;
        }
      }
      for (String name : old.keySet()) if (!rows.containsKey(name)) dropped++;
      if (!rows.equals(old)) {
        FingerprintFile.write(file, page.modulePath.toString(), rows.values());
        filesWritten++;
      }
    }
    return new StampResult(pages, filesWritten, rowsWritten, rowsKept, skipped, dropped);
  }

  // ---- fix ----------------------------------------------------------------

  /** One repair, as reported. {@code page}/{@code line} say where it was made. */
  record FixAction(String action, String page, int line, String name, String detail) {}

  /** What a fix does, or (dry run) would do. */
  record FixResult(List<FixAction> actions, int rowsDropped, int pagesWritten, int pagesDeleted) {}

  /**
   * Makes the repairs that need no judgment:
   * <ul>
   *   <li>an entry naming only definitions that are gone without a trace is deleted; a name gone
   *       without a trace is dropped from an entry that names others too;</li>
   *   <li>an entry naming a definition now found, with the recorded statement and the same name,
   *       in exactly one other module is moved to that module's page;</li>
   *   <li>a page whose module is gone is renamed after the module most of its names are now in,
   *       merged into that module's page when it has one, or deleted when its names are nowhere;</li>
   *   <li>an ambiguous name is qualified when exactly one of the definitions it matches has been
   *       fingerprinted for that page;</li>
   *   <li>fingerprints of entries no longer on their page are dropped.</li>
   * </ul>
   * A moved entry carries no fingerprint: its new page is a new context, so it is to be confirmed
   * there. Everything uncertain -- a rename guess, a name found elsewhere with another statement --
   * is left for {@code -gc tasks}.
   */
  FixResult fix(Scope scope, boolean dryRun) throws IOException {
    List<FixAction> actions = new ArrayList<>();
    Map<String, GuidePage> pageByPath = new HashMap<>();
    for (GuidePage p : myPages) pageByPath.put(p.relativePath, p);

    // New content of every page that changes, keyed by its (possibly new) relative path, and the
    // pages to delete. Appended entries are collected separately and added at the end.
    Map<String, List<String>> content = new LinkedHashMap<>();
    Map<String, String> separators = new HashMap<>();
    Map<String, List<List<String>>> appends = new LinkedHashMap<>();
    Set<String> deleted = new LinkedHashSet<>();
    Map<String, String> renamed = new LinkedHashMap<>(); // old relative path -> new
    Map<String, Set<String>> rowsToDrop = new HashMap<>();

    for (GuidePage page : myPages) {
      if (!scope.contains(page.modulePath)) continue;
      ModuleDefinitions md = myModules.get(page.modulePath);
      if (md == null) {
        if (myUnreadable.contains(page.modulePath)) continue;
        fixGonePage(page, pageByPath, content, separators, appends, deleted, renamed, actions);
        continue;
      }

      Map<String, FingerprintFile.Row> rows = FingerprintFile.read(FingerprintFile.pathFor(myGuideRoot, page));
      List<String> lines = new ArrayList<>(page.lines);
      Map<String, List<List<String>>> outgoing = new LinkedHashMap<>();
      boolean changed = false;
      // Bottom-up, so earlier line numbers stay valid while later entries are cut.
      List<GuidePage.Entry> entries = new ArrayList<>(page.entries);
      Collections.reverse(entries);
      for (GuidePage.Entry entry : entries) {
        int first = entry.line() - 1;
        List<String> gone = new ArrayList<>();
        Located movedTo = null;
        boolean allGoneWithoutTrace = true;
        for (String name : entry.names()) {
          Resolution r = md.resolve(name);
          if (r.isAmbiguous()) {
            allGoneWithoutTrace = false;
            Def only = null;
            int n = 0;
            for (Def d : r.candidates()) if (rows.containsKey(d.longName())) { only = d; n++; }
            if (n == 1) {
              String line = GuidePage.withHeaderNameReplaced(lines.get(first), name, only.longName());
              if (line != null) {
                lines.set(first, line);
                changed = true;
                actions.add(new FixAction("QUALIFY", page.relativePath, entry.line(), name, "-> " + only.longName()));
              }
            }
            continue;
          }
          if (r.def() != null) {
            allGoneWithoutTrace = false;
            continue;
          }
          Trace t = trace(page.modulePath, name, rows);
          if (t.kind() == TraceKind.NONE) {
            gone.add(name);
          } else {
            allGoneWithoutTrace = false;
            if (t.kind() == TraceKind.MOVED && t.where().size() == 1 && entry.names().size() == 1) movedTo = t.where().getFirst();
          }
        }

        if (allGoneWithoutTrace && !entry.names().isEmpty()) {
          for (int i = entry.endLine() - 1; i >= first; i--) lines.remove(i);
          changed = true;
          rowsToDrop.computeIfAbsent(page.relativePath, k -> new HashSet<>()).addAll(entry.names());
          actions.add(new FixAction("DELETE", page.relativePath, entry.line(), String.join(", ", entry.names()),
              "gone without a trace: not in the module, and no definition of that name or recorded statement anywhere"));
          continue;
        }
        if (movedTo != null) {
          String name = entry.names().getFirst();
          List<String> moved = new ArrayList<>(lines.subList(first, entry.endLine()));
          if (!name.equals(movedTo.def().longName())) {
            String line = GuidePage.withHeaderNameReplaced(moved.getFirst(), name, movedTo.def().longName());
            if (line != null) moved.set(0, line);
          }
          for (int i = entry.endLine() - 1; i >= first; i--) lines.remove(i);
          changed = true;
          String target = GuidePage.relativePathOf(movedTo.module());
          outgoing.computeIfAbsent(target, k -> new ArrayList<>()).add(dedent(moved));
          separators.putIfAbsent(target, page.lineSeparator);
          rowsToDrop.computeIfAbsent(page.relativePath, k -> new HashSet<>()).add(name);
          actions.add(new FixAction("MOVE", page.relativePath, entry.line(), name,
              "-> " + target + " as " + movedTo.def().longName() + " (same statement, same name; confirm it there)"));
          continue;
        }
        for (String name : gone) {
          String line = GuidePage.withoutHeaderName(lines.get(first), name);
          if (line == null) continue;
          lines.set(first, line);
          changed = true;
          rowsToDrop.computeIfAbsent(page.relativePath, k -> new HashSet<>()).add(name);
          actions.add(new FixAction("UNNAME", page.relativePath, entry.line(), name,
              "gone without a trace; the entry's other names stay"));
        }
      }
      sendInPageOrder(outgoing, appends);
      if (changed) {
        content.put(page.relativePath, lines);
        separators.putIfAbsent(page.relativePath, page.lineSeparator);
      }
    }

    // Append moved and merged entries to their target pages, creating pages that do not exist.
    for (Map.Entry<String, List<List<String>>> e : appends.entrySet()) {
      String target = e.getKey();
      List<String> lines = content.get(target);
      if (lines == null) {
        GuidePage existing = pageByPath.get(target);
        if (existing != null && !deleted.contains(target)) {
          lines = new ArrayList<>(existing.lines);
        } else {
          String module = target.substring(0, target.length() - ".md".length()).replace('/', '.');
          lines = new ArrayList<>(List.of("### " + module, ""));
          actions.add(new FixAction("NEW-PAGE", target, 1, module, "created to receive entries moved here"));
        }
        content.put(target, lines);
      }
      while (!lines.isEmpty() && lines.getLast().isBlank()) lines.removeLast();
      lines.add("");
      for (List<String> entry : e.getValue()) lines.addAll(entry);
    }

    int pagesWritten = 0, pagesDeleted = 0, rowsDropped = 0;
    if (!dryRun) {
      for (Map.Entry<String, String> r : renamed.entrySet()) {
        FingerprintFile.move(FingerprintFile.pathFor(myGuideRoot, r.getKey()), FingerprintFile.pathFor(myGuideRoot, r.getValue()));
      }
      for (String rel : deleted) {
        if (content.containsKey(rel)) continue;
        Files.deleteIfExists(myGuideRoot.resolve(rel));
        if (!renamed.containsKey(rel)) Files.deleteIfExists(FingerprintFile.pathFor(myGuideRoot, rel));
        pagesDeleted++;
      }
      for (Map.Entry<String, List<String>> c : content.entrySet()) {
        Path file = myGuideRoot.resolve(c.getKey());
        Files.createDirectories(file.getParent());
        String sep = separators.getOrDefault(c.getKey(), "\n");
        Files.writeString(file, String.join(sep, c.getValue()) + sep, StandardCharsets.UTF_8);
        pagesWritten++;
      }
      rowsDropped = dropRows(rowsToDrop);
    }
    // Fingerprints of pages that no longer exist (deleted by hand, or by an agent) describe
    // nothing; a page that is still there, or is being written now, keeps its own.
    Path fingerprints = myGuideRoot.resolve(FingerprintFile.DIRECTORY);
    if (Files.isDirectory(fingerprints) && scope.modulePrefixes().isEmpty() && scope.exactModules().isEmpty()) {
      List<Path> files;
      try (var stream = Files.walk(fingerprints)) {
        files = stream.filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".tsv")).sorted().toList();
      }
      for (Path f : files) {
        String rel = fingerprints.relativize(f).toString().replace(java.io.File.separatorChar, '/');
        String page = rel.substring(0, rel.length() - ".tsv".length()) + ".md";
        boolean stays = (pageByPath.containsKey(page) && !deleted.contains(page)) || content.containsKey(page) || renamed.containsValue(page);
        if (stays) continue;
        actions.add(new FixAction("DROP-FINGERPRINTS", page, 0, page, "fingerprints of a page that no longer exists"));
        if (!dryRun && !renamed.containsKey(page)) Files.deleteIfExists(f);
      }
    }
    return new FixResult(actions, rowsDropped, pagesWritten, pagesDeleted);
  }

  /**
   * Handles a page whose module is gone. When most of its names are now in one module that has no
   * page, the page is renamed after it (whatever does not fit is reported at the next check).
   * Otherwise its entries are settled one by one: those whose names are in that module are merged
   * into its page, one whose definition moved alone goes to that definition's page, one whose
   * names are nowhere is dropped -- and the rest stay, for a person or an agent to place. The page
   * is deleted only when nothing is left on it.
   */
  private void fixGonePage(GuidePage page, Map<String, GuidePage> pageByPath, Map<String, List<String>> content,
                           Map<String, String> separators, Map<String, List<List<String>>> appends,
                           Set<String> deleted, Map<String, String> renamed, List<FixAction> actions) throws IOException {
    Map.Entry<ModulePath, Integer> best = mostOfItIsNowIn(page);
    ModulePath target = best == null ? null : best.getKey();
    String targetRel = target == null ? null : GuidePage.relativePathOf(target);
    if (target != null) {
      boolean targetHasPage = (pageByPath.containsKey(targetRel) && !deleted.contains(targetRel)) || content.containsKey(targetRel);
      if (!targetHasPage) {
        deleted.add(page.relativePath);
        List<String> lines = new ArrayList<>(page.lines);
        if (!lines.isEmpty() && lines.getFirst().startsWith("### ")) lines.set(0, "### " + target);
        content.put(targetRel, lines);
        separators.put(targetRel, page.lineSeparator);
        renamed.put(page.relativePath, targetRel);
        actions.add(new FixAction("MOVE-PAGE", page.relativePath, 1, page.modulePath.toString(),
            "-> " + targetRel + " (" + best.getValue() + " of its " + nameCount(page) + " names are in " + target
                + "; the rest are reported at the next check)"));
        return;
      }
    }

    Map<String, FingerprintFile.Row> rows = FingerprintFile.read(FingerprintFile.pathFor(myGuideRoot, page));
    ModuleDefinitions targetDefs = target == null ? null : myModules.get(target);
    List<String> lines = new ArrayList<>(page.lines);
    Map<String, List<List<String>>> outgoing = new LinkedHashMap<>();
    int merged = 0, moved = 0, dropped = 0, left = 0;
    List<GuidePage.Entry> entries = new ArrayList<>(page.entries);
    Collections.reverse(entries);
    for (GuidePage.Entry entry : entries) {
      int first = entry.line() - 1;
      List<String> text = dedent(new ArrayList<>(page.lines.subList(first, entry.endLine())));
      boolean inTarget = false;
      for (String name : entry.names()) if (targetDefs != null && targetDefs.resolve(name).def() != null) inTarget = true;
      String destination = null;
      boolean nowhere = !entry.names().isEmpty();
      if (inTarget) {
        destination = targetRel;
        merged++;
      } else {
        Located movedTo = null;
        for (String name : entry.names()) {
          Trace t = trace(page.modulePath, name, rows);
          if (t.kind() != TraceKind.NONE) nowhere = false;
          if (t.kind() == TraceKind.MOVED && t.where().size() == 1 && entry.names().size() == 1) movedTo = t.where().getFirst();
        }
        if (movedTo != null) {
          String name = entry.names().getFirst();
          if (!name.equals(movedTo.def().longName())) {
            String line = GuidePage.withHeaderNameReplaced(text.getFirst(), name, movedTo.def().longName());
            if (line != null) text.set(0, line);
          }
          destination = GuidePage.relativePathOf(movedTo.module());
          moved++;
        } else if (nowhere) {
          dropped++;
        } else {
          left++;
          continue;
        }
      }
      for (int i = entry.endLine() - 1; i >= first; i--) lines.remove(i);
      if (destination != null) {
        outgoing.computeIfAbsent(destination, k -> new ArrayList<>()).add(text);
        separators.putIfAbsent(destination, page.lineSeparator);
      }
    }
    sendInPageOrder(outgoing, appends);

    String summary = merged + " merged into " + (targetRel == null ? "-" : targetRel) + ", " + moved
        + " moved after their definitions, " + dropped + " naming nothing anywhere dropped";
    if (merged + moved + dropped == 0) return; // nothing to settle: the page waits for a person or an agent
    if (left == 0) {
      deleted.add(page.relativePath);
      actions.add(new FixAction(merged + moved == 0 ? "DELETE-PAGE" : "MERGE-PAGE", page.relativePath, 1,
          page.modulePath.toString(), "the module is gone: " + summary + "; nothing is left, so the page goes"));
    } else {
      content.put(page.relativePath, lines);
      separators.putIfAbsent(page.relativePath, page.lineSeparator);
      actions.add(new FixAction("THIN-PAGE", page.relativePath, 1, page.modulePath.toString(),
          "the module is gone: " + summary + "; " + left + " entries left to place by hand"));
    }
  }

  /**
   * Queues one page's outgoing entries on their destinations. A page is edited bottom-up, so they
   * were collected last-first; they go out in the order the page had them.
   */
  private static void sendInPageOrder(Map<String, List<List<String>>> outgoing, Map<String, List<List<String>>> appends) {
    for (Map.Entry<String, List<List<String>>> e : outgoing.entrySet()) {
      List<List<String>> list = new ArrayList<>(e.getValue());
      Collections.reverse(list);
      appends.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(list);
    }
  }

  /** Moved lines start a top-level entry on their new page, whatever their indentation was. */
  private static List<String> dedent(List<String> lines) {
    if (lines.isEmpty()) return lines;
    String first = lines.getFirst();
    int indent = 0;
    while (indent < first.length() && first.charAt(indent) == ' ') indent++;
    if (indent == 0) return lines;
    List<String> out = new ArrayList<>(lines.size());
    for (String l : lines) {
      int n = 0;
      while (n < indent && n < l.length() && l.charAt(n) == ' ') n++;
      out.add(l.substring(n));
    }
    return out;
  }

  /** Drops the fingerprints of names a fix removed from their pages. */
  private int dropRows(Map<String, Set<String>> rowsToDrop) throws IOException {
    int n = 0;
    for (Map.Entry<String, Set<String>> e : rowsToDrop.entrySet()) {
      Path file = FingerprintFile.pathFor(myGuideRoot, e.getKey());
      LinkedHashMap<String, FingerprintFile.Row> rows = FingerprintFile.read(file);
      if (rows.isEmpty()) continue;
      int before = rows.size();
      rows.keySet().removeIf(key -> {
        for (String name : e.getValue()) if (key.equals(name) || key.endsWith("." + name)) return true;
        return false;
      });
      if (rows.size() != before) {
        n += before - rows.size();
        String module = e.getKey().substring(0, e.getKey().length() - ".md".length()).replace('/', '.');
        FingerprintFile.write(file, module, rows.values());
      }
    }
    return n;
  }

  // ---- traces of vanished definitions --------------------------------------

  enum TraceKind {
    /** Nothing anywhere: no definition of that name, and none with the recorded statement. */
    NONE,
    /** The recorded statement under the same name is now in another module (or several). */
    MOVED,
    /** The recorded statement is now under another name (a guess when several match). */
    RENAMED,
    /** A definition of that name exists elsewhere, with another (or no recorded) statement. */
    SAME_NAME
  }

  /** Where a vanished definition may be, with the fingerprint row it was recorded under, if any. */
  record Trace(TraceKind kind, List<Located> where, @Nullable FingerprintFile.Row row) {
    String describe(ModulePath module) {
      String list = list(where);
      return switch (kind) {
        case NONE -> "not in " + module + ", and nothing of that name or recorded statement is elsewhere in the library";
        case MOVED -> "not in " + module + "; the recorded statement, under the same name, is now " + list;
        case RENAMED -> "not in " + module + "; renamed? the recorded statement is now " + list;
        case SAME_NAME -> "not in " + module + "; a definition of that name is in " + list;
      };
    }
  }

  /**
   * Where a definition named on {@code module}'s page may have gone. A statement shared by many
   * definitions ({@code \func _ : \Prop}, a record with no fields) says nothing, so only a
   * near-unique match counts as a rename.
   */
  Trace trace(ModulePath module, String name, Map<String, FingerprintFile.Row> rows) {
    buildGlobalIndexes();
    FingerprintFile.Row old = rows.get(name);
    if (old == null) {
      for (FingerprintFile.Row r : rows.values()) {
        if (r.name().endsWith("." + name)) {
          old = r;
          break;
        }
      }
    }
    String shortName = name.substring(name.lastIndexOf('.') + 1);
    if (old != null && old.sig() != null) {
      List<Located> same = myBySigHash.getOrDefault(old.sig(), List.of());
      List<Located> sameName = new ArrayList<>();
      for (Located l : same) if (l.def().shortName().equals(shortName)) sameName.add(l);
      if (!sameName.isEmpty()) return new Trace(TraceKind.MOVED, sameName, old);
      if (!same.isEmpty() && same.size() <= 3) return new Trace(TraceKind.RENAMED, same, old);
    }
    // A definition of that name under another parent counts too, in this module as well: a class
    // renamed takes its members' long names with it.
    List<Located> named = new ArrayList<>();
    for (Located l : myByShortName.getOrDefault(shortName, List.of())) {
      if (!(l.module().equals(module) && l.def().longName().equals(name))) named.add(l);
    }
    if (!named.isEmpty()) return new Trace(TraceKind.SAME_NAME, named, old);
    return new Trace(TraceKind.NONE, List.of(), old);
  }

  /**
   * For a page whose module is gone: the module most of the page's names are now found in, and
   * how many, when one holds at least half of them and at least three -- the usual trace of a
   * module renamed or merged into another.
   */
  private Map.@Nullable Entry<ModulePath, Integer> mostOfItIsNowIn(GuidePage page) {
    buildGlobalIndexes();
    Map<ModulePath, Integer> hits = new HashMap<>();
    for (GuidePage.Entry entry : page.entries) {
      for (String name : entry.names()) {
        Set<ModulePath> seen = new HashSet<>();
        String shortName = name.substring(name.lastIndexOf('.') + 1);
        for (Located l : myByShortName.getOrDefault(shortName, List.of())) {
          String ln = l.def().longName();
          if ((ln.equals(name) || ln.endsWith("." + name)) && seen.add(l.module())) hits.merge(l.module(), 1, Integer::sum);
        }
      }
    }
    Map.Entry<ModulePath, Integer> best = null;
    for (Map.Entry<ModulePath, Integer> e : hits.entrySet()) {
      if (best == null || e.getValue() > best.getValue()
          || e.getValue().equals(best.getValue()) && e.getKey().toString().compareTo(best.getKey().toString()) < 0) {
        best = e;
      }
    }
    // A couple of names in common is a coincidence (`map`, `compose` are everywhere), not a trace.
    return best != null && best.getValue() >= 3 && 2 * best.getValue() >= nameCount(page) ? best : null;
  }

  private static int nameCount(GuidePage page) {
    int n = 0;
    for (GuidePage.Entry entry : page.entries) n += entry.names().size();
    return n;
  }

  private void buildGlobalIndexes() {
    if (myBySigHash != null) return;
    myBySigHash = new HashMap<>();
    myByShortName = new HashMap<>();
    for (Map.Entry<ModulePath, ModuleDefinitions> e : myModules.entrySet()) {
      for (Def def : e.getValue().defs) {
        Located l = new Located(e.getKey(), def);
        if (def.sigHash() != null) myBySigHash.computeIfAbsent(def.sigHash(), k -> new ArrayList<>()).add(l);
        myByShortName.computeIfAbsent(def.shortName(), k -> new ArrayList<>()).add(l);
      }
    }
  }

  // ---- helpers ------------------------------------------------------------

  /** The definitions an entry's header resolves to, mapped to the name they were written as. */
  private static Map<Def, String> resolveEntry(ModuleDefinitions md, GuidePage.Entry entry) {
    Map<Def, String> resolved = new IdentityHashMap<>();
    for (String name : entry.names()) {
      Def def = md.resolve(name).def();
      if (def != null) resolved.putIfAbsent(def, name);
    }
    return resolved;
  }

  /**
   * The definitions of the module that {@code text} mentions in code spans, other than the ones it
   * describes, with their mention hashes.
   */
  private static Map<String, String> mentions(String text, ModuleDefinitions md, Set<Def> own) {
    Map<String, String> result = new TreeMap<>();
    for (String token : GuidePage.codeTokens(text)) {
      Def def = md.resolve(token).def();
      if (def != null && !own.contains(def)) result.putIfAbsent(def.longName(), def.mentionHash());
    }
    return result;
  }

  /** The recorded mentions that changed (with their signature now) or disappeared (with none). */
  private static List<Candidate> changedMentions(Map<String, String> recorded, ModuleDefinitions md, ModulePath module) {
    List<Candidate> changed = new ArrayList<>();
    for (Map.Entry<String, String> m : recorded.entrySet()) {
      Def def = md.byLongName(m.getKey());
      if (def == null) changed.add(new Candidate(module.toString(), m.getKey(), null));
      else if (!def.mentionHash().equals(m.getValue())) changed.add(new Candidate(module.toString(), m.getKey(), def.signature()));
    }
    return changed;
  }

  private static String describeMentions(List<Candidate> changed) {
    List<String> c = new ArrayList<>(), g = new ArrayList<>();
    for (Candidate m : changed) (m.signature() == null ? g : c).add(m.name());
    StringBuilder sb = new StringBuilder();
    if (!c.isEmpty()) sb.append(quoted(c)).append(", which changed");
    if (!g.isEmpty()) {
      if (!sb.isEmpty()) sb.append("; and ");
      sb.append(quoted(g)).append(g.size() == 1 ? ", which no longer exists" : ", which no longer exist");
    }
    return sb.toString();
  }

  private static String textHash(String text) {
    return Fingerprints.hash(Fingerprints.normalize(text), Fingerprints.MENTION_HASH_LENGTH);
  }

  /** Whether a definition belongs on its module's page: public, and not a part of another one. */
  boolean worthDocumenting(Def def) {
    if (def.access() == AccessModifier.PRIVATE) return false;
    if (def.role() == ModuleDefinitions.Role.HELPER && !myDocumentHelpers) return false;
    return switch (def.kind()) {
      case FIELD, CONSTRUCTOR, COCLAUSE, LEVEL, COERCE, OTHER -> false;
      default -> true;
    };
  }

  private static String list(List<Located> located) {
    List<String> names = new ArrayList<>();
    for (Located l : located) names.add(l.toString());
    Collections.sort(names);
    if (names.size() > 3) return String.join(", ", names.subList(0, 3)) + " and " + (names.size() - 3) + " more";
    return String.join(", ", names);
  }

  private static String describe(List<Def> candidates) {
    List<String> names = new ArrayList<>();
    for (Def d : candidates) names.add(d.longName());
    int more = names.size() - Math.min(4, names.size());
    return String.join(", ", names.size() > 4 ? names.subList(0, 4) : names) + (more > 0 ? " and " + more + " more" : "");
  }

  private static String quoted(List<String> names) {
    List<String> q = new ArrayList<>();
    for (String n : names.size() > 4 ? names.subList(0, 4) : names) q.add("`" + n + "`");
    String s = String.join(", ", q);
    return names.size() > 4 ? s + " and " + (names.size() - 4) + " more" : s;
  }

  private static void add(List<Finding> findings, EnumMap<Problem, Integer> counts, @NotNull Finding f) {
    findings.add(f);
    count(counts, f.problem());
  }

  private static void count(EnumMap<Problem, Integer> counts, Problem p) {
    counts.merge(p, 1, Integer::sum);
  }
}
