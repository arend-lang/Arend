package org.arend.cat;

import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

public class CatPatternMatchingTest extends TypeCheckingTestCase {
  @Test
  public void matchTest() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func foo (n : Nat) => n
      \\func f (d :+ D) : Nat
        | con n => foo n
      """, 1);
  }

  @Test
  public void matchTest2() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func foo (n : Nat) => n
      \\func f (d : D) : Nat
        | con n => foo n
      """);
  }

  @Test
  public void matchTest3() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func foo (n : Nat) => n
      \\func f (d :+ D) : Nat
        | con 0 => 0
        | con (suc n) => foo n
      """);
  }

  @Test
  public void matchTest4() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func foo (n : Nat) => n
      \\func f (d : D) : Nat
        | con 0 => 0
        | con (suc n) => foo n
      """);
  }

  @Test
  public void matchTest5() {
    typeCheckModule("""
      \\data C | conC (n :+ Nat)
      \\data D | conD (c :+ C)
      \\func foo (n : Nat) => n
      \\func f (d :+ D) : Nat
        | conD (conC n) => foo n
      """, 1);
  }

  @Test
  public void matchTest6() {
    typeCheckModule("""
      \\data C | conC (n :+ Nat)
      \\data D | conD (c :+ C)
      \\func foo (n : Nat) => n
      \\func f (d : D) : Nat
        | conD (conC n) => foo n
      """);
  }

  @Test
  public void matchTest7() {
    typeCheckModule("""
      \\data C | conC (n :+ Nat)
      \\data D | conD (c : C)
      \\func foo (n : Nat) => n
      \\func f (d :+ D) : Nat
        | conD (conC n) => foo n
      """);
  }

  @Test
  public void caseTest() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d :+ D) : Nat => \\case d \\with {
        | con n => 0
      }
      """, 1);
  }

  @Test
  public void caseTest2() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func foo (n : Nat) => n
      \\func f (d : D) : Nat => \\case d \\with {
        | con n => foo n
      }
      """);
  }

  @Test
  public void caseTest3() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d :+ D) : Nat => \\case d \\with {
        | con 0 => 0
        | con (suc n) => 0
      }
      """, 1);
  }

  @Test
  public void caseTest4() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func foo (n : Nat) => n
      \\func f (d : D) : Nat => \\case d \\with {
        | con 0 => 0
        | con (suc n) => foo n
      }
      """);
  }

  @Test
  public void caseTest5() {
    typeCheckModule("""
      \\data C | conC (n :+ Nat)
      \\data D | conD (c :+ C)
      \\func f (d :+ D) : Nat => \\case d \\with {
        | conD (conC n) => n
      }
      """, 1);
  }

  @Test
  public void caseTest6() {
    typeCheckModule("""
      \\data C | conC (n :+ Nat)
      \\data D | conD (c :+ C)
      \\func foo (n : Nat) => n
      \\func f (d : D) : Nat => \\case d \\with {
        | conD (conC n) => foo n
      }
      """);
  }
}
