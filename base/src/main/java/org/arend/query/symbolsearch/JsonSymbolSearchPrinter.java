package org.arend.query.symbolsearch;

import org.arend.query.QueryLibraries;
import org.arend.query.PathDisplayUtils;
import org.arend.query.ResultJson;
import org.arend.query.SymbolIndex;
import org.arend.query.symbolsearch.SymbolSearchEngine.Hit;
import org.arend.query.symbolsearch.SymbolSearchEngine.Result;
import org.arend.query.symbolsearch.SymbolSearchTool.Options;
import org.arend.query.symbolsearch.SymbolSearchTool.SymbolSearchPrinter;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/** JSON: {@code {"results": [...], "count": N}} — see {@link #print} for the per-result fields. */
final class JsonSymbolSearchPrinter implements SymbolSearchPrinter {
  /**
   * Emits the ranked hits via {@link ResultJson} as {@code {"results": [...], "count": N}}.
   * Each result carries {@code library} (omitted when a single non-prelude library is in scope),
   * {@code module}, {@code name}, {@code kind}, {@code signature} (omitted when empty) and a
   * {@code location}. {@code results} honours {@code limit}; {@code count} is the pre-truncation total.
   */
  @Override
  public void print(PrintStream out, Result result, Options options, boolean showLibrary, QueryLibraries lm) {
    List<Hit> hits = result.hits();
    int shown = (options.limit > 0) ? Math.min(hits.size(), options.limit) : hits.size();
    List<ResultJson.Row> rows = new ArrayList<>(shown);
    for (int i = 0; i < shown; i++) {
      Hit h = hits.get(i);
      SymbolIndex.Entry e = h.entry();
      String file = (e.absoluteFile() == null || e.absoluteFile().isEmpty())
          ? null : PathDisplayUtils.shorten(e.absoluteFile(), lm);
      rows.add(new ResultJson.Row(
          showLibrary ? h.libName() : null,
          e.modulePath().toString(),
          e.longName(),
          e.kind().name(),
          e.signature().isEmpty() ? null : e.signature(),
          null,            // -ss reports a full signature, never an "expression" slice
          file,
          e.line(),
          e.column()));
    }
    ResultJson.write(out, rows, hits.size());
  }
}
