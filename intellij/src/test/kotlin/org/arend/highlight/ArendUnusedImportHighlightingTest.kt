package org.arend.highlight

import org.arend.fileTreeFromText
import org.arend.quickfix.QuickFixTestBase

class ArendUnusedImportHighlightingTest : QuickFixTestBase() {
    private fun unusedImportMessages(tail: String): List<String> {
        val tree = fileTreeFromText("""
            -- ! Main.ard
            \import Foo
            
            $tail
            -- ! Foo.ard
            \func bar => 1
            """)
        tree.create(myFixture.project, myFixture.findFileInTempDir("."))
        myFixture.configureFromTempProjectFile("Main.ard")
        typecheck(tree.fileNames)
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains("is not used") }
    }

    fun `test an unused import is greyed out`() {
        assertEquals(listOf("Import 'Foo' is not used"), unusedImportMessages("\\func f => 1"))
    }

    fun `test a used import is not greyed out`() {
        assertEquals(emptyList<String>(), unusedImportMessages("\\func f => bar"))
    }

    fun `test an unused import stays greyed out while the file has errors`() {
        assertEquals(listOf("Import 'Foo' is not used"), unusedImportMessages("\\func"))
        assertEquals(listOf("Import 'Foo' is not used"), unusedImportMessages("\\fun"))
    }
}
