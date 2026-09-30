package org.arend.codeInsight

import org.arend.ArendTestBase

class ArendTypedHandlerTest : ArendTestBase() {
    private fun check(code: String, newCode: String, type: Char, withSelection: Boolean = true) {
        InlineFile(code).withCaret()
        if (withSelection) {
            myFixture.performEditorAction("EditorSelectWord")
        }
        myFixture.type(type)
        myFixture.checkResult(newCode)
    }

    fun `test parens`() = check("""\func f {-caret-}(a : Nat) => {?}""", """\func f {a : Nat} => {?}""", '{')

    fun `test braces`() = check("""\func f {-caret-}{a : Nat} => {?}""", """\func f (a : Nat) => {?}""", '(')

    fun `test nothing`() = check("""\func f {-caret-}(a : Nat} => {?}""", """\func f {a : Nat} => {?}""", '{')

    fun `test nothing 2`() = check("""\func f {-caret-}(a : Nat} => {?}""", """\func f *a : Nat} => {?}""", '*')

    fun `test nothing 3`() = check("""\func f {a : Nat} => {?{-caret-}}""", """\func f {a : Nat} => {?}""", '}', false)

    fun `test simple quoting parens`() = check("""\func f (a {-caret-}: Nat} => {?}""", """\func f (a (:) Nat} => {?}""", '(')

    fun `test simple quoting braces`() = check("""\func f (a {-caret-}: Nat} => {?}""", """\func f (a {:} Nat} => {?}""", '{')

    fun `test closing paren`() = check("""\func f (a : Nat){-caret-} => {?}""", """\func f {a : Nat} => {?}""", '}')

    fun `test closing brace`() = check("""\func f {a : Nat}{-caret-} => {?}""", """\func f (a : Nat) => {?}""", ')')

    fun `test parens goal`() = check("""\func f => {-caret-}{?}""", """\func f => ({?})""", '(')

    fun `test parens goal 2`() = check("""\func f => {-caret-}{?}""", """\func f => ({?}""", '(', false)

    fun `test braces 2`() = check("""\class Foo {\n  | foo : {-caret-}Nat\n}""", """\class Foo {\n  | foo : {Nat}\n}""", '{')

    fun `test colon plus superscript`() = check("""\func f (x :{-caret-} \Type) => x""", """\func f (x :⁺ \Type) => x""", '+', false)

    fun `test arrow plus superscript`() = check("""\func f (X : \Cat) => X ->{-caret-} Nat""", """\func f (X : \Cat) => X ->⁺ Nat""", '+', false)

    fun `test fat arrow plus superscript`() = check("""\func f (X : \Cat) => \lam (x : X) =>{-caret-} 0""", """\func f (X : \Cat) => \lam (x : X) =>⁺ 0""", '+', false)

    fun `test colon plus not replaced in string`() = check("""\func f => "x :{-caret-}"""", """\func f => "x :+"""", '+', false)

    fun `test colon plus not replaced in comment`() = check("""\func f => {- x :{-caret-} -} 0""", """\func f => {- x :+ -} 0""", '+', false)
}
