package org.arend.frontend.query.scopeinfo;

import org.arend.ext.module.ModuleLocation;
import org.arend.frontend.query.JsonUtils;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** JSON: {@code {"target": ..., "entries": [...], "count": N}}; see {@link #print} for the entry shape. */
final class JsonScopePrinter implements ScopePrinter {
  /**
   * Emits {@code {"target": "...", "entries": [...], "count": N}} on {@code out}. Each entry
   * carries its in-scope {@code name}, its {@code context} (STATIC/DYNAMIC/PLEVEL/HLEVEL), and
   * either the resolved target ({@code kind}, {@code module}, {@code longName}, optional
   * {@code library}) or {@code "local": true} + {@code refType} for locally-bound referables.
   */
  @Override
  public int print(Scope scope, String targetLabel, @Nullable SymbolPattern pattern,
      Options options, boolean showLibrary, PrintStream out) {
    List<JsonRow> rows = new ArrayList<>();
    if (options.context == ScopeSelection.ALL) {
      collectJson(scope, ScopeContext.STATIC,  "STATIC",  pattern, showLibrary, rows);
      collectJson(scope, ScopeContext.DYNAMIC, "DYNAMIC", pattern, showLibrary, rows);
    } else if (options.context == ScopeSelection.STATIC_AND_DYNAMIC) {
      collectJson(scope, ScopeContext.STATIC,  "STATIC",  pattern, showLibrary, rows);
      collectJson(scope, ScopeContext.DYNAMIC, "DYNAMIC", pattern, showLibrary, rows);
    } else {
      ScopeContext ctx = options.context == ScopeSelection.DYNAMIC ? ScopeContext.DYNAMIC : ScopeContext.STATIC;
      collectJson(scope, ctx, ctx.name(), pattern, showLibrary, rows);
    }
    // Dedup identical entries (a merged scope can surface the same binding twice);
    // entries in different contexts keep distinct bodies, so they survive.
    LinkedHashMap<String, JsonRow> uniq = new LinkedHashMap<>();
    for (JsonRow r : rows) uniq.putIfAbsent(r.body(), r);
    rows = new ArrayList<>(uniq.values());
    // Same order as the text dump: by in-scope name (case-insensitive), then body.
    rows.sort(Comparator.comparing(JsonRow::name, String.CASE_INSENSITIVE_ORDER).thenComparing(JsonRow::body));
    writeJson(out, targetLabel, rows);
    return rows.size();
  }

  /** Emits the empty-result object; used by the tool's error paths, before any scope is available. */
  static void writeEmpty(PrintStream out, @Nullable String target) {
    writeJson(out, target, List.of());
  }

  private static void collectJson(Scope scope, ScopeContext ctx, String context,
      @Nullable SymbolPattern pattern, boolean showLibrary, List<JsonRow> rows) {
    for (Referable ref : scope.getElements(ctx)) {
      String name = ref.textRepresentation();
      if (pattern != null && !pattern.matches(name)) continue;
      rows.add(new JsonRow(name, jsonEntry(context, ref, showLibrary)));
    }
  }

  private static String jsonEntry(String context, Referable ref, boolean showLibrary) {
    StringBuilder sb = new StringBuilder("{");
    sb.append("\"name\":").append(JsonUtils.str(ref.textRepresentation()));
    sb.append(",\"context\":").append(JsonUtils.str(context));
    if (ref instanceof LocatedReferable lr) {
      sb.append(",\"kind\":").append(JsonUtils.str(String.valueOf(lr.getKind())));
      ModuleLocation loc = lr.getLocation();
      if (loc != null) {
        if (showLibrary) sb.append(",\"library\":").append(JsonUtils.str(loc.getLibraryName()));
        sb.append(",\"module\":").append(JsonUtils.str(loc.getModulePath().toString()));
      }
      sb.append(",\"longName\":").append(JsonUtils.str(lr.getRefLongName().toString()));
    } else {
      sb.append(",\"local\":true,\"refType\":").append(JsonUtils.str(ref.getClass().getSimpleName()));
    }
    return sb.append('}').toString();
  }

  private static void writeJson(PrintStream out, @Nullable String target, List<JsonRow> rows) {
    // `target` is ALWAYS emitted (null when the target could not be resolved, e.g. an
    // error path) so the object shape stays fixed across success and failure.
    String targetJson = target == null ? "null" : JsonUtils.str(target);
    if (rows.isEmpty()) {
      out.println("{\"target\": " + targetJson + ", \"entries\": [], \"count\": 0}");
      return;
    }
    out.println("{");
    out.println("  \"target\": " + targetJson + ",");
    out.println("  \"entries\": [");
    for (int i = 0; i < rows.size(); i++) {
      out.println("    " + rows.get(i).body() + (i < rows.size() - 1 ? "," : ""));
    }
    out.println("  ],");
    out.println("  \"count\": " + rows.size());
    out.println("}");
  }

  /** One rendered scope entry plus its in-scope name, used to sort the JSON array deterministically. */
  private record JsonRow(String name, String body) {}
}
