package org.arend.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.arend.ArendTestBase
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendDefClass

class ArendSuperclassesLineMarkerProviderTest: ArendTestBase() {

    fun testSimpleSuperclass() {
        val arendFile = myFixture.configureByText("Main.ard", """
      \class Foo
      
      \instance Bar : Foo
    """) as ArendFile

        val elements = mutableListOf<PsiElement>()
        arendFile.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                elements.add(element)
                super.visitElement(element)
            }
        })
        val foo = (elements.find { it is ArendDefClass && it.name == "Foo" } as ArendDefClass).defIdentifier?.id

        myFixture.doHighlighting()

        val provider = ArendSuperclassesLineMarkerProvider()
        val markers = mutableListOf<LineMarkerInfo<*>>()
        provider.collectSlowLineMarkers(elements, markers)
        assertEquals(1, markers.size)
        assertNotNull(markers.find { it.icon == AllIcons.Gutter.OverridenMethod && it.element == foo })
    }
}
