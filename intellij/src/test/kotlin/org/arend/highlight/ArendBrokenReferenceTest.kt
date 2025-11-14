package org.arend.highlight

import org.arend.fileTreeFromText
import org.arend.quickfix.QuickFixTestBase

class ArendBrokenReferenceTest: QuickFixTestBase() {
    fun checkUnresolvedRefs(text: String, refName: String) {
        val fileTree = fileTreeFromText(text)
        fileTree.create()

        // Configure the editor with the main file so that myEditorTestFixture is initialized
        myFixture.configureFromTempProjectFile("Main.ard")

        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("Cannot resolve reference '$refName'") != null })
    }

    fun testBug() = checkUnresolvedRefs("""
          -- ! A.ard
          
          \func bar (n : Nat) => 101 Nat.+ n
          -- ! Main.ard
          \import A
          
          \func lol => A.bad 100
        """, "bad")

}