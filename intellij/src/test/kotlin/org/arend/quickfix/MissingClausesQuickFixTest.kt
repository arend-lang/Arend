package org.arend.quickfix

class MissingClausesQuickFixTest: QuickFixTestBase() {
    private val listDefinition =
        """
        \data List (A : \Type)
          | nil
          | :: (x : A) (xs : List A)
        """

    private val listDefinition2 =
        """
        \data List (A : \Type)
          | nil
          | :: {A} (List A) 
        """

    private val listDefinition3 =
        """
        \data List (A : \Type)
          | nil
          | :: {x : A} {xs : List A} 
        """

    private val fooDefinition =
        """ 
        \data Nat2
          | zero2
          | suc2 (a : Nat2)
            
        \data Foo
          | foo1
          | foo2 {a : Nat2} (b : Nat2)
        """

    private val orDefinition =
        """
        \data Or (A B : \Type) | inl A | inr B 
        """

    private val pairDefinition = "\\record Pair (A B : \\Type) | fst : A | snd : B"

    fun testBasicPattern() = typedQuickFixTest("Implement",
        """
        $listDefinition
    
        \func length{-caret-} {A : \Type} (l : List A) : Nat \with {
          | nil => 0
          | :: x nil => 1 
        }
        """, """ 
        $listDefinition

        \func length {A : \Type} (l : List A) : Nat \with {
          | nil => 0
          | :: x nil => 1
          | :: x (:: x1 xs) => {?}
        }
        """)

    fun testBasicElim() = typedQuickFixTest("Implement",
        """
        $listDefinition
    
        \func length{-caret-} (A : \Type) (l : List A) (n : Nat) : Nat \elim l
          | nil => n
          | :: x nil => n
        """, """ 
        $listDefinition

        \func length (A : \Type) (l : List A) (n : Nat) : Nat \elim l
          | nil => n
          | :: x nil => n
          | :: x (:: x1 xs) => {?}
        """)

    fun testImplicitPattern() = typedQuickFixTest("Implement",
        """
        --! Main.ard
        \$listDefinition2
    
        \func length{-caret-} {A : \Type} (l : List A) : Nat \with
          | nil => 0
          | :: {x} nil => 1
        """, """ 
        \$listDefinition2

        \func length {A : \Type} (l : List A) : Nat \with
          | nil => 0
          | :: {x} nil => 1
          | :: (:: l) => {?}
        """)

    fun testImplicitPattern2() = typedQuickFixTest("Implement",
            """
        --! Main.ard
        \$listDefinition3
    
        \func length{-caret-} {A : \Type} {l : List A} : Nat \with
          | {_}, {nil} => 0
          | {_}, {:: {x} {nil}} => 1
        """, """ 
        \$listDefinition3

        \func length {A : \Type} {l : List A} : Nat \with
          | {_}, {nil} => 0
          | {_}, {:: {x} {nil}} => 1
          | {A}, {:: {x} {::}} => {?}
        """)

    fun testBasicTwoPatterns() = typedQuickFixTest("Implement",
        """
        --! Main.ard 
        \$listDefinition

        \func lol{-caret-} {A : \Type} (l : List A) (l2 : List A) : Nat \with
          | nil, nil => 1
        """, """
        \$listDefinition

        \func lol {A : \Type} (l : List A) (l2 : List A) : Nat \with
          | nil, nil => 1
          | :: x xs, l2 => {?}
          | nil, :: x xs => {?} 
        """)

    fun testMixedTwoPatterns() = typedQuickFixTest("Implement",
        """
        --! Main.ard
        \$fooDefinition        
        
        \func bar{-caret-} (a : Foo) {b : Foo} : Nat2 \with
          | foo1, {foo1} => zero2
          | foo2 {zero2} (suc2 zero2), {foo2 {zero2} (suc2 zero2)} => zero2 
        ""","""
        \$fooDefinition
        
        \func bar (a : Foo) {b : Foo} : Nat2 \with
          | foo1, {foo1} => zero2
          | foo2 {zero2} (suc2 zero2), {foo2 {zero2} (suc2 zero2)} => zero2
          | foo1, {foo2 b} => {?}
          | foo2 {suc2 a} b => {?}
          | foo2 {zero2} zero2 => {?}
          | foo2 {zero2} (suc2 (suc2 a)) => {?}
          | foo2 {zero2} (suc2 zero2), {foo1} => {?}
          | foo2 {zero2} (suc2 zero2), {foo2 {suc2 a} b} => {?}
          | foo2 {zero2} (suc2 zero2), {foo2 {zero2} zero2} => {?}
          | foo2 {zero2} (suc2 zero2), {foo2 {zero2} (suc2 (suc2 a))} => {?}    
        """)

