package org.arend.frontend.symbol;

import org.arend.error.DummyErrorReporter;
import org.arend.error.SourcePosition;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.naming.error.NotInScopeError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.server.ArendServer;
import org.arend.server.RawAnchor;
import org.arend.server.impl.SingleFileReferenceResolver;
import org.arend.server.modifier.RawImportAdder;
import org.arend.server.modifier.RawModifier;
import org.arend.server.modifier.RawSequenceModifier;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

/**
 * Name-resolution suggestion engine used by {@code -ai}'s step 1: takes the
 * buffered name-resolution errors, looks up unresolved short names in the
 * per-library binary symbol indices, and prints a candidate list (qualified
 * name + required imports) the user can paste back into the source. Never
 * rewrites sources — an earlier auto-fix version did and was prone to
 * mangling identifiers when positions or replacement lengths got out of sync,
 * so the unique-candidate path was folded into the same suggestion-block
 * output as the ambiguous one.
 *
 * Also doubles as the home of the unrelated missing-constructor-import
 * warning (a selective {@code \import M(Data)} that doesn't also list
 * {@code Data}'s constructors), which fires from the same pass.
 */
public final class ReferenceResolveSuggest {

  private ReferenceResolveSuggest() {}

  public record Result(
      @NotNull List<GeneralError> errorsToPrint,
      @NotNull List<SuggestionBlock> suggestionBlocks,
      @NotNull List<String> warnings
  ) {}

  /**
   * One "Candidates for X at …" block alongside the {@link ModulePath} it relates
   * to. The module path lets the output router apply the same granularity rules
   * here as for errors/warnings: at LIBRARY granularity the body goes to the log
   * only; at MODULE / DEFINITION granularity it goes to stdout iff the affected
   * module matches the target.
   */
  public record SuggestionBlock(@NotNull String text, @org.jetbrains.annotations.Nullable ModulePath affectedModule) {}

  /**
   * One candidate referable for a given unresolved short name, evaluated in the
   * context of a specific anchor file.
   */
  private record Candidate(
      @NotNull String label,                  // "library::module:longName  [KIND]"
      @NotNull String calculatedName,         // dotted text to splice into the source
      @NotNull List<RawImportAdder> imports   // import lines (toString form)
  ) {}

  public static Result process(@NotNull ArendServer server,
                               @NotNull LibraryManager manager,
                               @NotNull List<GeneralError> errors,
                               @NotNull List<SourceLibrary> requestedLibraries) {
    // 1. Load all library symbol indices once.
    Map<SourceLibrary, SymbolIndex> indices = loadIndices(manager, server);

    List<GeneralError> errorsToPrint = new ArrayList<>();
    List<SuggestionBlock> suggestionBlocks = new ArrayList<>();
    Set<String> namesAlreadySuggested = new HashSet<>();

    for (GeneralError err : errors) {
      if (!(err instanceof NotInScopeError nse) || !isTopLevelUnresolved(nse)) {
        errorsToPrint.add(err);
        continue;
      }

      SourcePosition position = positionOf(nse);
      ModuleLocation module = moduleOf(position);
      ConcreteGroup currentFile = module == null ? null : server.getRawGroup(module);
      LocatedReferable anchorParent = currentFile == null ? null : currentFile.referable();
      Path filePath = filePathOf(manager, module);

      if (position == null || module == null || currentFile == null
          || anchorParent == null || anchorParent.getLocation() == null
          || filePath == null) {
        errorsToPrint.add(err);
        continue;
      }

      // 2. Find symbol-index candidates for this short name.
      List<IndexHit> hits = lookupCandidates(indices, nse.name);

      if (hits.isEmpty()) {
        errorsToPrint.add(err);
        continue;
      }

      // 3. For each hit, compute a calculated name + import command via
      //    SingleFileReferenceResolver. Drop hits whose target referable can't
      //    be located (stale index, foreign generated entries, etc.).
      List<Candidate> candidates = new ArrayList<>();
      for (IndexHit hit : hits) {
        LocatedReferable target = locateReferable(server, hit);
        if (target == null) continue;

        SingleFileReferenceResolver resolver =
            new SingleFileReferenceResolver(server, DummyErrorReporter.INSTANCE, currentFile);
        RawAnchor anchor = new RawAnchor(anchorParent, position);
        Pair<@Nullable ModulePath, List<Pair<String, Referable>>> resolved =
            resolver.makeTargetAvailable(target, anchor);
        if (resolved == null) continue;

        String calculatedName = renderCalculatedName(resolved);
        List<RawImportAdder> imports = collectImports(resolver.getModifier());

        candidates.add(new Candidate(formatLabel(hit, target), calculatedName, imports));
      }

      if (candidates.isEmpty()) {
        errorsToPrint.add(err);
        continue;
      }

      // Deduplicate exact-equal candidates (same label + same calculated name).
      candidates = dedupCandidates(candidates);

      // The original error is always re-emitted alongside the suggestion: the
      // user still needs to see WHERE in the source the name failed; the
      // candidate block tells them WHAT to write instead.
      errorsToPrint.add(err);
      String key = filePath + "|" + nse.name;
      if (namesAlreadySuggested.add(key)) {
        String text = formatSuggestionBlock(filePath, position, nse.name, candidates, manager);
        suggestionBlocks.add(new SuggestionBlock(text, module.getModulePath()));
      }
    }

    // Surface non-core warnings: imports that bring in a data type but
    // leave its constructors out of scope (the silent-variable-pattern bug).
    List<String> warnings = findMissingConstructorImports(server, manager, requestedLibraries);

    return new Result(errorsToPrint, suggestionBlocks, warnings);
  }

