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

  @Test
  public void caseCovariantArgTest() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d :+ D) : Nat => \\case d :+ _ \\with {
        | con n => 0
      }
      """);
  }

  @Test
  public void caseCovariantArgAsTest() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d :+ D) : Nat => \\case d \\as x :+ _ \\with {
        | con n => 0
      }
      """);
  }

  @Test
  public void caseCovariantArgMixedTest() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d :+ D) (m : Nat) : Nat => \\case d :+ _, m \\with {
        | con n, 0 => 0
        | con n, suc k => k
      }
      """);
  }

  @Test
  public void caseCovariantElimTest() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d :+ D) : Nat => \\case \\elim d \\with {
        | con n => 0
      }
      """);
  }

  @Test
  public void caseCovariantElimTest2() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d :+ D) : Nat => \\case \\elim d :+ _ \\with {
        | con n => 0
      }
      """);
  }

  @Test
  public void caseCovariantElimTest4() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func foo (n :+ Nat) => n
      \\func f (d :+ D) : Nat => \\case \\elim d \\with {
        | con n => foo n
      }
      """);
  }

  @Test
  public void caseCovariantElimError() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func foo (n : Nat) => n
      \\func f (d :+ D) : Nat => \\case \\elim d \\with {
        | con n => foo n
      }
      """, 1);
  }

  @Test
  public void caseCovariantElimError2() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func foo (n : Nat) => n
      \\func f (d :+ D) : Nat => \\case \\elim d : _ \\with {
        | con n => foo n
      }
      """, 1);
  }

  @Test
  public void caseCovariantElimError3() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d : D) : Nat => \\case \\elim d :+ _ \\with {
        | con n => n
      }
      """, 1);
  }

  @Test
  public void caseCovariantElimInvariantMarkerTest() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d : D) : Nat => \\case \\elim d : _ \\with {
        | con n => 0
      }
      """);
  }

  @Test
  public void caseCovariantElimError4() {
    typeCheckModule("""
      \\data D | con (n :+ Nat)
      \\func f (d :+ D) : Nat => \\case \\elim d : _ \\with {
        | con n => 0
      }
      """, 1);
  }

  @Test
  public void caseCovariantReturnTest() {
    typeCheckModule("""
      \\data D (n :+ Nat) | dcon
      \\func f (m : Nat) : D m => \\case m \\as x :+ _ \\return D x \\with {
        | _ => dcon
      }
      """);
  }

  @Test
  public void caseCovariantReturnError() {
    typeCheckModule("""
      \\data D (n : Nat) | dcon
      \\func f (m : Nat) : D m => \\case m \\as x :+ _ \\return D x \\with {
        | _ => dcon
      }
      """, 1);
  }

  @Test
  public void caseCovariantArgTypeTest() {
    typeCheckModule("""
      \\data D (n :+ Nat) | dcon
      \\func f (m : Nat) (q : D m) : Nat => \\case m \\as x :+ _, q :+ D x \\with {
        | _, _ => 0
      }
      """);
  }

  @Test
  public void caseCovariantArgTypeError() {
    typeCheckModule("""
      \\data D (n : Nat) | dcon
      \\func f (m : Nat) (q : D m) : Nat => \\case m \\as x :+ _, q :+ D x \\with {
        | _, _ => 0
      }
      """, 1);
  }

  @Test
  public void caseCovariantArgTypeError2() {
    typeCheckModule("""
      \\data D (n :+ Nat) | dcon
      \\func f (m : Nat) (q : D m) : Nat => \\case m \\as x :+ _, q : D x \\with {
        | _, _ => 0
      }
      """, 1);
  }

  @Test
  public void caseCovariantArgTypeError3() {
    typeCheckModule("""
      \\data D (n : Nat) | dcon
      \\func f (m : Nat) (q : D m) : Nat => \\case m \\as x :+ _, q : D x \\with {
        | _, _ => 0
      }
      """, 1);
  }

  @Test
  public void caseCovariantElimReturnTest() {
    typeCheckModule("""
      \\data D (n :+ Nat) | dcon
      \\func f (m : Nat) : D m => \\case \\elim m \\with {
        | _ => dcon
      }
      """);
  }

  @Test
  public void caseCovariantElimReturnError() {
    typeCheckModule("""
      \\data D (n : Nat) | dcon
      \\func f (m : Nat) : D m -> Nat => \\case \\elim m :+ _ \\with {
        | _ => \\lam _ => 0
      }
      """, 1);
  }

  @Test
  public void caseCovariantElimArgTypeTest() {
    typeCheckModule("""
      \\data D (n :+ Nat) | dcon
      \\func f (m : Nat) (q : D m) : Nat => \\case \\elim m, q \\with {
        | _, _ => 0
      }
      """);
  }
}
