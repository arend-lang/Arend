package org.arend.tracer

import org.arend.ArendTestBase
import org.intellij.lang.annotations.Language

abstract class ArendTraceTestBase : ArendTestBase() {
    protected fun doTrace(@Language("Arend") code: String): ArendTracingData {
        InlineFile(code).withCaret()
        typecheck()
        val (expression, definitionRef) =
            ArendTraceAction.getElementAtCursor(myFixture.file, myFixture.editor)!!
        return ArendTraceAction.runTracingTypechecker(project, definitionRef, expression)!!
    }

    protected fun getFirstEntry(tracingData: ArendTracingData) =
        tracingData.trace.entries.getOrNull(tracingData.firstEntryIndex)
}