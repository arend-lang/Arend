package org.arend.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.LanguageStructureViewBuilder
import org.arend.ArendTestBase

class ArendStructureViewNavigationTest : ArendTestBase() {
    fun `test navigate to elements`() {
        InlineFile("""
            \func foo => 0

            \data D | con1 | con2

            \class C {
              | field : Nat
            }
        """.trimIndent())
        val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(myFixture.file) as TreeBasedStructureViewBuilder
        val root = builder.createStructureViewModel(myFixture.editor).root

        val visited = mutableListOf<String>()
        fun visit(element: StructureViewTreeElement) {
            for (child in element.children) {
                child as StructureViewTreeElement
                val name = child.presentation.presentableText!!
                assertTrue(child.canNavigateToSource())
                myFixture.editor.caretModel.moveToOffset(0)
                child.navigate(true)
                assertEquals(name, myFixture.file.text.indexOf(name), myFixture.editor.caretModel.offset)
                visited.add(name)
                visit(child)
            }
        }
        visit(root)
        assertEquals(listOf("foo", "D", "con1", "con2", "C", "field"), visited)
    }
}
