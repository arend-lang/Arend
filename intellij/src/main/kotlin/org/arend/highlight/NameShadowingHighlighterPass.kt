package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.descendantsOfType
import org.arend.naming.reference.DataLocalReferable
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.psi.parentOfType
import org.arend.server.ArendServerService
import org.arend.term.concrete.LocalVariablesCollector
import org.arend.util.ArendBundle

class NameShadowingHighlighterPass(file: ArendFile, editor: Editor) :
    BasePass(file, editor, "Arend name shadowing annotator", TextRange(0, editor.document.textLength)) {

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        val typeTeles = file.descendantsOfType<ArendConstructor>().map { it.descendantsOfType<ArendTypeTele>() }.flatten()
        exploreScope(typeTeles)
    }

    private fun exploreScope(teles: Sequence<ArendSourceNodeImpl>) {
        for (tele in teles) {
            val identifiers = when (tele) {
                is ArendTypeTele -> tele.typedExpr?.identifierOrUnknownList?.mapNotNull { it.defIdentifier } ?: continue
                is ArendNameTele -> tele.identifierOrUnknownList.mapNotNull { it.defIdentifier }
                is ArendNameTeleUntyped -> listOf(tele.defIdentifier)
                else -> emptyList()
            }
            searchForSameIdentifiers(identifiers, tele.parentOfType<ArendDefinition<*>>(), tele)
        }
    }

    private fun searchForSameIdentifiers(identifiers: List<ArendDefIdentifier>, definition: ArendDefinition<*>?, tele: ArendSourceNodeImpl?) {
        if (identifiers.isEmpty()) {
            return
        }
        val server = definition?.project?.service<ArendServerService>()?.server
        val definitionData = definition?.tcReferable?.let { server?.getResolvedDefinition(it) }
        val elements = LocalVariablesCollector.getLocalReferables(definitionData?.definition(), tele).mapNotNull { ((it as? DataLocalReferable)?.data as? PsiNamedElement?)?.name }

        for (identifier in identifiers) {
            val name = identifier.name
            if (elements.contains(name)) {
                val builder = HighlightInfo
                    .newHighlightInfo(HighlightInfoType.WARNING)
                    .range(identifier.textRange)
                    .severity(HighlightSeverity.WARNING)
                    .descriptionAndTooltip(ArendBundle.message("arend.inspection.name.shadowed", name))
                addHighlightInfo(builder)
            }
        }
    }
}
