package org.arend.codeInsight.completion

class ArendNoVariantsDelegatorTest : ArendCompletionTestBase() {
    fun testClassExtends() = checkCompletionVariants("""
        -- ! A.ard
        
        \class Clazz { }
        
        -- ! Main.ard
        
        \class Foo \extends Cl{-caret-}        
    """, listOf("Clazz"), CompletionCondition.CONTAINS)

}