    fun testElim() = typedQuickFixTest("Implement",
        """
        --! Main.ard
        \$orDefinition
        
        \func Or-to-||{-caret-} {A B : \Prop} (a-or-b : Or A B) : Or A B \elim a-or-b
          | inl a => inl a
        """, """
        \$orDefinition
            
        \func Or-to-|| {A B : \Prop} (a-or-b : Or A B) : Or A B \elim a-or-b
          | inl a => inl a
          | inr b => {?}
        """)

    fun testCase() = typedQuickFixTest("Implement",
        """
        --! Main.ard
        \$orDefinition
        
        \func Or-to-|| {A B : \Prop} (a-or-b : Or A B) : Or A B => \case a-or-b \with {
          | inl a => inl a{-caret-}
        }
        """, """
        \$orDefinition
            
        \func Or-to-|| {A B : \Prop} (a-or-b : Or A B) : Or A B => \case a-or-b \with {
          | inl a => inl a
          | inr b => {?}
        }
        """)

    fun testCaseWithoutBraces() = typedQuickFixTest("Implement",
            """
               \func test (n : Nat) : Nat => \case n{-caret-} \with 
            """, """
               \func test (n : Nat) : Nat => \case n \with {
                 | 0 => {?}
                 | suc n1 => {?}
               }
            """)

    fun testCaseWithoutWith() = typedQuickFixTest("Implement",
            """
               \func foo : Nat => \case 0{-caret-}
            """, """
               \func foo : Nat => \case 0 \with {
                 | 0 => {?}
                 | suc n => {?}
               } 
            """)

    fun testResolveReference() = typedQuickFixTest("Implement",
        """
        --! Logic.ard 
        \data || (A B : \Type)
          | byLeft A
          | byRight B
 
        --! Main.ard    
        \import Logic ()

        \func byLeft => 101

        \func lol{-caret-} {A B : \Type} (a b : Logic.|| A B) : Nat
          | Logic.byLeft x, Logic.byLeft y => {?} 
        """, """
        \import Logic (byRight, ||)

        \func byLeft => 101

        \func lol {A B : \Type} (a b : Logic.|| A B) : Nat
          | Logic.byLeft x, Logic.byLeft y => {?}
          | byRight b, b1 => {?}
          | ||.byLeft x, byRight b => {?} 
        """)

    fun testNaturalNumbers() = typedQuickFixTest("Implement",
        """
        --! Main.ard    
        \func plus{-caret-} (a b : Nat) : Nat
          | 0, 2 => 0        
        """, """
        \func plus (a b : Nat) : Nat
          | 0, 2 => 0
          | suc a, b => {?}
          | 0, 0 => {?}
          | 0, 1 => {?}
          | 0, suc (suc (suc b)) => {?}    
        """)

    fun testIntegralNumbers() = typedQuickFixTest("Implement",
        """
        \func{-caret-} abs (a : Int) : Int
          | -3 => 3
          | 3 => 3 
        """, """
        \func abs (a : Int) : Int
          | -3 => 3
          | 3 => 3
          | 0 => {?}
          | 1 => {?}
          | 2 => {?}
          | pos (suc (suc (suc (suc n)))) => {?}
          | neg 0 => {?}
          | -1 => {?}
          | -2 => {?}
          | neg (suc (suc (suc (suc n)))) => {?}    
        """)

    fun testEmpty1() = typedQuickFixTest("Implement",
            """
                \func foo{-caret-} (x : Nat) : Nat
            """, """
                \func foo (x : Nat) : Nat
                  | 0 => {?}
                  | suc x => {?}
            """)

    fun testEmpty2() = typedQuickFixTest("Implement",
            """
                \func foo (x : Nat) : Nat => \case x {-caret-}\with {  }
            """, """
                \func foo (x : Nat) : Nat => \case x \with {
                  | 0 => {?}
                  | suc n => {?}
                }
            """)

