package org.arend.codeInsight.completion

class ArendNoVariantsDelegatorTest : ArendCompletionTestBase() {
    fun testClassExtends() = checkCompletionVariants("""
        -- ! A.ard
        
        \class Clazz { }
        
        -- ! Main.ard
        
        \class Foo \extends Cl{-caret-}        
    """, listOf("Clazz"), CompletionCondition.CONTAINS)

    fun testTrailingDocCommentsKept() = doSingleCompletion("""
        \record R {
          | a : Nat           -- | doc a
          | b : Nat           -- | doc b
          | c : Nat -> Nat = cartan-pair{-caret-} -- | doc c
          | d : Nat           -- | doc d

          \func cartan-pairing => 0
        }
    """, """
        \record R {
          | a : Nat           -- | doc a
          | b : Nat           -- | doc b
          | c : Nat -> Nat = R.cartan-pairing{-caret-} -- | doc c
          | d : Nat           -- | doc d

          \func cartan-pairing => 0
        }
    """)
}