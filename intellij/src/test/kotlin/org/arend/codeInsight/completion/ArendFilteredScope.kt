package org.arend.codeInsight.completion

class ArendFilteredScope : ArendCompletionTestBase() {
    fun `test super classes`() =
        checkCompletionVariants(
            "\\class A { | z : Nat }\n" +
            "\\class B\n" +
            "\\func f\n" +
            "\\data D\n" +
            "\\record R\n" +
            "\\class S => A\n" +
            "\\class C \\extends {-caret-} { | g : Nat }",
            listOf("A", "B", "C", "R", "S"))

    fun `test instance`() =
        checkCompletionVariants(
            "\\class A { | z : Nat }\n" +
            "\\class B\n" +
            "\\func f\n" +
            "\\data D\n" +
            "\\record R\n" +
            "\\class S => A\n" +
            "\\instance I : {-caret-}",
            listOf("A", "B", "S"))

    fun `test instance with args`() =
        checkCompletionVariants(
            "\\class A { | z : Nat }\n" +
            "\\class B\n" +
            "\\func f\n" +
            "\\data D\n" +
            "\\record R\n" +
            "\\class S => A\n" +
            "\\instance I : {-caret-} f D",
            listOf("A", "B", "S"))

    fun `test pattern`() =
        checkCompletionVariants(
            "\\class A { | z : Nat }\n" +
            "\\func f\n" +
            "\\data D | con1 | con2\n" +
            "\\data E | con3\n" +
            "\\record R\n" +
            "\\class S => A\n" +
            "\\func h (x : Nat) | {-caret-}",
            listOf("con1", "con2", "con3") + PRELUDE_CONSTRUCTORS)

    fun `test pattern argument`() =
        checkCompletionVariants(
            "\\class A { | z : Nat }\n" +
            "\\func f\n" +
            "\\data D | con1 Nat | con2\n" +
            "\\data E | con3\n" +
            "\\record R\n" +
            "\\class S => A\n" +
            "\\func h (x : Nat) | con1 {-caret-}",
            listOf("con1", "con2", "con3") + PRELUDE_CONSTRUCTORS)

    fun `test pattern type`() =
        checkCompletionVariants(
            "\\class A { | z : Nat }\n" +
            "\\func f\n" +
            "\\data D | con1 Nat | con2\n" +
            "\\data E | con3\n" +
            "\\record R\n" +
            "\\class S => A\n" +
            "\\func h (x y : Nat) | suc t, suc (s : {-caret-})",
            listOf("t", "A", "z", "f", "D", "E", "R", "S", "h", "con1", "con2", "con3") + PRELUDE_DEFINITIONS + DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST)

    fun `test new`() =
        checkCompletionVariants(
            "\\class A { | z : Nat }\n" +
            "\\class B\n" +
            "\\func f\n" +
            "\\data D\n" +
            "\\record R\n" +
            "\\class S => A\n" +
            "\\func h ' => \\new {-caret-}",
            listOf("A", "B", "R", "S"))

    fun `test new with args`() =
        checkCompletionVariants(
            "\\class A { | z : Nat }\n" +
            "\\class B\n" +
            "\\func f\n" +
            "\\data D\n" +
            "\\record R\n" +
            "\\class S => A\n" +
            "\\func h ' => \\new {-caret-} f B",
            listOf("A", "B", "R", "S"))

    fun `test new arg with level args`() =
        checkCompletionVariants(
            "\\class A { | z : Nat }\n" +
            "\\class B\n" +
            "\\func f\n" +
            "\\data D\n" +
            "\\record R\n" +
            "\\class S => A\n" +
            "\\func h ' => f \\new {-caret-} \\lp",
            listOf("A", "B", "R", "S"))

    fun `test new arg`() =
        checkCompletionVariants(
            "\\class A { | z : Nat }\n" +
            "\\class B\n" +
            "\\func f\n" +
            "\\data D\n" +
            "\\record R\n" +
            "\\class S => A\n" +
            "\\func h ' => f \\new {-caret-}",
            listOf("A", "B", "R", "S"))
}