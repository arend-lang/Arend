package org.arend.search

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import kotlinx.collections.immutable.toImmutableList
import org.arend.ArendTestBase
import org.arend.prelude.Prelude
import org.arend.util.FileUtils

class ArendReferenceSearchTest : ArendTestBase() {
    fun `test Nat is used in Prelude`() {
        InlineFile("""\func zero : Na{-caret-}t => 0""").withCaret()
        assertTrue(isUsedInPrelude(GlobalSearchScope.allScope(project)))
        assertFalse(isUsedInPrelude(GlobalSearchScope.projectScope(project)))
        assertTrue(isUsedInPrelude(GlobalSearchScope.moduleWithLibrariesScope(module)))
        assertFalse(isUsedInPrelude(GlobalSearchScope.moduleScope(module)))
    }

    fun `test path is used in Prelude`() {
        InlineFile("""\func zero-is-zero : 0 = 0 => pat{-caret-}h (\lam _ => 0)""").withCaret()
        assertTrue(isUsedInPrelude(GlobalSearchScope.allScope(project)))
        assertFalse(isUsedInPrelude(GlobalSearchScope.projectScope(project)))
        assertTrue(isUsedInPrelude(GlobalSearchScope.moduleWithLibrariesScope(module)))
        assertFalse(isUsedInPrelude(GlobalSearchScope.moduleScope(module)))
    }

    private fun isUsedInPrelude(scope: GlobalSearchScope): Boolean {
        val element = myFixture.elementAtCaret
        val search = ReferencesSearch.search(element, scope)
        return search.anyMatch { it.element.containingFile.name == Prelude.MODULE_PATH.toString() + FileUtils.EXTENSION }
    }

    fun `test findUsages upon alias`() {
        InlineFile("""\func foobar \alias f{-caret-}ubar => {?}""").withCaret()
        val element = myFixture.elementAtCaret
        val search = ReferencesSearch.search(element, GlobalSearchScope.allScope(project))
        assertTrue(search.toImmutableList().isEmpty())
    }

    fun `test findUsages upon alias 2`() {
        InlineFile("""\func foobar \alias f{-caret-}ubar => {?}\n\n\func bar => fubar""").withCaret()
        val element = myFixture.elementAtCaret
        val search = ReferencesSearch.search(element, GlobalSearchScope.allScope(project))
        assertTrue(search.toImmutableList().size == 1)
    }
}