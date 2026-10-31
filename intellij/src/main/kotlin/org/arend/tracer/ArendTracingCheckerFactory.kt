package org.arend.tracer

import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.error.MergingErrorReporter
import org.arend.psi.ext.ArendExpr
import org.arend.server.ArendServerResolveListener
import org.arend.typechecking.error.local.GoalDataHolder
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.visitor.ArendCheckerFactory
import org.arend.typechecking.visitor.CheckTypeVisitor

/**
 * Creates tracing checkers for [org.arend.server.ArendChecker.typecheck], which typechecks a renamed copy of the definition,
 * so tracing neither modifies the server's resolved definitions nor replaces its typechecked ones.
 */
class ArendTracingCheckerFactory : ArendCheckerFactory {
    private val tracers = ArrayList<ArendTracingTypechecker>()
    private val errorsConsumer = ErrorsConsumer()

    override fun create(errorReporter: ErrorReporter?, pool: GlobalInstancePool?, extension: ArendExtension?, listener: ArendServerResolveListener?): CheckTypeVisitor {
        val tracer = ArendTracingTypechecker(if (errorReporter == null) errorsConsumer else MergingErrorReporter(errorReporter, errorsConsumer), pool, extension)
        synchronized(tracers) { tracers.add(tracer) }
        return tracer
    }

    /**
     * Returns the trace containing [expression], or the first trace if there is no such one.
     * Returns `null` if the definition was not typechecked. Requires read access.
     */
    fun getTracingData(expression: ArendExpr): ArendTracingData? {
        val tracers = synchronized(tracers) { ArrayList(tracers) }
        for (tracer in tracers) {
            val index = tracer.trace.indexOfEntry(expression)
            if (index >= 0) return ArendTracingData(tracer.trace, errorsConsumer.hasErrors, index)
        }
        return tracers.firstOrNull()?.let { ArendTracingData(it.trace, errorsConsumer.hasErrors, -1) }
    }

    private class ErrorsConsumer : ErrorReporter {
        @Volatile
        var hasErrors = false

        override fun report(error: GeneralError) {
            if (error !is GoalDataHolder) {
                hasErrors = true
            }
        }
    }
}