    fun testEmpty3() = typedQuickFixTest("Implement",
            """
                \func foo{-caret-} (x : Nat) : Nat \elim x
            """, """
                \func foo (x : Nat) : Nat \elim x
                  | 0 => {?}
                  | suc x => {?}
            """)

    fun testRenamer() = typedQuickFixTest("Implement",
            """
               \func foo{-caret-} (n m : Nat) : Nat 
            """, """
               \func foo (n m : Nat) : Nat
                 | 0, 0 => {?}
                 | 0, suc m => {?}
                 | suc n, 0 => {?}
                 | suc n, suc m => {?} 
            """)

    fun testTuple1() = typedQuickFixTest("Implement",
            """
               \func test{-caret-} {A : \Type} (B : A -> \Type) (p : \Sigma (x : A) (B x)) : A \elim p 
            """, """
               \func test {A : \Type} (B : A -> \Type) (p : \Sigma (x : A) (B x)) : A \elim p
                 | (x,b) => {?} 
            """)

    fun testTuple2() = typedQuickFixTest("Implement",
            """
               $pairDefinition

               \func test2{-caret-} {A B : \Type} (p : Pair A B) : A \elim p 
            """, """
               $pairDefinition

               \func test2 {A B : \Type} (p : Pair A B) : A \elim p
                 | (a,b) => {?} 
            """)

    fun testTuple3() = typedCheckNoQuickFixes("Implement",
            """
               \func test{-caret-} {A : \Type} (B : A -> \Type) (p : \Sigma (x : A) (B x)) : A 
            """)

    fun testFixInArendCoClauseDef() = typedQuickFixTest("Implement", """
               \record R (f : Nat -> Nat)
               
               \func foo (n : Nat) : R \cowith
                 | f{-caret-} x \with {}
    """, """
               \record R (f : Nat -> Nat)
               
               \func foo (n : Nat) : R \cowith
                 | f x \with {
                   | 0 => {?}
                   | suc x => {?}
                 }
    """)

    fun testFixInCoClauseDefWithoutFC() = typedQuickFixTest("Implement", """
               \record R (f : Nat -> Nat)
               
               \func foo (n : Nat) : R \cowith
                 | f{-caret-} x \with
    """, """
               \record R (f : Nat -> Nat)

               \func foo (n : Nat) : R \cowith
                 | f x \with {
                   | 0 => {?}
                   | suc x => {?}
                 }
    """)

    fun testFixInCoClauseDefWithoutWith() = typedQuickFixTest("Implement", """
               \record R (f : Nat -> Nat)
               
               \func foo (n : Nat) : R \cowith
                 | f{-caret-} x
    """, """
               \record R (f : Nat -> Nat)

               \func foo (n : Nat) : R \cowith
                 | f x \with {
                   | 0 => {?}
                   | suc x => {?}
                 }
    """)

    fun test_145() = typedQuickFixTest("Implement", """
               \data Empty

               \func isNeg (x : Nat) : \Type
                 | 0 => Empty
                 | suc x => Empty

               \func test{-caret-} {n : Nat} (p : isNeg n) : Empty 
    """, """
               \data Empty

               \func isNeg (x : Nat) : \Type
                 | 0 => Empty
                 | suc x => Empty

               \func test {n : Nat} (p : isNeg n) : Empty
                 | {0}, ()
    """)

    fun test_88_1() = typedQuickFixTest("Implement", """
       \data D | cons (a b : Nat) (a = b)

       \func{-caret-} lol2 (d : D) : Nat \elim d 
    """, """
       \data D | cons (a b : Nat) (a = b)

       \func lol2 (d : D) : Nat \elim d
         | cons a b p => {?} 
    """)

    fun test_88_2() = typedQuickFixTest("Implement", """
       \data Tree | leaf | branch Tree Tree

       \func foo{-caret-} (z : Tree) : Nat
    """, """
       \data Tree | leaf | branch Tree Tree

       \func foo (z : Tree) : Nat
         | leaf => {?}
         | branch z1 z2 => {?}        
    """)

