package org.arend.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.arend.ArendIcons
import org.arend.ArendTestBase
import org.arend.error.DummyErrorReporter
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendDefFunction
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.typechecking.computation.UnstoppableCancellationIndicator


class ArendLineMarkerProviderTest : ArendTestBase() {

  fun testSimpleAndMutualRecursiveFunctions() {
    val arendFile = myFixture.configureByText("Main.ard", """
      \func h (x y : Nat) : Nat
        | zero, zero => zero
        | zero, suc y' => h zero y'
        | suc x', y' => h x' y'
      
      \func f (x y : Nat) : Nat
        | zero, _ => zero
        | suc x', zero => zero
        | suc x', suc y' => h (g x' (suc y')) (f (suc (suc (suc x'))) y')
      
      \func g (x y : Nat) : Nat
        | zero, _ => zero
        | suc x', zero => zero
        | suc x', suc y' => h (f (suc x') (suc y')) (g x' (suc (suc y')))
    """) as ArendFile

    val elements = mutableListOf<PsiElement>()
    arendFile.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        elements.add(element)
        super.visitElement(element)
      }
    })
    val h = (elements.find { it is ArendDefFunction && it.name == "h" } as? ArendDefFunction)?.defIdentifier?.id
    val f = (elements.find { it is ArendDefFunction && it.name == "f" } as? ArendDefFunction)?.defIdentifier?.id
    val g = (elements.find { it is ArendDefFunction && it.name == "g" } as? ArendDefFunction)?.defIdentifier?.id

    val provider = ArendLineMarkerProvider()
    val markers = mutableListOf<LineMarkerInfo<*>>()
    arendFile.project.service<ArendServerService>().server.getCheckerFor(listOf(arendFile.moduleLocation!!)).typecheck(null, DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
    provider.collectSlowLineMarkers(elements, markers)

    assertEquals(3, markers.size)
    assertNotNull(markers.find { it.icon == AllIcons.Gutter.RecursiveMethod && it.element == h })
    assertNotNull(markers.find { it.icon == ArendIcons.MUTUAL_RECURSIVE && it.element == f })
    assertNotNull(markers.find { it.icon == ArendIcons.MUTUAL_RECURSIVE && it.element == g })
  }

  fun testCaseFunctions() {
    val arendFile = myFixture.configureByText("Main.ard", """
      \func foo (n : Nat) : Nat => \case \elim n \with {
        | zero => 101
        | suc n => bar n
      }
      
      \func bar (n : Nat) : Nat
        | zero => 101
        | suc n => foo n
      """
    ) as ArendFile

    val elements = mutableListOf<PsiElement>()
    arendFile.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        elements.add(element)
        super.visitElement(element)
      }
    })
    val bar = (elements.find { it is ArendDefFunction && it.name == "bar" } as? ArendDefFunction)?.defIdentifier?.id
    val foo = (elements.find { it is ArendDefFunction && it.name == "foo" } as? ArendDefFunction)?.defIdentifier?.id

    val provider = ArendLineMarkerProvider()
    val markers = mutableListOf<LineMarkerInfo<*>>()
    arendFile.project.service<ArendServerService>().server.getCheckerFor(listOf(arendFile.moduleLocation!!))
      .typecheck(
        null,
        DummyErrorReporter.INSTANCE,
        UnstoppableCancellationIndicator.INSTANCE,
        ProgressReporter.empty()
      )
    provider.collectSlowLineMarkers(elements, markers)

    assertEquals(2, markers.size)
    assertNotNull(markers.find { it.icon == ArendIcons.MUTUAL_RECURSIVE && it.element == bar })
    assertNotNull(markers.find { it.icon == ArendIcons.MUTUAL_RECURSIVE && it.element == foo })
  }
}
