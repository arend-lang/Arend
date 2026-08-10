package org.arend.frontend.query.scopeinfo;

import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.frontend.query.QualifiedName;
import org.arend.frontend.query.SymbolPattern;
import org.arend.frontend.query.scopeinfo.ScopeInfoTool.Options;
import org.arend.frontend.query.scopeinfo.ScopeInfoTool.ScopePrinter;
import org.arend.frontend.query.scopeinfo.ScopeInfoTool.ScopeSelection;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.scope.Scope;
import org.arend.naming.scope.Scope.ScopeContext;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Text: one entry per line as {@code SHORT_NAME -> LIB::MODULE:LONG_NAME [KIND]}, sorted and deduped. */
final class TextScopePrinter implements ScopePrinter {
  @Override
  public int print(Scope scope, String targetLabel, @Nullable SymbolPattern pattern,
      Options options, boolean showLibrary, PrintStream out) {
    out.println("--- Scope at " + targetLabel + " ---");
    int total;
    int matched = 0;
    if (options.context == ScopeSelection.ALL) {
      // Print each context section in turn.
      matched += dumpSection(scope, ScopeContext.STATIC,  "STATIC",  pattern, showLibrary, out);
      matched += dumpSection(scope, ScopeContext.DYNAMIC, "DYNAMIC", pattern, showLibrary, out);
      total = scope.getElements(null).size();
    } else if (options.context == ScopeSelection.STATIC_AND_DYNAMIC) {
      // Default: static and dynamic entries as a single sorted list (not two sections).
      List<Referable> elements = new ArrayList<>();
      elements.addAll(scope.getElements(ScopeContext.STATIC));
      elements.addAll(scope.getElements(ScopeContext.DYNAMIC));
      total = distinctCount(elements, showLibrary);
      matched = printEntries(elements, pattern, showLibrary, out);
    } else {
      ScopeContext ctx = options.context == ScopeSelection.DYNAMIC ? ScopeContext.DYNAMIC : ScopeContext.STATIC;
      Collection<? extends Referable> elements = scope.getElements(ctx);
      total = distinctCount(elements, showLibrary);
      matched = printEntries(elements, pattern, showLibrary, out);
    }

    if (pattern != null) {
      out.println("--- " + matched + " match(es) of " + total + " entries (pattern: " + pattern.source() + ") ---");
    } else {
      out.println("--- " + total + " entries ---");
    }
    return 0;
  }

  private static int dumpSection(Scope scope, ScopeContext ctx, String header,
      @Nullable SymbolPattern pattern, boolean showLibrary, PrintStream out) {
    Collection<? extends Referable> elements = scope.getElements(ctx);
    if (elements.isEmpty()) return 0;
    out.println();
    out.println("[" + header + "]");
    return printEntries(elements, pattern, showLibrary, out);
  }

  private static int printEntries(Collection<? extends Referable> elements,
      @Nullable SymbolPattern pattern, boolean showLibrary, PrintStream out) {
    // Sort by short name (then full line) so the dump has a stable, readable order,
    // and dedup identical lines: a merged scope (e.g. the REPL's, or STATIC+DYNAMIC)
    // can surface the same binding twice.
    Set<String> lines = new LinkedHashSet<>();
    for (Referable ref : elements) {
      if (pattern != null && !pattern.matches(ref.textRepresentation())) continue;
      lines.add(entryLine(ref, showLibrary));
    }
    List<String> sorted = new ArrayList<>(lines);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    for (String line : sorted) out.println(line);
    return sorted.size();
  }

  /** Number of DISTINCT rendered entries (same {@link #entryLine} key as {@link #printEntries}), unfiltered. */
  private static int distinctCount(Collection<? extends Referable> elements, boolean showLibrary) {
    Set<String> lines = new HashSet<>();
    for (Referable ref : elements) lines.add(entryLine(ref, showLibrary));
    return lines.size();
  }

  /** One dump line: {@code SHORT_NAME -> <referent>}. */
  private static String entryLine(Referable ref, boolean showLibrary) {
    return ref.textRepresentation() + " -> " + referentLabel(ref, showLibrary);
  }

  private static String referentLabel(Referable ref, boolean showLibrary) {
    if (ref instanceof LocatedReferable lr) {
      ModuleLocation loc = lr.getLocation();
      LongName ln = lr.getRefLongName();
      if (loc == null) return "?:" + ln + " [" + lr.getKind() + "]";
      return QualifiedName.format(showLibrary, loc.getLibraryName(), loc.getModulePath().toString(),
          ln.toString()) + " [" + lr.getKind() + "]";
    }
    return "(local " + ref.getClass().getSimpleName() + ")";
  }
}
