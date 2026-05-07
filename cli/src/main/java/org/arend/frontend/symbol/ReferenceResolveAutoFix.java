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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Auto-fix engine used by {@code -rr}: takes the buffered name-resolution
 * errors, looks up unresolved short names in the per-library binary symbol
 * indices, and either rewrites the source (unique candidate) or prints the
 * candidate list (multiple candidates).
 */
public final class ReferenceResolveAutoFix {

  private ReferenceResolveAutoFix() {}

  public record Result(
      @NotNull List<GeneralError> errorsToPrint,
      @NotNull List<String> suggestionBlocks,
      @NotNull List<String> infoMessages,
      @NotNull List<Path> modifiedFiles
  ) {}

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
                               @NotNull List<GeneralError> errors) {
    // 1. Load all library symbol indices once.
    Map<SourceLibrary, SymbolIndex> indices = loadIndices(manager);

    // 2. Collect fixes per file + buffered output for non-fixable errors.
    Map<Path, FileFixes> perFile = new LinkedHashMap<>();
    List<GeneralError> errorsToPrint = new ArrayList<>();
    List<String> suggestionBlocks = new ArrayList<>();
    List<String> infoMessages = new ArrayList<>();
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

      // 3. Find symbol-index candidates for this short name.
      List<IndexHit> hits = lookupCandidates(indices, nse.name);

      if (hits.isEmpty()) {
        errorsToPrint.add(err);
        continue;
      }

      // 4. For each hit, compute a calculated name + import command via
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

      if (candidates.size() == 1) {
        Candidate only = candidates.getFirst();
        FileFixes ff = perFile.computeIfAbsent(filePath, FileFixes::new);
        ff.replacements.add(new Replacement(position.line, position.column, nse.name.length(), only.calculatedName));
        for (RawImportAdder imp : only.imports) ff.imports.add(imp);
        infoMessages.add(formatInfoMessage(filePath, position, nse.name, only));
      } else {
        errorsToPrint.add(err);
        // Print the candidate list once per (file, name) pair.
        String key = filePath + "|" + nse.name;
        if (namesAlreadySuggested.add(key)) {
          suggestionBlocks.add(formatSuggestionBlock(filePath, position, nse.name, candidates));
        }
      }
    }

    // 5. Apply the per-file fixes.
    List<Path> modified = new ArrayList<>();
    for (FileFixes ff : perFile.values()) {
      if (applyFixes(ff)) modified.add(ff.path);
    }

    return new Result(errorsToPrint, suggestionBlocks, infoMessages, modified);
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

  private static Map<SourceLibrary, SymbolIndex> loadIndices(LibraryManager manager) {
    Map<SourceLibrary, SymbolIndex> result = new LinkedHashMap<>();
    for (String libName : manager.getLibraries()) {
      SourceLibrary lib = manager.getLibrary(libName);
      if (lib == null) continue;
      result.put(lib, SymbolIndex.loadOrCreate(lib));
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

  private static String formatInfoMessage(Path file, SourcePosition pos, String name, Candidate fix) {
    StringBuilder sb = new StringBuilder();
    sb.append("[INFO] ").append(file).append(':').append(pos.line).append(':').append(pos.column);
    sb.append(": auto-resolved '").append(name).append("' -> ").append(fix.calculatedName);
    sb.append(" (").append(fix.label).append(')');
    if (!fix.imports.isEmpty()) {
      sb.append("; added");
      for (RawImportAdder imp : fix.imports) sb.append(" `").append(imp.command()).append('`');
    }
    return sb.toString();
  }

  private static String formatSuggestionBlock(Path file, SourcePosition pos, String name, List<Candidate> candidates) {
    StringBuilder sb = new StringBuilder();
    sb.append("  Candidates for '").append(name).append("' at ")
        .append(file).append(':').append(pos.line).append(':').append(pos.column).append(':');
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

  // ---- file rewriting ---------------------------------------------------

  private record Replacement(int line, int column, int length, @NotNull String text) {}

  private static final class FileFixes {
    final Path path;
    final List<Replacement> replacements = new ArrayList<>();
    final Set<RawImportAdder> imports = new LinkedHashSet<>();
    FileFixes(Path path) { this.path = path; }
  }

  private static boolean applyFixes(FileFixes ff) {
    String original;
    try {
      original = Files.readString(ff.path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      System.err.println("[ERROR] cannot read " + ff.path + ": " + e.getMessage());
      return false;
    }

    String afterReplacements = applyReplacements(original, ff.replacements);
    String afterImports = applyImports(afterReplacements, ff.imports);

    if (afterImports.equals(original)) return false;
    try {
      Files.writeString(ff.path, afterImports, StandardCharsets.UTF_8);
    } catch (IOException e) {
      System.err.println("[ERROR] cannot write " + ff.path + ": " + e.getMessage());
      return false;
    }
    return true;
  }

  private static String applyReplacements(String text, List<Replacement> replacements) {
    if (replacements.isEmpty()) return text;
    int[] lineStarts = computeLineStarts(text);
    // Sort by file offset descending so each replacement keeps prior offsets intact.
    List<Replacement> ordered = new ArrayList<>(replacements);
    ordered.sort(Comparator.comparingInt(
        (Replacement r) -> offsetOf(lineStarts, r.line, r.column)).reversed());
    StringBuilder sb = new StringBuilder(text);
    for (Replacement r : ordered) {
      int start = offsetOf(lineStarts, r.line, r.column);
      if (start < 0 || start + r.length > sb.length()) continue;
      sb.replace(start, start + r.length, r.text);
    }
    return sb.toString();
  }

  private static int[] computeLineStarts(String text) {
    List<Integer> starts = new ArrayList<>();
    starts.add(0);
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') starts.add(i + 1);
    }
    int[] arr = new int[starts.size()];
    for (int i = 0; i < starts.size(); i++) arr[i] = starts.get(i);
    return arr;
  }

  private static int offsetOf(int[] lineStarts, int line, int column) {
    int idx = line - 1;
    if (idx < 0 || idx >= lineStarts.length) return -1;
    return lineStarts[idx] + (column - 1);
  }

  /**
   * Insert each new {@code \import ...} line in the existing import block,
   * keeping it sorted lexicographically by the module path. Imports that
   * target the same module are merged: a "using everything" form supersedes
   * any name-list form, otherwise the renaming sets are unioned.
   */
  private static String applyImports(String text, Set<RawImportAdder> imports) {
    if (imports.isEmpty()) return text;

    // 1. Lower the new imports into structured specs grouped by module path.
    LinkedHashMap<String, ImportSpec> newSpecs = new LinkedHashMap<>();
    for (RawImportAdder imp : imports) {
      ConcreteNamespaceCommand cmd = imp.command();
      String mod = String.join(".", cmd.module().getPath());
      ImportSpec spec = newSpecs.computeIfAbsent(mod, ImportSpec::new);
      spec.merge(cmd);
    }

    List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));

    // 2. Find the contiguous block of \import lines at the top.
    int firstImport = -1;
    int lastImport = -1;
    for (int i = 0; i < lines.size(); i++) {
      String trimmed = lines.get(i).stripLeading();
      if (trimmed.startsWith("\\import")) {
        if (firstImport < 0) firstImport = i;
        lastImport = i;
      } else if (firstImport >= 0 && !trimmed.isEmpty()) {
        break;
      }
    }

    // 3. Walk the existing block. For each \import line whose module path
    // matches one of the new specs, replace the line with a merged version;
    // otherwise leave it alone.
    List<String> mergedBlock = new ArrayList<>();
    if (firstImport >= 0) {
      for (int i = firstImport; i <= lastImport; i++) {
        String original = lines.get(i);
        String trimmed = original.stripLeading();
        if (!trimmed.startsWith("\\import")) {
          mergedBlock.add(original);
          continue;
        }
        String key = importLineKey(trimmed);
        ImportSpec spec = newSpecs.remove(key);
        if (spec == null) {
          mergedBlock.add(original);
        } else {
          // Merge the existing line's renamings into the new spec, then render.
          ImportSpec existing = ImportSpec.parse(trimmed);
          if (existing != null) spec.absorb(existing);
          mergedBlock.add(spec.render());
        }
      }
    }

    // 4. Render the remaining new specs (modules not previously imported).
    List<String> additions = new ArrayList<>();
    for (ImportSpec spec : newSpecs.values()) additions.add(spec.render());
    if (additions.isEmpty() && firstImport < 0) return text;

    // 5. Reassemble the file with the imports block sorted lexicographically.
    List<String> finalBlock = new ArrayList<>(mergedBlock);
    finalBlock.addAll(additions);
    finalBlock.sort(Comparator.naturalOrder());

    if (firstImport < 0) {
      List<String> head = new ArrayList<>(finalBlock);
      if (!lines.isEmpty()) head.add("");
      head.addAll(lines);
      return String.join("\n", head);
    }

    List<String> rebuilt = new ArrayList<>();
    rebuilt.addAll(lines.subList(0, firstImport));
    rebuilt.addAll(finalBlock);
    rebuilt.addAll(lines.subList(lastImport + 1, lines.size()));
    return String.join("\n", rebuilt);
  }

  /** Crude module-path key: the bare module path token of {@code \import Foo.Bar(...)}. */
  private static String importLineKey(String line) {
    String body = line.stripLeading();
    if (body.startsWith("\\import")) body = body.substring("\\import".length()).stripLeading();
    int cut = body.length();
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (c == ' ' || c == '\t' || c == '(' || c == '\\') { cut = i; break; }
    }
    return body.substring(0, cut);
  }

  private static final class ImportSpec {
    final String modulePath;
    boolean usingAll = false;            // `\import Foo` (no name list)
    final TreeSet<String> names = new TreeSet<>();

    ImportSpec(String modulePath) { this.modulePath = modulePath; }

    void merge(ConcreteNamespaceCommand cmd) {
      // The "import everything" shape coming out of the resolver is
      // isUsing() with empty renamings/hidings; it formats as `\import Foo`.
      if (cmd.isUsing() && cmd.renamings().isEmpty() && cmd.hidings().isEmpty()) {
        usingAll = true;
        return;
      }
      for (ConcreteNamespaceCommand.NameRenaming r : cmd.renamings()) {
        names.add(r.reference().getRefName());
      }
    }

    void absorb(ImportSpec other) {
      if (other.usingAll) usingAll = true;
      names.addAll(other.names);
    }

    /** Best-effort parser for an existing {@code \import ...} source line. */
    static @Nullable ImportSpec parse(String trimmed) {
      String key = importLineKey(trimmed);
      if (key.isEmpty()) return null;
      ImportSpec spec = new ImportSpec(key);
      String rest = trimmed.substring(trimmed.indexOf(key) + key.length()).trim();
      if (rest.isEmpty()) {
        spec.usingAll = true;
        return spec;
      }
      // `\hiding (...)` is left untouched -- bail and don't merge with this line.
      if (rest.contains("\\hiding")) return null;
      // Strip an optional using prefix (the backslash form).
      if (rest.startsWith("\\using")) rest = rest.substring("\\using".length()).trim();
      // Now we expect `(name1, name2, ...)`. Anything else (e.g. \plevel renamings) -- bail.
      if (!rest.startsWith("(") || !rest.endsWith(")")) return null;
      String inside = rest.substring(1, rest.length() - 1);
      for (String piece : inside.split(",")) {
        String p = piece.trim();
        if (p.isEmpty()) continue;
        // `name \as alias` -- keep the original name; auto-fix doesn't introduce aliases.
        int as = p.indexOf("\\as");
        if (as >= 0) p = p.substring(0, as).trim();
        if (!p.isEmpty()) spec.names.add(p);
      }
      return spec;
    }

    String render() {
      if (usingAll || names.isEmpty()) return "\\import " + modulePath;
      return "\\import " + modulePath + "(" + String.join(", ", names) + ")";
    }
  }
}
