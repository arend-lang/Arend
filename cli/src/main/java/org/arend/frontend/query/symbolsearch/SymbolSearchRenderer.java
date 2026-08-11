package org.arend.frontend.query.symbolsearch;

import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.query.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renders a matched {@link SymbolIndex.Entry} for the {@code -ss} text listing: the location
 * header, the {@code [library::]module:LongName [KIND]} line with the query's matched parts
 * wrapped in ANSI green, and the (optionally multi-line) signature. Also exposes
 * {@link #prefixSegments} — the entry's enclosing-path segments — which the tool's long-name
 * matcher shares.
 */
final class SymbolSearchRenderer {
  private SymbolSearchRenderer() {}

  /** The plain (un-highlighted) {@code [library::]module:LongName} label for an entry. */
  static String plainQualifiedName(boolean showLibrary, String libName,
                                   SymbolIndex.Entry e, String renderedLongName) {
    return QualifiedName.format(showLibrary, libName, e.modulePath().toString(), renderedLongName);
  }

  /** Appends the full multi-line text entry for one hit (location header, name line, signature). */
  static void appendEntry(StringBuilder out, boolean showLibrary, String libName, SymbolIndex.Entry e,
                          LibraryManager libraryManager, List<SymbolPattern> patterns) {
    String header;
    if (e.absoluteFile() == null || e.absoluteFile().isEmpty()) {
      header = "<" + libName + ":" + e.modulePath() + ">";
    } else {
      header = PathDisplayUtils.shorten(e.absoluteFile(), libraryManager)
          + ":" + (e.line() == 0 ? "?" : e.line()) + ":" + (e.column() == 0 ? "?" : e.column());
    }
    out.append(header).append('\n');
    out.append(renderQualifiedName(showLibrary, libName, e, patterns))
        .append("  [").append(e.kind().name()).append("]\n");
    if (!e.signature().isEmpty()) {
      // A container signature (\class/\record/\data) is multi-line; indent every
      // line by two spaces so the field/constructor lines nest under the header.
      out.append("  ").append(e.signature().replace("\n", "\n  ")).append('\n');
    }
    out.append('\n');
  }

  /**
   * The enclosing module and namespace path of an entry, as a segment list: the
   * module-path segments followed by the in-module long-name segments, minus the
   * final segment (the definition's own short name). This is what the leading
   * segments of a {@link SymbolPattern.Mode#LONGNAME} query match against.
   */
  static List<String> prefixSegments(SymbolIndex.Entry e) {
    List<String> segments = new ArrayList<>(e.modulePath().toList());
    for (String s : e.longName().split("\\.")) if (!s.isEmpty()) segments.add(s);
    if (!segments.isEmpty()) segments.removeLast();   // drop the short name
    return segments;
  }

  /**
   * Renders {@code [library::]module:LongName} with every query-matched part wrapped in ANSI
   * green. For ordinary patterns only the short name's matched span lights up; for a
   * {@link SymbolPattern.Mode#LONGNAME} pattern every matched segment does (short name plus
   * the enclosing path segments its leading parts matched). Non-JSON path only.
   */
  private static String renderQualifiedName(boolean showLibrary, String libName,
                                            SymbolIndex.Entry e, List<SymbolPattern> patterns) {
    String moduleStr = e.modulePath().toString();
    String longNameStr = e.longName();
    String shortName = e.shortName();

    List<int[]> moduleRanges = new ArrayList<>();
    List<int[]> longNameRanges = new ArrayList<>();

    // Segment offsets are needed only when a LONGNAME pattern is present.
    List<String> moduleSegments = null;
    int[] moduleOffsets = null, longOffsets = null;

    for (SymbolPattern p : patterns) {
      if (p.mode() == SymbolPattern.Mode.LONGNAME) {
        if (moduleSegments == null) {
          moduleSegments = e.modulePath().toList();
          moduleOffsets = segmentOffsets(moduleSegments);
          longOffsets = segmentOffsets(splitDot(longNameStr));
        }
        List<String> prefix = prefixSegments(e);
        SymbolPattern.LongNameHighlights hl = p.longNameHighlights(prefix, shortName);
        if (hl == null) continue;   // this OR-ed pattern is not the one that matched
        // Final segment -> the short name (trailing segment of the long name).
        int shortOff = shortNameOffset(longNameStr, shortName);
        if (shortOff >= 0) {
          for (int[] r : hl.shortNameRanges()) longNameRanges.add(new int[]{shortOff + r[0], shortOff + r[1]});
        }
        // Leading segments -> the module path or the long name's leading segments, by index.
        int msz = moduleSegments.size();
        List<List<int[]>> prefixRanges = hl.prefixRanges();
        for (int i = 0; i < prefixRanges.size(); i++) {
          List<int[]> rr = prefixRanges.get(i);
          if (rr.isEmpty()) continue;
          if (i < msz) {
            for (int[] r : rr) moduleRanges.add(new int[]{moduleOffsets[i] + r[0], moduleOffsets[i] + r[1]});
          } else {
            int j = i - msz;   // index into the long name's leading segments
            for (int[] r : rr) longNameRanges.add(new int[]{longOffsets[j] + r[0], longOffsets[j] + r[1]});
          }
        }
      } else {
        int off = shortNameOffset(longNameStr, shortName);
        if (off < 0) continue;
        for (int[] r : p.highlightRanges(shortName)) longNameRanges.add(new int[]{off + r[0], off + r[1]});
      }
    }

    return QualifiedName.format(showLibrary, libName,
        MatchHighlighter.highlight(moduleStr, moduleRanges), MatchHighlighter.highlight(longNameStr, longNameRanges));
  }

  /** Position of the trailing short name within its long name (-1 if absent). */
  private static int shortNameOffset(String longName, String shortName) {
    return longName.endsWith(shortName) ? longName.length() - shortName.length()
                                        : longName.lastIndexOf(shortName);
  }

  /** Splits a dot-joined name into segments; when called on an empty string returns an empty list. */
  private static List<String> splitDot(String s) {
    List<String> segments = new ArrayList<>();
    if (!s.isEmpty()) Collections.addAll(segments, s.split("\\."));
    return segments;
  }

  /** Char offset of each segment within {@code segments} joined by '.'. */
  private static int[] segmentOffsets(List<String> segments) {
    int[] offs = new int[segments.size()];
    int pos = 0;
    for (int i = 0; i < segments.size(); i++) {
      offs[i] = pos;
      pos += segments.get(i).length() + 1;   // + 1 for the '.' separator
    }
    return offs;
  }
}
