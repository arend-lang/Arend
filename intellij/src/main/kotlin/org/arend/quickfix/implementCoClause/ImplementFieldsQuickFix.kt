package org.arend.quickfix.implementCoClause

import com.google.common.collect.Sets.combinations
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.naturalSorted
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.definition.ClassDefinition
import org.arend.naming.reference.FieldReferableImpl
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.*
import org.arend.psi.findPrevSibling
import org.arend.refactoring.moveCaretToEndOffset
import org.arend.settings.ArendProjectStatistics
import org.arend.term.abs.Abstract
import org.arend.util.ArendBundle
import kotlin.math.min

open class ImplementFieldsQuickFix(
                              private val instanceRef: SmartPsiElementPointer<PsiElement>,
                              private val classRef: SmartPsiElementPointer<ArendDefClass>?,
                              private val needsBulb: Boolean,
                              private val fieldsToImplement: List<Pair<LocatedReferable, Boolean>>): IntentionAction, Iconable {
    private var caretMoved = false

    override fun startInWriteAction() = true

    override fun getFamilyName() = text

    override fun getText() = ArendBundle.message("arend.coClause.implementMissing")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        instanceRef.element != null

    private fun getMinGroup(possibleBaseArguments: Set<PsiElement>, rules: Map<ArendClassField, List<Set<PsiElement>>>): Set<Set<PsiElement>> {
        val results = mutableSetOf<Set<PsiElement>>()
        for (groupSize in 1..min(DEFAULT_FIELDS_LIMIT, possibleBaseArguments.size)) {
            if (results.isNotEmpty()) {
                break
            }
            val groups = combinations(possibleBaseArguments.toSet(), groupSize)
            for (group in groups) {
                val derivedFields = group.toMutableSet()
                if (derivedFields.mapNotNull { (it as ArendClassField).defIdentifier?.name }.containsAll(setOf("norm_zro", "norm_negative", "norm_+"))) {
                    println()
                }
                while (derivedFields.size < rules.size) {
                    var added = false
                    for ((field, possibleArguments) in rules) {
                        for (arguments in possibleArguments) {
                            if (!derivedFields.contains(field) && arguments.all { derivedFields.contains(it) }) {
                                derivedFields.add(field)
                                added = true
                                break
                            }
                        }
                    }
                    if (!added) {
                        break
                    }
                }
                if (rules.size == derivedFields.size) {
                    results.add(group)
                }
            }
        }
        return results
    }

    private fun getMinDefaultFields(): Pair<Set<Set<PsiElement>>, Set<PsiElement>> {
        val arendClassDef = classRef?.element
        val defaultFields = (arendClassDef?.tcReferable?.typechecked as? ClassDefinition)?.defaultImplDependencies ?: emptyMap()

        val psiElementsToImplement = fieldsToImplement.mapNotNull { (it.first as? FieldReferableImpl)?.data as? ArendClassField }.toSet()
        val defaultFieldsToImplement = defaultFields.filter { psiElementsToImplement.contains(it.key.referable.data) }

        val rules = mutableMapOf<ArendClassField, MutableList<MutableSet<PsiElement>>>()
        for ((field, arguments) in defaultFieldsToImplement) {
            val arendClassField = field.referable.data as? ArendClassField ?: continue
            val necessaryArguments = mutableSetOf<PsiElement>()
            for (argument in arguments) {
                if (!defaultFieldsToImplement.keys.contains(argument)) continue
                val arendArgumentClassField = argument.referable.data as? ArendClassField ?: continue
                necessaryArguments.add(arendArgumentClassField)
            }
            necessaryArguments.remove(arendClassField)
            if (necessaryArguments.isNotEmpty()) {
                rules.getOrPut(arendClassField) { mutableListOf() }.add(necessaryArguments)
            }
        }

        val defaultArendClassFields = defaultFieldsToImplement.mapNotNull { it.key.referable.data as? ArendClassField }
        val independentDefaultFields = defaultArendClassFields.filter { !rules.containsKey(it) }.toMutableSet()
        var isRemoved: Boolean
        while (true) {
            isRemoved = false
            for ((field, argumentVariants) in rules) {
                for (arguments in argumentVariants) {
                    if (arguments.removeAll(independentDefaultFields)) {
                        isRemoved = true
                    }
                }
                if (argumentVariants.any { it.isEmpty() }) {
                    independentDefaultFields.add(field)
                }
            }
            if (!isRemoved) {
                break
            }
            rules.keys.removeAll(independentDefaultFields)
        }

        val newRules = rules.filter { it.value.none { arguments -> arguments.isEmpty() } }.toMutableMap()

        val possibleBaseArguments = defaultArendClassFields.map { newRules[it]?.flatten() ?: emptyList() }.flatten().toSet()
        val result = if (newRules.isEmpty()) {
            emptySet()
        } else {
            getMinGroup(possibleBaseArguments, newRules)
        }
        return Pair(result, defaultArendClassFields.toSet())
    }

    private fun addField(field: Referable, inserter: AbstractCoClauseInserter, editor: Editor?, psiFactory: ArendPsiFactory, needQualifiedName: Boolean = false) {
        val coClauses = inserter.coClausesList

        val fieldClass = (field as? LocatedReferable)?.locatedReferableParent
        val name = if (needQualifiedName && fieldClass != null) "${fieldClass.textRepresentation()}.${field.textRepresentation()}" else field.textRepresentation()

        if (coClauses.isEmpty()) {
            inserter.insertFirstCoClause(name, psiFactory, editor)
            caretMoved = true
        } else {
            val anchor = coClauses.last()
            val coClause = when (anchor) {
                is ArendCoClause -> psiFactory.createCoClause(name)
                is ArendLocalCoClause -> psiFactory.createLocalCoClause(name)
                else -> null
            }

            if (coClause != null) {
                val pipeSample = coClause.findPrevSibling()
                val whitespace = psiFactory.createWhitespace(" ")
                val insertedCoClause = anchor.parent.addAfter(coClause, anchor)
                if (insertedCoClause is ArendCoClause && pipeSample != null) {
                    anchor.parent.addBefore(pipeSample, insertedCoClause)
                    anchor.parent.addBefore(whitespace, insertedCoClause)
                }
                if (!caretMoved && editor != null) {
                    moveCaretToEndOffset(editor, anchor.nextSibling)
                    caretMoved = true
                }
                anchor.parent.addAfter(psiFactory.createWhitespace("\n  "), anchor)
            }
        }
    }

    private fun getFullClassName(): String {
        val classReferable = instanceRef.element as? PsiLocatedReferable
        return classReferable?.fullName?.toString() ?: "FullClassNameIsNotAvailable"
    }

    private fun showFields(
        project: Project,
        editor: Editor,
        variants: MutableList<MutableList<LocatedReferable>>,
        allFields: Map<LocatedReferable, Boolean>,
        baseFields: List<Pair<LocatedReferable, Boolean>>
    ) {
        if (variants.isEmpty()) {
            variants.add(baseFields.map { it.first }.toMutableList())
        } else if (variants.size == 1) {
            variants.first().addAll(0, baseFields.map { it.first })
        }

        if (variants.size == 1) {
            val psiFactory = ArendPsiFactory(project)
            val firstCCInserter = makeFirstCoClauseInserter(instanceRef.element) ?: return
            WriteCommandAction.runWriteCommandAction(editor.project) {
                for (field in variants.first()) {
                    addField(field, firstCCInserter, editor, psiFactory, allFields[field]!!)
                }
            }
            return
        }

        val className = getFullClassName()
        val defaultArguments = project.service<ArendProjectStatistics>().state.implementFieldsStatistics[className]
        var suggestDefaultOption = true
        var matchedList: List<LocatedReferable>? = null
        if (defaultArguments != null && variants[0].size == defaultArguments.size) {
            val sortedDefaultArguments = defaultArguments.naturalSorted()
            for (variant in variants) {
                if (variant == sortedDefaultArguments) {
                    matchedList = variant
                }
            }
            if (matchedList == null) {
                suggestDefaultOption = false
            }
        } else {
            suggestDefaultOption = false
        }

        if (suggestDefaultOption) {
            val defaultOption = ArendBundle.message("arend.clause.implementMissing.default.option")
            val anotherOption = ArendBundle.message("arend.clause.implementMissing.another.option")
            val defaultOptionStep = object : BaseListPopupStep<String>(ArendBundle.message("arend.clause.implementMissing.question"), listOf(defaultOption, anotherOption)) {
                override fun onChosen(option: String?, finalChoice: Boolean): PopupStep<*>? {
                    if (option == defaultOption) {
                        printFields(project, editor, baseFields, matchedList!!, allFields)
                    } else if (option == anotherOption) {
                        createListOfVariants(project, editor, allFields, baseFields, variants)
                    }
                    return FINAL_CHOICE
                }
            }

            val popup = JBPopupFactory.getInstance().createListPopup(defaultOptionStep)
            popup.showInBestPositionFor(editor)
        } else {
            createListOfVariants(project, editor, allFields, baseFields, variants)
        }
    }

    private fun createListOfVariants(
        project: Project,
        editor: Editor,
        allFields: Map<LocatedReferable, Boolean>,
        baseFields: List<Pair<LocatedReferable, Boolean>>,
        variants: List<List<LocatedReferable>>
    ) {
        val fieldsToImplementStep = object : BaseListPopupStep<List<LocatedReferable>>(ArendBundle.message("arend.clause.implementMissing.question"), variants) {
            override fun onChosen(extraFields: List<LocatedReferable>?, finalChoice: Boolean): PopupStep<*>? {
                if (extraFields != null) {
                    val name = getFullClassName()
                    project.service<ArendProjectStatistics>().state.implementFieldsStatistics[name] = extraFields.map { it.textRepresentation() }
                    printFields(project, editor, baseFields, extraFields, allFields)
                }
                return FINAL_CHOICE
            }
        }

        val popup = JBPopupFactory.getInstance().createListPopup(fieldsToImplementStep)
        popup.showInBestPositionFor(editor)
    }

    private fun printFields(
        project: Project,
        editor: Editor,
        baseFields: List<Pair<LocatedReferable, Boolean>>,
        extraFields: List<LocatedReferable>,
        allFields: Map<LocatedReferable, Boolean>
    ) {
        val psiFactory = ArendPsiFactory(project)
        val firstCCInserter = makeFirstCoClauseInserter(instanceRef.element) ?: return
        val fields = baseFields.map { it.first } + extraFields
        WriteCommandAction.runWriteCommandAction(editor.project) {
            for (field in fields) {
                addField(field, firstCCInserter, editor, psiFactory, allFields[field]!!)
            }
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return
        val instance = instanceRef.element ?: return

        val (groups, defaultFields) = if (instance is Abstract.ClassReferenceHolder) {
            getMinDefaultFields()
        } else {
            Pair(emptySet(), emptySet())
        }

        val allFields = fieldsToImplement.toMap()
        val baseFields = fieldsToImplement.filter { !defaultFields.contains(it.first.abstractReferable as? PsiElement?) }
        val extraFields = fieldsToImplement.filter { defaultFields.contains(it.first.abstractReferable as? PsiElement?) }
            .associateBy { (referable, _) -> defaultFields.find { referable.abstractReferable == it }!! }

        var variants = groups.map { group -> group.mapNotNull { extraFields[it]?.first }.naturalSorted().toMutableList() }.toMutableList()
        if (variants.isNotEmpty()) {
            val minSize = variants.minBy { it.size }.size
            variants = if (minSize == 0) {
                mutableListOf()
            } else {
                variants.filter { it.size == minSize }.naturalSorted().toMutableList()
            }
        }

        showFields(project, editor, variants, allFields, baseFields)
    }

    override fun getIcon(flags: Int) = if (needsBulb) AllIcons.Actions.IntentionBulb else null

    companion object {
        const val DEFAULT_FIELDS_LIMIT = 16
    }
}