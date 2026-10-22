package org.arend.query.symbolsearch;

import org.arend.query.QueryLibrary;
import org.arend.query.SymbolIndex;
import org.arend.query.SymbolPattern;
import org.arend.server.ArendServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The name-matching engine behind {@code -ss}: refreshes each in-scope library's symbol index,
 * matches every entry against the patterns, and returns the ranked hits plus (for the empty-result
 * case) "did you mean?" suggestions drawn from the query's word-parts.
 */
final class SymbolSearchEngine {
  private SymbolSearchEngine() {}

  /**
   * One matched index entry, the library it came from, and the name the query matched:
   * the entry's short name, or its {@code \alias} when only the alias matched.
   */
  record Hit(String libName, SymbolIndex.Entry entry, String matchedName) {}

  /** Ranked hits, ranked suggestions (entries matching a query word-part), and those word-parts. */
  record Result(List<Hit> hits, List<Hit> suggestions, LinkedHashSet<String> suggestParts) {}

  // Rank: shortest matched name first, so an exact-length name (the best possible substring match
  // for any literal query) surfaces above longer names that merely contain the query. Tie-break
  // alphabetically (case-insensitive) for determinism, then by library so equal names group.
  private static final Comparator<Hit> BY_RANK = Comparator
      .<Hit>comparingInt(h -> h.matchedName().length())
      .thenComparing(Hit::matchedName, String.CASE_INSENSITIVE_ORDER)
      .thenComparing(Hit::libName);

  static Result find(List<SymbolPattern> patterns, EnumSet<SymbolIndex.Kind> kinds,
                     List<QueryLibrary> libsInScope, ArendServer server) {
    LinkedHashSet<String> suggestParts = collectWordParts(patterns);
    List<SymbolPattern> suggestPatterns = compileSuggestPatterns(suggestParts);

    List<Hit> hits = new ArrayList<>();
    List<Hit> suggestions = new ArrayList<>();
    for (QueryLibrary lib : libsInScope) {
      SymbolIndex idx = SymbolIndex.refreshLibrary(lib, server, false);
      for (SymbolIndex.Entry e : idx.allEntries()) {
        if (!kinds.contains(e.kind())) continue;
        String name = matchedName(patterns, e);
        if (name != null) {
          hits.add(new Hit(lib.getLibraryName(), e, name));
        } else if (!suggestPatterns.isEmpty() && (name = matchedName(suggestPatterns, e)) != null) {
          suggestions.add(new Hit(lib.getLibraryName(), e, name));
        }
      }
    }
    hits.sort(BY_RANK);
    suggestions.sort(BY_RANK);
    return new Result(hits, suggestions, suggestParts);
  }

  /** The entry's name (short name, else alias) that one of {@code patterns} matches, or {@code null}. */
  private static @Nullable String matchedName(List<SymbolPattern> patterns, SymbolIndex.Entry e) {
    return e.findName(name -> matchesAny(patterns, e, name));
  }

  /**
   * Whether one of {@code patterns} matches {@code name}, one of {@code e}'s names. A long-name
   * pattern matches its final segment against {@code name} and its leading segments against the
   * entry's enclosing path, which an alias shares with the short name it stands for.
   */
  private static boolean matchesAny(List<SymbolPattern> patterns, SymbolIndex.Entry e, String name) {
    List<String> prefix = null;   // lazily built; only long-name patterns need it
    for (SymbolPattern p : patterns) {
      if (p.mode() == SymbolPattern.Mode.LONGNAME) {
        if (prefix == null) prefix = SymbolSearchRenderer.prefixSegments(e);
        if (p.matchesLongName(prefix, name)) return true;
      } else if (p.matches(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Splits each {@code LITERAL} pattern's body on non-alphanumeric Arend ID chars and keeps
   * the alphanumeric runs of length >= 3, minus any part equal to the whole pattern (which
   * would just re-run the failed search). Feeds the "Did you mean?" suggestions.
   */
  private static LinkedHashSet<String> collectWordParts(List<SymbolPattern> patterns) {
    LinkedHashSet<String> parts = new LinkedHashSet<>();
    Set<String> originals = new HashSet<>();
    for (SymbolPattern p : patterns) {
      if (p.mode() != SymbolPattern.Mode.LITERAL) continue;
      String body = p.body();
      originals.add(body);
      for (String part : body.split("[^a-zA-Z0-9]+")) {
        if (part.length() >= 3) parts.add(part);
      }
    }
    parts.removeAll(originals);
    return parts;
  }

  private static List<SymbolPattern> compileSuggestPatterns(LinkedHashSet<String> parts) {
    List<SymbolPattern> out = new ArrayList<>();
    for (String part : parts) {
      try {
        out.add(SymbolPattern.compile(part));
      } catch (IllegalArgumentException ignored) {
        // a part may itself contain non-ident chars (e.g., apostrophe-only
        // fragments after a weird split) -- just skip those.
      }
    }
    return out;
  }
}
