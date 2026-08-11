package org.arend.frontend.query.symbolsearch;

import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.query.SymbolPattern;
import org.arend.frontend.query.symbolsearch.SymbolSearchEngine.Hit;
import org.arend.frontend.query.symbolsearch.SymbolSearchEngine.Result;
import org.arend.frontend.query.symbolsearch.SymbolSearchTool.Options;
import org.arend.frontend.query.symbolsearch.SymbolSearchTool.SymbolSearchPrinter;

import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringJoiner;

/** Text: the ranked {@code -ss} listing (via {@link SymbolSearchRenderer}), with "did you mean?" suggestions when empty. */
final class TextSymbolSearchPrinter implements SymbolSearchPrinter {
  private final List<SymbolPattern> patterns;

  TextSymbolSearchPrinter(List<SymbolPattern> patterns) {
    this.patterns = patterns;
  }

  @Override
  public void print(PrintStream out, Result result, Options options, boolean showLibrary, LibraryManager lm) {
    List<Hit> hits = result.hits();
    int total = hits.size();
    int printed = 0;
    StringBuilder sb = new StringBuilder();
    for (Hit h : hits) {
      if (options.limit > 0 && printed >= options.limit) break;
      SymbolSearchRenderer.appendEntry(sb, showLibrary, h.libName(), h.entry(), lm, patterns);
      printed++;
    }

    out.print(sb);
    if (total == 0) {
      out.println("No matches.");
      printSuggestions(out, result.suggestions(), result.suggestParts(), showLibrary);
    } else {
      out.println();
      out.println("Found " + total + " match" + (total == 1 ? "" : "es")
          + options.truncationNote(printed, total));
    }
  }

  private static void printSuggestions(PrintStream out, List<Hit> suggestions,
                                       LinkedHashSet<String> parts, boolean showLibrary) {
    if (suggestions.isEmpty()) return;
    int cap = 8;
    int show = Math.min(suggestions.size(), cap);
    out.println();
    StringJoiner partList = new StringJoiner("', '", "'", "'");
    for (String p : parts) partList.add(p);
    out.println("Did you mean? (names containing word-parts of your query: " + partList + ")");
    for (int i = 0; i < show; i++) {
      Hit h = suggestions.get(i);
      out.println("  " + SymbolSearchRenderer.plainQualifiedName(showLibrary, h.libName(), h.entry(), h.entry().longName())
          + "  [" + h.entry().kind().name() + "]");
    }
    if (suggestions.size() > cap) {
      out.println("  ... and " + (suggestions.size() - cap) + " more "
          + "(re-run with a single word-part to see all).");
    }
  }
}
