package org.arend.quickfix.instance

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.expr.Expression
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ancestor
import org.arend.refactoring.*
import org.arend.ext.error.InstanceInferenceError
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ext.*
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.resolving.ArendReferenceBase
import org.arend.server.ArendServerService
import org.arend.util.ArendBundle

class InstanceInferenceQuickFix(val error: InstanceInferenceError, val cause: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    private val classRef = error.classRef as? TCDefReferable

    override fun startInWriteAction(): Boolean = false

    override fun getText(): String = ArendBundle.message("arend.instance.importInstance")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        classRef != null && project.service<ArendServerService>().server.instanceCache.hasInstances(classRef)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || classRef == null) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Searching for instances", true) {
            var instances : List<List<TCDefReferable>>? = null
            var foundExact = true

            override fun run(indicator: ProgressIndicator) {
                val classifying = error.classifyingExpression as? Expression
                val foundInstances = project.service<ArendServerService>().server.instanceCache.getAvailableInstances(classRef, classifying)
                if (foundInstances.isEmpty() && classifying != null) {
                    instances = project.service<ArendServerService>().server.instanceCache.getAvailableInstances(classRef, null)
                    foundExact = false
                } else {
                    instances = foundInstances
                }
            }

            override fun onFinished() {
                val instancesVal = instances
                val longName = cause.element

                when {
                    longName == null || instancesVal == null -> HintManager.getInstance().showErrorHint(editor, "Code is outdated")
                    instancesVal.isEmpty() -> HintManager.getInstance().showErrorHint(editor, "Cannot find instances for ${longName.text}")
                    instancesVal.size == 1 && foundExact -> doAddImplicitArg(project, longName, instancesVal.first())
                    else -> {
                        val lookupList = instancesVal.map {
                            val ref = it.first()
                            (ArendReferenceBase.createArendLookUpElement(ref, ref.abstractReferable, null, false, null, false, "") ?: LookupElementBuilder.create(ref, "")).withPresentableText(ref.refName)
                        }
                        val lookup = LookupManager.getInstance(project).showLookup(editor, *lookupList.toTypedArray())
                        lookup?.addLookupListener(object : LookupListener {
                            override fun itemSelected(event: LookupEvent) {
                                val index = lookupList.indexOf(event.item)
                                if (index != -1) {
                                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                                    doAddImplicitArg(project, longName, instancesVal[index])
                                }
                            }
                        })
                        (lookup as? LookupImpl)?.addAdvertisement("Choose instance", null)
                    }
                }
            }
        })
    }

    companion object {
        fun doAddImplicitArg(project: Project, compositeElement: ArendCompositeElement, chosenElement: List<TCDefReferable>) {
            WriteCommandAction.runWriteCommandAction(project, "Import Instance", null, {
                val enclosingDefinition = compositeElement.ancestor<ArendDefinition<*>>()
                val mySourceContainer = enclosingDefinition?.parentGroup
                if (mySourceContainer != null) {
                    val psiFactory = ArendPsiFactory(project)

                    for (element in chosenElement) {
                        val elementReferable = element.data as? PsiLocatedReferable ?: continue
                        val importData = getTargetName(elementReferable, compositeElement)

                        if (importData != null) {
                            val openedName: List<String> = importData.first.split(".")
                            importData.second?.execute()
                            if (openedName.size > 1 && elementReferable is ArendGroup)
                                doAddIdToOpen(psiFactory, openedName, compositeElement, elementReferable, instanceMode = true)
                        }
                    }
                }
            }, compositeElement.containingFile)
        }
    }

}