    fun test_88_3() = typedQuickFixTest("Implement", """
       \data Tree | leaf | branch Tree Tree

       \func countLeaves{-caret-} (z1 z2 : Tree) : Nat 
    ""","""
       \data Tree | leaf | branch Tree Tree

       \func countLeaves (z1 z2 : Tree) : Nat
         | leaf, leaf => {?}
         | leaf, branch z2 z1 => {?}
         | branch z1 z2, leaf => {?}
         | branch z1 z2, branch z3 z4 => {?} 
    """)

    fun test_88_4() = typedQuickFixTest("Implement", """
       \func test{-caret-} (z : Nat) : Nat \elim z 
    """, """
       \func test{-caret-} (z : Nat) : Nat \elim z
         | 0 => {?}
         | suc z => {?} 
    """)

    fun test_88_5() = typedQuickFixTest("Implement", """
       \func lol{-caret-} (a : Nat) (b : Nat) (c : Nat) \elim b, c
    """, """
       \func lol (a : Nat) (b : Nat) (c : Nat) \elim b, c
         | 0, 0 => {?}
         | 0, suc c => {?}
         | suc b, 0 => {?}
         | suc b, suc c => {?}
    """)

    fun test_88_6() = typedQuickFixTest("Implement", """
       \data D | con (t s : D)
       
       \func foo{-caret-} (t : D) : Nat 
    """, """
       \data D | con (t s : D)
       
       \func foo (t : D) : Nat
         | con t s => {?} 
    """)

    fun test_88_7() = typedQuickFixTest("Implement", """
       \data Tree (A : \Type) | Leaf | Branch (Tree A) A (Tree A)
       
       \func foo{-caret-} {A : \Type} (t : Tree A) : Nat \elim t
    """, """
       \data Tree (A : \Type) | Leaf | Branch (Tree A) A (Tree A)
       
       \func foo {A : \Type} (t : Tree A) : Nat \elim t
         | Leaf => {?}
         | Branch t1 a t2 => {?}
    """)

    fun test_86_1() = typedQuickFixTest("Implement", """
       \func foo{-caret-} (x y : Nat) (p : x = y) : Nat \elim p 
    """, """
       \func foo (x y : Nat) (p : x = y) : Nat \elim p
         | idp => {?}
    """)

    fun test_86_2() = typedQuickFixTest("Implement", """
       \func foo{-caret-} (x y : Nat) (p : x = y) : Nat \elim x, p 
    """, """
       \func foo (x y : Nat) (p : x = y) : Nat \elim x, p
         | 0, idp => {?}
         | suc x, idp => {?}
    """)

    fun test_86_3() = typedQuickFixTest("Implement", """
       \func foo (x y : Nat) (p : x = y) : Nat => \case \elim x, p{-caret-} \with {}
    """, """
       \func foo (x y : Nat) (p : x = y) : Nat => \case \elim x, p \with {
         | 0, idp => {?}
         | suc x, idp => {?}
       }
    """)

    fun test_86_4() = typedQuickFixTest("Implement", """
       \func foo (x y : Nat) (p : x = y) : Nat => \case x \as x', p \as p' : x' = y{-caret-} \with {} 
    """, """
       \func foo (x y : Nat) (p : x = y) : Nat => \case x \as x', p \as p' : x' = y \with {
         | 0, idp => {?}
         | suc x', idp => {?}
       } 
    """)

    fun test_86_5() = typedQuickFixTest("Implement", """
       \func foo (x y : Nat) (p : x = y) : Nat => \case x, p{-caret-} \with {}
    """, """
       \func foo (x y : Nat) (p : x = y) : Nat => \case x, p \with {
         | 0, p1 => {?}
         | suc n, p1 => {?}
       }
    """)

    fun test86_6() = typedQuickFixTest("Implement", """
       \func foo{-caret-} (x : Nat) (p : x = x) : Nat \elim x, p 
    """, """
       \func foo{-caret-} (x : Nat) (p : x = x) : Nat \elim x, p
         | 0, p => {?}
         | suc x, p => {?} 
    """)

    fun test86_7() = typedQuickFixTest("Implement", """
       \data Lol | idp

       \func foo{-caret-} (x y : Nat) (p : x = y) : Nat \elim p 
    """, """
       \import Prelude 
       
       \data Lol | idp

       \func foo (x y : Nat) (p : x = y) : Nat \elim p
         | Prelude.idp => {?} 
    """)