  // ---- missing-constructor-import warning -------------------------------

  /**
   * For every selective `\import M(...)` in every loaded source file of the
   * requested libraries, look up each renaming target in the imported module
   * and emit a warning when the target is a `\data` definition whose
   * constructors aren't also brought into the file's scope. This is the
   * known-confusing pattern documented in arend-bugs.md: a constructor-shaped
   * pattern silently binds a fresh variable when the constructor isn't in scope.
   */
  private static List<String> findMissingConstructorImports(@NotNull ArendServer server,
                                                            @NotNull LibraryManager manager,
                                                            @NotNull List<SourceLibrary> requestedLibraries) {
    List<String> out = new ArrayList<>();
    for (SourceLibrary library : requestedLibraries) {
      for (ModulePath mp : library.findModules(false)) {
        ModuleLocation loc = new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp);
        ConcreteGroup group = server.getRawGroup(loc);
        if (group == null) continue;
        Path filePath = filePathOf(manager, loc);
        scanGroupForImports(server, loc, group, filePath, out, manager);
      }
    }
    return out;
  }

  private static void scanGroupForImports(@NotNull ArendServer server,
                                          @NotNull ModuleLocation currentModule,
                                          @NotNull ConcreteGroup group,
                                          @Nullable Path filePath,
                                          @NotNull List<String> out,
                                          @Nullable LibraryManager manager) {
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteNamespaceCommand cmd = stmt.command();
      if (cmd != null && cmd.isImport()) {
        checkImportForMissingConstructors(server, currentModule, cmd, filePath, out, manager);
      }
      if (stmt.group() != null) {
        scanGroupForImports(server, currentModule, stmt.group(), filePath, out, manager);
      }
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      scanGroupForImports(server, currentModule, dyn, filePath, out, manager);
    }
  }

  private static void checkImportForMissingConstructors(@NotNull ArendServer server,
                                                        @NotNull ModuleLocation currentModule,
                                                        @NotNull ConcreteNamespaceCommand cmd,
                                                        @Nullable Path filePath,
                                                        @NotNull List<String> out,
                                                        @Nullable LibraryManager manager) {
    // Only selective imports of the form `import M (a, b, ...)` can leave a
    // data type's constructors out of scope; the bare and using forms bring everything.
    if (cmd.isUsing()) return;
    if (cmd.renamings().isEmpty()) return;

    ModulePath importedPath = new ModulePath(cmd.module().getPath());
    boolean inTests = currentModule.getLocationKind() == ModuleLocation.LocationKind.TEST;
    ModuleLocation importedModule = server.findModule(importedPath, currentModule.getLibraryName(), inTests, true);
    if (importedModule == null) return;
    ConcreteGroup importedGroup = server.getRawGroup(importedModule);
    if (importedGroup == null) return;

    Set<String> importedNames = new HashSet<>();
    for (ConcreteNamespaceCommand.NameRenaming r : cmd.renamings()) {
      importedNames.add(r.reference().getRefName());
    }
    Set<String> hiddenNames = new HashSet<>();
    for (ConcreteNamespaceCommand.NameHiding h : cmd.hidings()) {
      hiddenNames.add(h.reference().getRefName());
    }

    for (ConcreteNamespaceCommand.NameRenaming r : cmd.renamings()) {
      String name = r.reference().getRefName();
      ConcreteGroup defGroup = findChildGroupByName(importedGroup, name);
      if (defGroup == null) continue;
      if (!(defGroup.definition() instanceof org.arend.term.concrete.Concrete.DataDefinition)) continue;

      List<String> missing = new ArrayList<>();
      for (org.arend.naming.reference.InternalReferable inner : defGroup.getInternalReferables()) {
        String cname = inner.textRepresentation();
        if (cname == null || cname.isEmpty()) continue;
        if (importedNames.contains(cname)) continue;
        if (hiddenNames.contains(cname)) continue;
        missing.add(cname);
      }
      if (missing.isEmpty()) continue;

      out.add(formatMissingConstructorWarning(currentModule, cmd, filePath, importedPath, name, missing, manager));
    }
  }

  private static @Nullable ConcreteGroup findChildGroupByName(@NotNull ConcreteGroup group, @NotNull String name) {
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub == null) continue;
      LocatedReferable ref = sub.referable();
      if (ref == null) continue;
      if (name.equals(ref.textRepresentation())) return sub;
      if (ref instanceof GlobalReferable g && name.equals(g.getAliasName())) return sub;
    }
    return null;
  }

  private static String formatMissingConstructorWarning(@NotNull ModuleLocation currentModule,
                                                        @NotNull ConcreteNamespaceCommand cmd,
                                                        @Nullable Path filePath,
                                                        @NotNull ModulePath importedPath,
                                                        @NotNull String dataName,
                                                        @NotNull List<String> missing,
                                                        @Nullable LibraryManager manager) {
    String pos;
    String shortenedPath = filePath != null ? PathDisplay.shorten(filePath, manager) : null;
    if (cmd.getData() instanceof SourcePosition sp) {
      String prefix = shortenedPath != null ? shortenedPath : currentModule.toString();
      pos = prefix + ":" + sp.line + ":" + sp.column;
    } else if (shortenedPath != null) {
      pos = shortenedPath;
    } else {
      pos = currentModule.toString();
    }
    return "[WARNING] " + pos + ": \\import " + importedPath + "(" + dataName
        + ") brings the data type but not its constructor(s) " + String.join(", ", missing)
        + ". Pattern matches against these constructors will silently bind fresh variables."
        + " Add them: \\import " + importedPath + "(" + dataName + ", " + String.join(", ", missing) + ").";
  }

  // ---- candidate lookup --------------------------------------------------

  private record IndexHit(@NotNull SourceLibrary library, @NotNull SymbolIndex.Entry entry) {}

  private static List<IndexHit> lookupCandidates(Map<SourceLibrary, SymbolIndex> indices, String name) {
    List<IndexHit> hits = new ArrayList<>();
    for (Map.Entry<SourceLibrary, SymbolIndex> entry : indices.entrySet()) {
      for (SymbolIndex.Entry e : entry.getValue().allEntries()) {
        if (e.shortName().equals(name)) {
          hits.add(new IndexHit(entry.getKey(), e));
        }
      }
    }
    return hits;
  }

  private static Map<SourceLibrary, SymbolIndex> loadIndices(LibraryManager manager, ArendServer server) {
    Map<SourceLibrary, SymbolIndex> result = new LinkedHashMap<>();
    for (String libName : manager.getLibraries()) {
      SourceLibrary lib = manager.getLibrary(libName);
      if (lib == null) continue;
      SymbolIndex idx = SymbolIndex.loadOrCreate(lib);
      // Populate the GENERATED_BUCKET with virtual modules (Meta, Paths.Meta,
      // Function.Meta, Algebra.Meta, etc.) registered programmatically by
      // extensions. loadOrCreate only reads the on-disk cache; without this
      // refresh, candidates for meta combinators (in, at, rewrite, run, …)
      // would be missing from the suggestion set.
      idx.refresh(lib, server, false);
      result.put(lib, idx);
    }
    return result;
  }

  private static @Nullable LocatedReferable locateReferable(ArendServer server, IndexHit hit) {
    ModuleLocation moduleLoc = server.findModule(hit.entry.modulePath(), hit.library.getLibraryName(), true, true);
    if (moduleLoc == null) return null;
    ConcreteGroup group = server.getRawGroup(moduleLoc);
    if (group == null) return null;
    return walkLongName(group, LongName.fromString(hit.entry.longName()));
  }

  private static @Nullable LocatedReferable walkLongName(ConcreteGroup group, LongName ln) {
    LocatedReferable result = group.referable();
    List<String> names = ln.toList();
    for (int i = 0; i < names.size(); i++) {
      String segment = names.get(i);
      ConcreteGroup next = findChildGroup(group, segment);
      if (next != null) {
        group = next;
        result = next.referable();
        continue;
      }
      LocatedReferable internal = findInternalReferable(group, segment);
      if (internal != null) {
        return i == names.size() - 1 ? internal : null;
      }
      return null;
    }
    return result;
  }

  private static @Nullable ConcreteGroup findChildGroup(ConcreteGroup group, String name) {
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null && sub.referable() != null
          && name.equals(sub.referable().textRepresentation())) {
        return sub;
      }
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      if (dyn.referable() != null && name.equals(dyn.referable().textRepresentation())) {
        return dyn;
      }
    }
    return null;
  }

  private static @Nullable LocatedReferable findInternalReferable(ConcreteGroup group, String name) {
    for (LocatedReferable r : group.getInternalReferables()) {
      if (name.equals(r.textRepresentation())) return r;
    }
    return null;
  }

  // ---- formatting --------------------------------------------------------

  private static String renderCalculatedName(Pair<@Nullable ModulePath, List<Pair<String, Referable>>> resolved) {
    StringBuilder sb = new StringBuilder();
    if (resolved.proj1 != null) {
      for (String seg : resolved.proj1.toList()) {
        if (sb.length() > 0) sb.append('.');
        sb.append(seg);
      }
    }
    for (Pair<String, Referable> p : resolved.proj2) {
      if (sb.length() > 0) sb.append('.');
      sb.append(p.proj1);
    }
    return sb.toString();
  }

  private static List<RawImportAdder> collectImports(@Nullable RawModifier modifier) {
    List<RawImportAdder> out = new ArrayList<>();
    collectImportsRec(modifier, out);
    return out;
  }

  private static void collectImportsRec(@Nullable RawModifier modifier, List<RawImportAdder> out) {
    if (modifier instanceof RawImportAdder adder) {
      out.add(adder);
    } else if (modifier instanceof RawSequenceModifier seq) {
      for (RawModifier m : seq.sequence()) collectImportsRec(m, out);
    }
  }

  private static String formatLabel(IndexHit hit, LocatedReferable target) {
    String kind;
    if (target instanceof GlobalReferable g) kind = g.getKind().name();
    else kind = hit.entry.kind().name();
    return hit.library.getLibraryName() + "::" + hit.entry.modulePath() + ":" + hit.entry.longName()
        + "  [" + kind + "]";
  }

  private static String formatSuggestionBlock(Path file, SourcePosition pos, String name, List<Candidate> candidates, LibraryManager manager) {
    StringBuilder sb = new StringBuilder();
    sb.append("  Candidates for '").append(name).append("' at ")
        .append(PathDisplay.shorten(file, manager)).append(':').append(pos.line).append(':').append(pos.column).append(':');
    int idx = 1;
    for (Candidate c : candidates) {
      sb.append('\n').append("    [").append(idx++).append("] ").append(c.label)
          .append("  -> ").append(c.calculatedName);
      if (c.imports.isEmpty()) {
        sb.append("  (no import needed)");
      } else {
        for (RawImportAdder imp : c.imports) sb.append("  +").append(imp.command());
      }
    }
    return sb.toString();
  }

  private static List<Candidate> dedupCandidates(List<Candidate> in) {
    LinkedHashMap<String, Candidate> map = new LinkedHashMap<>();
    for (Candidate c : in) {
      StringBuilder key = new StringBuilder();
      key.append(c.label).append('|').append(c.calculatedName);
      for (RawImportAdder imp : c.imports) key.append('|').append(imp.command());
      map.putIfAbsent(key.toString(), c);
    }
    return new ArrayList<>(map.values());
  }

  // ---- error introspection ----------------------------------------------

  private static boolean isTopLevelUnresolved(NotInScopeError err) {
    return err.referable == null && err.index == 0 && err.name != null && !err.name.isEmpty();
  }

  private static @Nullable SourcePosition positionOf(NotInScopeError err) {
    Object cause = err.getCause();
    if (cause instanceof SourcePosition sp && sp.line > 0) return sp;
    return null;
  }

  private static @Nullable ModuleLocation moduleOf(@Nullable SourcePosition pos) {
    if (pos instanceof org.arend.frontend.parser.Position fp) return fp.module;
    return null;
  }

  private static @Nullable Path filePathOf(LibraryManager manager, @Nullable ModuleLocation module) {
    if (module == null || module.getLocationKind() != ModuleLocation.LocationKind.SOURCE) return null;
    SourceLibrary lib = manager.getLibrary(module.getLibraryName());
    if (!(lib instanceof FileSourceLibrary fl)) return null;
    Path src = fl.getSourceBasePath();
    if (src == null) return null;
    try {
      return FileUtils.sourceFile(src, module.getModulePath()).toAbsolutePath().normalize();
    } catch (RuntimeException e) {
      return null;
    }
  }

}
