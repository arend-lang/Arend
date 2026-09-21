package org.arend.typechecking.error

import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.module.error.BinaryCacheError

class DeduplicatingErrorReporter(private val delegate: ErrorReporter) : ErrorReporter {
    private class Group(val representative: GeneralError) {
        var count = 0
    }

    private val groups = LinkedHashMap<Any, Group>()

    override fun report(error: GeneralError) {
        val key = if (error is BinaryCacheError)
            Triple(error.phase, error.exception.javaClass, error.exception.message)
        else
            error.javaClass to error.message
        groups.getOrPut(key) { Group(error) }.count++
    }

    fun flush() {
        for (group in groups.values) {
            delegate.report(if (group.count > 1) AggregatedError(group.representative, group.count) else group.representative)
        }
        groups.clear()
    }

    private class AggregatedError(private val original: GeneralError, count: Int) :
        GeneralError(original.level, "${describe(original, count)}") {
        override fun getBodyDoc(ppConfig: PrettyPrinterConfig): Doc = original.getBodyDoc(ppConfig)

        override fun isShort() = original.isShort()

        companion object {
            private fun describe(error: GeneralError, count: Int): String {
                val plural = if (count == 1) "module" else "modules"
                return if (error is BinaryCacheError)
                    "Cannot load binary cache for $count $plural during ${error.phase}"
                else
                    "${error.message} ($count $plural)"
            }
        }
    }
}