    fun `test shadowed names from parent case`() = typedQuickFixTest("Implement", """
        $listDefinition
        
        \func foo {A : \Type} (list : List A) : Nat \elim list
          | nil => 0
          | :: x xs => \case{-caret-} xs \with {}
    """, """
        $listDefinition
        
        \func foo {A : \Type} (list : List A) : Nat \elim list
          | nil => 0
          | :: x xs => \case xs \with {
            | nil => {?}
            | :: x1 xs1 => {?}
          }
    """)

    fun `test avoid shadowing let binding`() = typedQuickFixTest("Implement", """
       \data Wrapper (A : \Type) | wrapped (x : A)
        
       \func foo (w : Wrapper Nat) : Nat => \let x => 0 \in \case \elim{-caret-} w \with 
    """, """
       \data Wrapper (A : \Type) | wrapped (x : A)
        
       \func foo (w : Wrapper Nat) : Nat => \let x => 0 \in \case \elim w \with {
         | wrapped x1 => {?}
       }        
    """)

    fun `test avoid shadowing parameter`() = typedQuickFixTest("Implement", """
       \data Wrapper (A : \Type) | wrapped (x : A)
        
       \func foo (w x : Wrapper Nat) : Nat => \case \elim{-caret-} w \with 
    """, """
       \data Wrapper (A : \Type) | wrapped (x : A)
        
       \func foo (w x : Wrapper Nat) : Nat => \case \elim w \with {
         | wrapped x1 => {?}
       }        
    """)

    fun `test names of eliminated variables may be reused`() = typedQuickFixTest("Implement", """
       \func foo (n : Nat) : Nat => \case \elim{-caret-} n \with
    """, """
       \func foo (n : Nat) : Nat => \case \elim n \with {
         | 0 => {?}
         | suc n => {?}
       }
    """)

    fun `test names of eliminated variables may be reused 2`() = typedQuickFixTest("Implement", """
        \data Wrapper (A : \Type) | wrapped (x : A)
        
        \func foo : Nat => \let x => wrapped 1 \in \case \elim{-caret-} x \with
    """, """
        \data Wrapper (A : \Type) | wrapped (x : A)

        \func foo : Nat => \let x => wrapped 1 \in \case \elim x \with {
          | wrapped x => {?}
        }
    """)


    fun `test names of eliminated variables may be reused 3`() = typedQuickFixTest("Implement", """
        \data Wrapper (A : \Type) | wrapped (x : A)
        
        \func foo (x : Nat) : Nat => \let x => wrapped 1 \in \case \elim{-caret-} x \with
    """, """
        \data Wrapper (A : \Type) | wrapped (x : A)

        \func foo (x : Nat) : Nat => \let x => wrapped 1 \in \case \elim x \with {
          | wrapped x1 => {?}
        }
    """)

    fun `test avoid shadowing function parameter`() = typedQuickFixTest("Implement", """
        \data Wrapper (A : \Type) | wrapped (x : A)
        
        {-caret-}\func foo (w x : Wrapper Nat) : Nat \elim w
    """, """
        \data Wrapper (A : \Type) | wrapped (x : A)

        \func foo (w x : Wrapper Nat) : Nat \elim w
          | wrapped x1 => {?}
    """)

    fun `test names of eliminated function parameters may be reused`() = typedQuickFixTest("Implement", """
        \data Wrapper (A : \Type) | wrapped (x : A)
        
        {-caret-}\func foo (x : Wrapper Nat) : Nat \elim x
    """, """
        \data Wrapper (A : \Type) | wrapped (x : A)

        \func foo (x : Wrapper Nat) : Nat \elim x
          | wrapped x => {?}
    """)

    fun `test names of eliminated function parameters may be reused 2`() = typedQuickFixTest("Implement", """
        \data Wrapper (A : \Type) | wrapped (x : A)
        
        {-caret-}\func foo (x : Nat -> Nat) (w : Wrapper Nat) : Nat
    """, """
        \data Wrapper (A : \Type) | wrapped (x : A)

        \func foo (x : Nat -> Nat) (w : Wrapper Nat) : Nat
          | x, wrapped x1 => {?}
    """)
}