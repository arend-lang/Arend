package org.arend.search.proof

import org.arend.ArendTestBase
import org.arend.proof.ProofSearchQuery

class ParsingTest : ArendTestBase() {
    private fun doTest(pattern: String, serialized: String) {
        val query = ProofSearchQuery.fromString(pattern)
        assertTrue("Expected correct parsing", query is ProofSearchQuery.ParsingResult.OK)
        val result = (query as ProofSearchQuery.ParsingResult.OK).value
        assertTrue("Expected: $serialized, got: $result", result.toString() == serialized)
    }

    private fun doTestFail(pattern: String, serialized: String) {
        val query = ProofSearchQuery.fromString(pattern)
        assertTrue("Expected failure", query is ProofSearchQuery.ParsingResult.Error)
        val result = (query as ProofSearchQuery.ParsingResult.Error).range
        val replaced = pattern.replaceRange(IntRange(result.proj1, result.proj2 - 1), "!".repeat(result.proj2 - result.proj1))
        assertTrue("Expected: $serialized, got: $replaced", replaced == serialized)
    }

    fun test1() = doTest("a -> b", "<a> --> <b>")
    fun test2() = doTest("a", "<a>")
    fun test3() = doTest("a \\and b", "<a  \\and  b>")
    fun test4() = doTestFail("a ->", "a !!")
    fun test5() = doTestFail("-> a", "!! a")
    fun test6() = doTestFail("a \\and", "a !!!!")
    fun test7() = doTestFail("\\and a", "!!!! a")
    fun test8() = doTest("a b", "<[a b]>")
    fun test9() = doTest("a \\and b -> c \\and d -> e \\and f", "<a  \\and  b> -> <c  \\and  d> --> <e  \\and  f>")
    fun test10() = doTestFail("", "")
    fun test11() = doTestFail("()", "(!")
    fun test12() = doTest("(a)", "<a>")
    fun test13() = doTestFail("a \\and ->", "a \\and !!")
    fun test14() = doTestFail("(a", "!a")
    fun test15() = doTestFail("a)", "a!")
    fun test16() = doTestFail("((a)", "!(a)")
    fun test17() = doTestFail("{)", "{!")
    fun test18() = doTestFail("-> ->", "!! ->")
    fun test19() = doTest("_ = _ \\and _ -> _ = (_ = _)", "<[_ = _]  \\and  _> --> <[_ = [_ = _]]>")
    fun test20() = doTestFail("\\and", "!!!!")
    fun test21() = doTest("_ = _ \\and _", "<[_ = _]  \\and  _>")
}