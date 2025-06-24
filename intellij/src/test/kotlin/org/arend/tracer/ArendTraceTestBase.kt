package org.arend.tracer

import com.intellij.openapi.components.service
import org.arend.ArendTestBase
import org.arend.server.ArendServerService
import org.arend.term.concrete.Concrete
import org.intellij.lang.annotations.Language

abstract class ArendTraceTestBase : ArendTestBase() {
    protected fun doTrace(@Language("Arend") code: String): ArendTracingData {
        InlineFile(code).withCaret()
        typecheck()
        val (expression, definitionRef) =
            ArendTraceAction.getElementAtCursor(myFixture.file, myFixture.editor)!!
        val definition = project.service<ArendServerService>().server.getResolvedDefinition(definitionRef)?.definition
                as Concrete.Definition
        return ArendTraceAction.runTracingTypechecker(project, definition, expression)
    }

    protected fun getFirstEntry(tracingData: ArendTracingData) =
        tracingData.trace.entries.getOrNull(tracingData.firstEntryIndex)
}