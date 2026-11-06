package org.arend.frontend.query;

import org.arend.ext.module.LongName;
import org.arend.util.FileUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link FileUtils#isCorrectDefinitionName} is a hand-written copy of the {@code ID} rule of
 * {@code Arend.g4}; {@link ArendNameCharSet} reads the same rule out of the generated lexer. The
 * copy had lost the rule's mathematical-operator blocks, so every spec naming an operator such as
 * {@code ∪}, {@code ⊆} or {@code Big_∧} was rejected as an "invalid definition name" by
 * {@code -fu}/{@code -ch}/{@code -sc} even though the parser accepts it. Compare the two over
 * the whole BMP, as a name's first character and as a later one.
 */
public class DefinitionNameGrammarTest {
  private static boolean accepted(String name) {
    return FileUtils.isCorrectDefinitionName(new LongName(List.of(name)));
  }

  @Test
  public void validatorAgreesWithLexer() {
    List<String> mismatches = new ArrayList<>();
    for (int i = 0; i <= Character.MAX_VALUE; i++) {
      char c = (char) i;
      if (c == '.') continue;   // the long-name separator, never part of a segment
      boolean idChar = ArendNameCharSet.isIdChar(c);
      boolean startChar = idChar && !Character.isDigit(c) && c != '\'';
      if (accepted("a" + c) != idChar || accepted(String.valueOf(c)) != startChar) {
        mismatches.add(String.format("U+%04X", i));
      }
    }
    assertEquals("characters on which FileUtils and Arend.g4 disagree", List.of(), mismatches);
  }

  @Test
  public void operatorNamesAreAccepted() {
    for (String name : List.of("∪", "⊆", "Big_∧", "⊓-comm", "∀x")) {
      assertTrue(name, accepted(name));
    }
  }
}
