package org.arend.quickfix

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.psi.ext.ArendReferenceElement

class StaleResolveAfterImportFixTest : QuickFixTestBase() {
    private fun checkResolvedAreValid() {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val broken = PsiTreeUtil.findChildrenOfType(myFixture.file, ArendReferenceElement::class.java).mapNotNull { ref ->
            val target = ref.resolvePsi() ?: return@mapNotNull null
            try {
                SmartPointerManager.createPointer(target)
                null
            } catch (e: Exception) {
                "'${ref.text}' at ${ref.textRange} -> ${target.javaClass.simpleName} '${target.text}': $e"
            }
        }
        assertEmpty("References resolved to invalid PSI:\n", broken)
    }

    fun `test reference to a parameter after the parameter is reparsed`() {
        configure("""
            -- ! B.ard
            \class C (q : Nat) | r : Nat -> Nat
            \func u {t : Nat{-caret-}} (c : C) => c.r t
        """)
        myFixture.doHighlighting()
        checkResolvedAreValid()
        myFixture.performEditorAction("EditorDelete")
        checkResolvedAreValid()
        assertNull(referenceInBody().resolvePsi())

        myFixture.type("}")
        checkResolvedAreValid()
        val text = myFixture.editor.document.text
        val param = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(text.indexOf("c :")), ArendDefIdentifier::class.java)
        assertNotNull(param)
        assertEquals(param, referenceInBody().resolvePsi())
    }

    private fun referenceInBody(): ArendReferenceElement {
        val offset = myFixture.editor.document.text.lastIndexOf("c.r")
        return PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(offset), ArendReferenceElement::class.java)!!
    }
}
