package org.arend.typechecking.definition;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

/** Runs {@link AxiomTest} with lemma bodies cleared after typechecking, as in the CLI and the plugin. */
public class AxiomClearLemmasTest extends AxiomTest {
  @Override
  protected boolean clearLemmas() {
    return true;
  }

  @Test
  public void mutuallyRecursiveGoalTest() {
    typeCheckModule("""
      \\lemma foo (n : Nat) : 0 = 0
        | 0 => {?}
        | suc n => bar n
      \\lemma bar (n : Nat) : 0 = 0
        | 0 => idp
        | suc n => foo n
      """, 1);
    assertFalse(getDefinition("foo").getGoals().isEmpty());
    assertFalse(getDefinition("bar").getGoals().isEmpty());
  }
}
