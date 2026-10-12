package org.arend.term.prettyprint;

import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers which referable each name printed by a {@link PrettyPrintVisitor} stands for, if the visitor is given one
 * (see {@link PrettyPrintVisitor#setNameAnnotator}). A name is printed as a placeholder of the same length, so that the
 * layout is the same as without annotations; {@link #decode} puts the names back and returns where they are.
 *
 * <p>A placeholder is a start character, which encodes the lowest digit of its index, followed by continuation
 * characters, which encode the others; both are private use characters. Placeholders are therefore self-delimiting,
 * and a part of the text that is printed twice, to be measured first, only leaves an index unused.
 */
public class NameAnnotator {
  public enum Kind { REFERENCE, DECLARATION }

  public record Annotation(int start, int end, @NotNull Kind kind, @NotNull Referable referable) {}

  private record Entry(String name, Kind kind, Referable referable) {}

  private static final char START = '';
  private static final int START_COUNT = 0x1000;
  private static final char CONTINUATION = '';
  private static final int CONTINUATION_COUNT = 0x900;

  // Names of one character have room for START_COUNT indices; the others for more than are ever printed
  private final List<Entry> myShortNames = new ArrayList<>();
  private final List<Entry> myLongNames = new ArrayList<>();

  private static boolean isStart(char c) {
    return c >= START && c < START + START_COUNT;
  }

  private static boolean isContinuation(char c) {
    return c >= CONTINUATION && c < CONTINUATION + CONTINUATION_COUNT;
  }

  public @NotNull String reference(@Nullable Referable referable, @NotNull String name) {
    return annotate(referable, name, Kind.REFERENCE);
  }

  public @NotNull String declaration(@Nullable Referable referable, @NotNull String name) {
    return annotate(referable, name, Kind.DECLARATION);
  }

  private String annotate(Referable referable, String name, Kind kind) {
    if (referable == null || name.isEmpty()) return name;
    for (int i = 0; i < name.length(); i++) {
      if (isStart(name.charAt(i)) || isContinuation(name.charAt(i))) return name;
    }
    List<Entry> entries = name.length() == 1 ? myShortNames : myLongNames;
    int index = entries.size();
    if (name.length() == 1 && index >= START_COUNT || name.length() == 2 && index >= START_COUNT * CONTINUATION_COUNT) {
      return name;
    }
    entries.add(new Entry(name, kind, referable));

    char[] chars = new char[name.length()];
    chars[0] = (char) (START + index % START_COUNT);
    index /= START_COUNT;
    for (int i = 1; i < chars.length; i++) {
      chars[i] = (char) (CONTINUATION + index % CONTINUATION_COUNT);
      index /= CONTINUATION_COUNT;
    }
    return new String(chars);
  }

  private @Nullable Entry entry(CharSequence text, int start, int end) {
    int index = 0;
    for (int i = end - 1; i > start; i--) {
      index = index * CONTINUATION_COUNT + (text.charAt(i) - CONTINUATION);
    }
    index = index * START_COUNT + (text.charAt(start) - START);
    List<Entry> entries = end - start == 1 ? myShortNames : myLongNames;
    Entry entry = index < entries.size() ? entries.get(index) : null;
    return entry != null && entry.name.length() == end - start ? entry : null;
  }

  // The last character of the text as it is once decoded
  public char lastChar(@NotNull CharSequence text) {
    int end = text.length();
    char last = text.charAt(end - 1);
    if (!isStart(last) && !isContinuation(last)) return last;
    int start = end - 1;
    while (start > 0 && !isStart(text.charAt(start))) start--;
    Entry entry = entry(text, start, end);
    return entry == null ? last : entry.name.charAt(entry.name.length() - 1);
  }

  /**
   * Replaces the placeholders in {@code text} from {@code from} on with their names, and forgets them, so that the
   * indices can be used again. Returns the ranges of the names, which the replacement does not move.
   */
  public @NotNull List<Annotation> decode(@NotNull StringBuilder text, int from) {
    List<Annotation> result = new ArrayList<>();
    int i = from;
    while (i < text.length()) {
      if (!isStart(text.charAt(i))) {
        i++;
        continue;
      }
      int end = i + 1;
      while (end < text.length() && isContinuation(text.charAt(end))) end++;
      Entry entry = entry(text, i, end);
      if (entry != null) {
        text.replace(i, end, entry.name);
        result.add(new Annotation(i, end, entry.kind, entry.referable));
      }
      i = end;
    }
    myShortNames.clear();
    myLongNames.clear();
    return result;
  }
}
