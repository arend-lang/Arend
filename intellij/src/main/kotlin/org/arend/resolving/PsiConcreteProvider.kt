package org.arend.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.reference.Precedence
import org.arend.naming.error.ReferenceError
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.LocatedReferableImpl
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.ConcreteDefinitionVisitor
import org.arend.typechecking.execution.TypecheckingEventsProcessor
import org.arend.typechecking.provider.ConcreteProvider


private object NullDefinition : Concrete.Definition(LocatedReferableImpl(Precedence.DEFAULT, "_", null, GlobalReferable.Kind.OTHER)) {
    override fun <P : Any?, R : Any?> accept(visitor: ConcreteDefinitionVisitor<in P, out R>?, params: P): R? = null
}

class PsiConcreteProvider(private val project: Project, private val referableConverter: ReferableConverter, private val errorReporter: ErrorReporter, private val eventsProcessor: TypecheckingEventsProcessor?, private val resolve: Boolean = true) : ConcreteProvider {
    private val cache: MutableMap<PsiLocatedReferable, Concrete.ReferableDefinition> = HashMap()

    private fun getConcreteDefinition(psiReferable: PsiConcreteReferable): Concrete.ReferableDefinition? {
        var cached = true
        var scope: Scope? = null
        val result = cache.computeIfAbsent(psiReferable) { runReadAction {
            cached = false
            if (eventsProcessor != null) {
                eventsProcessor.onTestStarted(psiReferable)
                eventsProcessor.startTimer(psiReferable)
            }

            val def = psiReferable.computeConcrete(referableConverter, errorReporter)
            if (def == null) {
                if (eventsProcessor != null) {
                    eventsProcessor.stopTimer(psiReferable)
                    eventsProcessor.onTestFailure(psiReferable)
                    eventsProcessor.onTestFinished(psiReferable)
                }
                return@runReadAction NullDefinition
            } else {
                if (resolve && def.relatedDefinition.stage == Concrete.Stage.NOT_RESOLVED) {
                    scope = CachingScope.make(ConvertingScope(referableConverter, psiReferable.scope))
                    def.relatedDefinition.accept(DefinitionResolveNameVisitor(this, true, errorReporter), scope)
                }
                eventsProcessor?.stopTimer(psiReferable)
                return@runReadAction def
            }
        } }

        if (result === NullDefinition) {
            return null
        }
        if (cached) {
            return result
        }

        if (resolve && result.relatedDefinition.stage < Concrete.Stage.RESOLVED) {
            runReadAction {
                if (scope == null) {
                    scope = CachingScope.make(ConvertingScope(referableConverter, psiReferable.scope))
                }
                result.relatedDefinition.accept(DefinitionResolveNameVisitor(this, errorReporter), scope)
            }
        }

        when (result) {
            is Concrete.DataDefinition -> for (clause in result.constructorClauses) {
                for (constructor in clause.constructors) {
                    PsiLocatedReferable.fromReferable(constructor.data)?.let { cache[it] = constructor }
                }
            }
            is Concrete.ClassDefinition -> for (element in result.elements) {
                if (element is Concrete.ClassField) {
                    PsiLocatedReferable.fromReferable(element.data)?.let { cache[it] = element }
                }
            }
        }

        return result
    }

    override fun getConcrete(referable: GlobalReferable): Concrete.ReferableDefinition? {
        var psiReferable = PsiLocatedReferable.fromReferable(referable)
        if (psiReferable == null) {
            if (referable is DataLocatedReferable) {
                psiReferable = referable.fixPointer(project)
                if (psiReferable == null) {
                    errorReporter.report(ReferenceError(GeneralError.Stage.OTHER, "Reference is invalid. Try to typecheck the definition again", referable))
                    return null
                }
            } else {
                errorReporter.report(ReferenceError(GeneralError.Stage.OTHER, "Unknown type of reference", referable))
                return null
            }
        }

        if (psiReferable is PsiConcreteReferable) {
            return getConcreteDefinition(psiReferable)
        }

        cache[psiReferable]?.let { return it }
        val concreteRef = runReadAction { psiReferable.ancestor<PsiConcreteReferable>() } ?: return null
        getConcreteDefinition(concreteRef) ?: return null
        return cache[psiReferable]
    }

    override fun getConcreteFunction(referable: GlobalReferable): Concrete.FunctionDefinition? {
        val psiReferable = referable.underlyingReferable
        return if (psiReferable is PsiConcreteReferable && (psiReferable is ArendDefFunction || psiReferable is ArendDefInstance)) getConcreteDefinition(psiReferable) as? Concrete.FunctionDefinition else null
    }

    override fun getConcreteInstance(referable: GlobalReferable): Concrete.FunctionDefinition? {
        val psiReferable = referable.underlyingReferable
        return if (psiReferable is ArendDefInstance) getConcreteDefinition(psiReferable) as? Concrete.FunctionDefinition else null
    }

    override fun getConcreteClass(referable: GlobalReferable): Concrete.ClassDefinition? {
        val psiReferable = referable.underlyingReferable
        return if (psiReferable is ArendDefClass) getConcreteDefinition(psiReferable) as? Concrete.ClassDefinition else null
    }

    override fun getConcreteData(referable: GlobalReferable): Concrete.DataDefinition? {
        val psiReferable = referable.underlyingReferable
        return if (psiReferable is ArendDefData) getConcreteDefinition(psiReferable) as? Concrete.DataDefinition else null
    }

    override fun getTCReferable(referable: GlobalReferable) =
        referable as? TCReferable ?: (referable as? LocatedReferable)?.let { referableConverter.toDataLocatedReferable(it) }
}