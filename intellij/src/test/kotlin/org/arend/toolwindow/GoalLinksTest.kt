package org.arend.toolwindow

import com.intellij.openapi.components.service
import org.arend.ArendTestBase
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.LocalError
import org.arend.ext.module.ModulePath
import org.arend.injection.CollectingDocStringBuilder
import org.arend.injection.withModulesScope
import com.intellij.psi.PsiElement
import org.arend.naming.reference.DataModuleReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.psi.ext.PsiModuleReferable
import org.arend.naming.resolving.CollectingResolverListener
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.server.ArendServerRequesterImpl
import org.arend.server.ArendServerService
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.typechecking.error.local.GoalError

class GoalLinksTest : ArendTestBase() {
    private fun toPsiTarget(referable: Referable): String? = when (val ref = referable.abstractReferable) {
        is PsiElement -> ref.containingFile?.name
        is PsiModuleReferable -> ref.modules.firstOrNull()?.name
        is DataModuleReferable -> (ref.data as? PsiElement)?.containingFile?.name
        else -> null
    }

    private fun resolveGoals(files: Map<String, String>): List<Pair<String, List<String>>> {
        for ((name, text) in files) myFixture.addFileToProject(name, text)
        val main = psiManager.findFile(myFixture.findFileInTempDir("Main.ard")) as ArendFile
        typecheck(files.keys.map { ModulePath(it.removeSuffix(".ard")) })
        val server = project.service<ArendServerService>().server
        return server.errorMap[main.moduleLocation]!!.filterIsInstance<GoalError>().map { error ->
            val definition = (error as LocalError).definition as LocatedReferable
            val scope = server.getReferableScope(definition)!!
            val text = StringBuilder()
            val visitor = CollectingDocStringBuilder(text, error)
            error.getBodyDoc(PrettyPrinterConfigWithRenamer(scope))!!.accept(visitor, false)
            val exprText = visitor.textRanges.first().single().substring(text.toString())
            val listener = CollectingResolverListener(ArendServerRequesterImpl(project), true)
            ConcreteBuilder.convertExpression(ArendPsiFactory(project).createExpression(exprText))
                .accept(ExpressionResolveNameVisitor(withModulesScope(scope, server, definition.location?.libraryName), mutableListOf(), server.typingInfo, DummyErrorReporter.INSTANCE, null, listener), null)
            Pair(exprText, listener.getCacheStructure(null)!!.cache.map { "${it.referable.refName}:${toPsiTarget(it.referable)}" })
        }
    }

    fun `test not imported module`() {
        val goals = resolveGoals(linkedMapOf(
            "A.ard" to """
                \data D | con
                \module M \where { \data E | econ }
            """.trimIndent(),
            "B.ard" to """
                \import A
                \func lem (d : D) (e : M.E) => 0
            """.trimIndent(),
            "Main.ard" to """
                \import B
                \func foo => lem {?} {?}
            """.trimIndent()))
        assertEquals(listOf(
            Pair("A.D", listOf("A:A.ard", "D:A.ard")),
            Pair("A.M.E", listOf("A:A.ard", "M:A.ard", "E:A.ard"))), goals)
    }

    fun `test imported module`() {
        val goals = resolveGoals(linkedMapOf(
            "A.ard" to """
                \data D | con
            """.trimIndent(),
            "Main.ard" to """
                \import A
                \func lem (d : D) => 0
                \func foo => lem {?}
            """.trimIndent()))
        assertEquals(listOf(Pair("D", listOf("D:A.ard"))), goals)
    }
}
