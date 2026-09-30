package org.arend.cat;

import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

public class CatLemmaTest extends TypeCheckingTestCase {
  // g's parameter is ordinary (invariant), so passing the covariant x to it directly is rejected
  // without relaxation -- unlike \case, which does not restrict its scrutinee this way (see
  // CoreDefinitionCheckerTest#caseOnCovariantParameterPassesDoubleCheck).
  @Test
  public void categoricalBodyStillRejectedWithoutLevel() {
    typeCheckModule("""
      \\data Empty
      \\func g (e : Empty) : Nat => 0
      \\lemma f (x :+ Empty) : Nat => g x
      """, -1);
  }

  @Test
  public void groupoidalContextRelaxesBody() {
    typeCheckModule("""
      \\data Empty
      \\func g (e : Empty) : Nat => 0
      \\lemma f (x :+ Empty) : Nat \\level+ (\\lam h tl tr => \\case h dleft) => g x
      """);
  }

  @Test
  public void groupoidalMultiParam() {
    typeCheckModule("""
      \\data Empty
      \\lemma f (x :+ Empty) (y :+ Nat) : Nat \\level+ (\\lam h tl tr => \\case (h dleft).1) => \\case x
      """);
  }

  @Test
  public void groupoidalLeadingInvariantParam() {
    typeCheckModule("""
      \\data Empty
      \\lemma f (n : Nat) (x :+ Empty) : Nat \\level+ (\\lam h tl tr => \\case n, h dleft \\with { | 0, () | suc n, () }) => \\case x
      """);
  }

  @Test
  public void groupoidalInterleavedInvariantParam() {
    typeCheckModule("""
      \\data Empty
      \\lemma f (x :+ Empty) (n : Nat) (y :+ Empty) : Nat \\level+ (\\lam h tl tr => \\case (h dleft).2) => \\case x
      """);
  }

  @Test
  public void groupoidalDependentParams() {
    typeCheckModule("""
      \\data Empty
      \\lemma f (e : Empty) (C :+ \\Cat0) (x :+ C) : Nat \\level+ (\\lam h tl tr => \\case e) => \\case e
      """);
  }

  @Test
  public void groupoidalResultTypeDependsOnParam() {
    typeCheckModule("""
      \\data Empty
      \\lemma f (e : Empty) (A :+ \\Cat0) : A \\level+ (\\lam h tl tr => \\case e) => \\case e
      """);
  }

  @Test
  public void oldFormStillWorksWithCategoricalParams() {
    typeCheckModule("""
      \\data Empty
      \\lemma f (x e :+ Empty) : Empty \\level (\\lam a b => \\case a) => e
      """);
  }

  @Test
  public void levelPlusBadProofReported() {
    typeCheckModule("""
      \\data Empty
      \\lemma f (x :+ Empty) : Nat \\level+ 0 => \\case x
      """, -1);
  }

  @Test
  public void levelPlusZeroCategoricalParamsAllowed() {
    typeCheckModule("""
      \\data Empty
      \\lemma f (e : Empty) : Empty \\level+ (\\lam h tl tr => \\case e) => e
      """);
  }

  @Test
  public void levelPlusNotSFuncReported() {
    typeCheckModule("""
      \\data Empty
      \\func f (x :+ Empty) : Nat \\level+ (\\lam h tl tr => \\case h dleft) => 0
      """, 1);
  }

  @Test
  public void axiomTest() {
    typeCheckModule("""
      \\data Empty
      \\axiom f (x :+ Empty) : Nat \\level+ (\\lam h tl tr => \\case h dleft)
      """);
  }
}
