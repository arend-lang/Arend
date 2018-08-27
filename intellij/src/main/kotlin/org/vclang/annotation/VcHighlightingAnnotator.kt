package org.vclang.annotation

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.jetpad.vclang.error.Error
import com.jetbrains.jetpad.vclang.naming.error.NotInScopeError
import com.jetbrains.jetpad.vclang.naming.reference.*
import com.jetbrains.jetpad.vclang.naming.resolving.NameResolvingChecker
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.NamespaceCommandNamespace
import com.jetbrains.jetpad.vclang.term.NamespaceCommand
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.BaseAbstractExpressionVisitor
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.group.Group
import com.jetbrains.jetpad.vclang.typechecking.error.local.LocalError
import com.jetbrains.jetpad.vclang.util.LongName
import org.vclang.highlight.VcHighlightingColors
import org.vclang.psi.*
import org.vclang.psi.ext.*
import org.vclang.psi.ext.impl.InstanceAdapter
import org.vclang.quickfix.InstanceQuickFix
import org.vclang.resolving.DataLocatedReferable
import org.vclang.resolving.PsiPartialConcreteProvider
import org.vclang.typing.ExpectedTypeVisitor
import org.vclang.typing.ReferableExtractVisitor
import org.vclang.typing.TypecheckingVisitor

class VcHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        var color: VcHighlightingColors? = null
        val resolved = if (element is VcReferenceElement) {
            val resolved = VclangImportHintAction.getResolved(element)
            when (resolved) {
                null -> {
                    val annotation = holder.createErrorAnnotation(element, "Unresolved reference")
                    annotation.highlightType = ProblemHighlightType.ERROR

                    val fix = VclangImportHintAction(element)
                    if (fix.isAvailable(element.project, null, element.containingFile))
                        annotation.registerFix(fix)

                    return
                }
                is PsiDirectory -> {
                    val refList = (element.parent as? VcLongName)?.refIdentifierList
                    if (refList == null || refList.indexOf(element) == refList.size - 1) {
                        holder.createErrorAnnotation(element, "Unexpected reference to a directory")
                    }
                    return
                }
                is VcFile -> {
                    val longName = element.parent as? VcLongName
                    if (longName == null || longName.parent !is VcStatCmd) {
                        val refList = longName?.refIdentifierList
                        if (refList == null || refList.indexOf(element) == refList.size - 1) {
                            holder.createErrorAnnotation(element, "Unexpected reference to a file")
                        }
                    }
                    return
                }
                is GlobalReferable -> {
                    if (resolved.precedence.isInfix) {
                        color = VcHighlightingColors.OPERATORS
                    }
                }
            }
            resolved
        } else null

        if (element is VcArgumentAppExpr) {
            val pElement = element.parent
            if (pElement is VcNewExprImplMixin) {
                if (InstanceQuickFix.annotateNewExpr(pElement, holder)) {
                    return
                }
            }
            if (pElement is VcNewExpr && (pElement.newKw != null || pElement.lbrace != null) || pElement is VcNewArg || pElement is VcDefInstance) {
                val longName = element.longNameExpr?.longName ?: run {
                    val atomFieldsAcc = element.atomFieldsAcc
                    if (atomFieldsAcc != null && atomFieldsAcc.fieldAccList.isEmpty()) {
                        atomFieldsAcc.atom.literal?.longName
                    } else {
                        null
                    }
                }
                if (longName != null) {
                    val ref = longName.referent
                    val resolvedRef = (ref as? UnresolvedReference)?.resolve(element.scope) ?: ref
                    if (resolvedRef !is VcDefClass && resolvedRef !is UnresolvedReference && resolvedRef !is ErrorReference) {
                        holder.createErrorAnnotation(longName, "Expected a class")
                        return
                    }
                    if (pElement is VcDefInstance && resolvedRef is VcDefClass && resolvedRef.recordKw != null) {
                        holder.createErrorAnnotation(longName, "Expected a class, got a record")
                        return
                    }
                }
            }
        }

        if (element is VcAtomPatternOrPrefix) {
            val parentPattern = element.parent as? VcPattern ?: return
            val def = parentPattern.defIdentifier?.reference?.resolve() as? VcConstructor ?: return
            checkPattern(element, def.typeTeleList, parentPattern.atomPatternOrPrefixList, holder)
            return
        }

        if (element is VcPattern) {
            val clause = element.parent as? VcClause
            if (clause != null) {
                val parent = clause.parent
                when (parent) {
                    is VcFunctionClauses -> {
                        val body = parent.parent as? VcFunctionBody
                        val func = body?.parent as? VcDefFunction
                        if (func != null) {
                            checkPattern(element, body.elim, func.nameTeleList, clause.patternList, holder)
                        }
                    }
                    is VcConstructor -> checkPattern(element, parent.elim, parent.typeTeleList, clause.patternList, holder)
                    is VcCaseExpr -> checkPattern(element, parent.caseArgList.size, clause.patternList, holder)
                }
            } else {
                val conClause = element.parent as? VcConstructorClause
                val dataBody = conClause?.parent as? VcDataBody
                val typeTele = (dataBody?.parent as? VcDefData)?.typeTeleList
                if (typeTele != null) {
                    checkPattern(element, dataBody.elim, typeTele, conClause.patternList, holder)
                }
            }
        }

        if (element is VcPatternImplMixin) {
            val number = element.number
            if (number == Concrete.NumberPattern.MAX_VALUE || number == -Concrete.NumberPattern.MAX_VALUE) {
                element.getAtomPattern()?.let { holder.createErrorAnnotation(it, "Value too big") }
            }
            return
        }

        when {
            element is VcDefIdentifier -> color = VcHighlightingColors.DECLARATION
            element is VcInfixArgument || element is VcPostfixArgument -> color = VcHighlightingColors.OPERATORS
            element is VcRefIdentifier || element is LeafPsiElement && element.node.elementType == VcElementTypes.DOT -> {
                val parent = element.parent as? VcLongName
                if (parent != null) {
                    if (parent.parent is VcStatCmd) return
                    val refList = parent.refIdentifierList
                    if (refList.isEmpty() || refList.last() != element) {
                        color = VcHighlightingColors.LONG_NAME
                    }
                }
            }
        }

        val nameResolvingChecker = object : NameResolvingChecker(false, false, PsiPartialConcreteProvider) {
            override fun onDefinitionNamesClash(ref1: LocatedReferable, ref2: LocatedReferable, level: Error.Level) {
                holder.createAnnotation(levelToSeverity(level), element.textRange, "Duplicate definition name '${ref2.textRepresentation()}'")
                color = null
            }

            override fun onFieldNamesClash(ref1: LocatedReferable, superClass1: ClassReferable, ref2: LocatedReferable, superClass2: ClassReferable, currentClass: ClassReferable, level: Error.Level) {
                holder.createAnnotation(levelToSeverity(level), element.textRange, "Field is already defined in super class '${superClass1.textRepresentation()}'")
                color = null
            }

            public override fun onNamespacesClash(cmd1: NamespaceCommand, cmd2: NamespaceCommand, name: String, level: Error.Level) {
                annotateNamespacesClash(cmd1, cmd2, name, level)
                annotateNamespacesClash(cmd2, cmd1, name, level)
            }

            fun annotateNamespacesClash(cmd1: NamespaceCommand, cmd2: NamespaceCommand, name: String, level: Error.Level) {
                if (cmd1 is PsiElement) {
                    holder.createAnnotation(levelToSeverity(level), cmd1.textRange, "Definition '$name' is imported from ${LongName(cmd2.path)}")
                }
            }

            override fun onError(error: LocalError) {
                if (error is NotInScopeError) {
                    return
                }

                val cause = error.cause
                if (cause is PsiElement) {
                    holder.createAnnotation(levelToSeverity(error.level), ((cause as? VcDefFunction)?.defIdentifier ?: cause).textRange, error.shortMessage)
                }
            }
        }

        if (element is VcDefIdentifier) {
            val definition = element.parent as? PsiLocatedReferable ?: return

            if (definition is Abstract.ReferableDefinition && (definition is Abstract.ClassField || definition is Abstract.ClassFieldSynonym)) {
                val fieldRef = definition.referable
                if (fieldRef != null) {
                    val classRef = (definition.parentSourceNode as? Abstract.ClassDefinition)?.referable
                    nameResolvingChecker.checkField(fieldRef, NameResolvingChecker.collectClassFields(classRef), classRef)
                }
            }

            if (definition is InstanceAdapter) {
                if (InstanceQuickFix.annotateClassInstance(definition, holder)) {
                    color = null
                }
            } else if (definition is VcDefFunction && definition.coerceKw != null) {
                val lastParam = definition.nameTeleList.lastOrNull()
                if (lastParam == null) {
                    holder.createErrorAnnotation(element, "\\coerce must have at least one parameter")
                    color = null
                } else {
                    val visitor = ReferableExtractVisitor()
                    val scope = definition.scope
                    val parentDef = definition.parentGroup
                    val isParamDef = ExpressionResolveNameVisitor.resolve(lastParam.expr?.accept(visitor, null), scope) == parentDef

                    val defType = definition.expr
                    var resultDef = if (defType != null) {
                        ExpressionResolveNameVisitor.resolve(defType.accept(visitor, null), scope)
                    } else {
                        val term = definition.functionBody?.expr
                        if (term is VcNewExpr && term.newKw != null) {
                            ExpressionResolveNameVisitor.resolve(term.argumentAppExpr?.accept(visitor, null), scope)
                        } else {
                            (ExpressionResolveNameVisitor.resolve(term?.accept(visitor, null), scope) as? VcConstructor)?.ancestors?.filterIsInstance<VcDefData>()?.firstOrNull()
                        }
                    }
                    if (resultDef is DataLocatedReferable) {
                        resultDef = PsiLocatedReferable.fromReferable(resultDef)
                    }

                    val ok = if (isParamDef) {
                        resultDef != parentDef
                    } else {
                        if (resultDef == parentDef) {
                            true
                        } else if (resultDef != null) {
                            resultDef !is VcDefData && resultDef !is VcDefClass
                        } else {
                            if (defType != null) {
                                defType.accept(object : BaseAbstractExpressionVisitor<Void,Boolean>(true) {
                                    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) = false
                                    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?) = false
                                    override fun visitSigma(data: Any?, parameters: Collection<Abstract.Parameter>, errorData: Abstract.ErrorData?, params: Void?) = false
                                }, null)
                            } else {
                                definition.functionBody?.expr?.accept(object : BaseAbstractExpressionVisitor<Void,Boolean>(true) {
                                    override fun visitLam(data: Any?, parameters: Collection<Abstract.Parameter>, body: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) = false
                                    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?) = false
                                    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?) = false
                                    override fun visitTuple(data: Any?, fields: Collection<Abstract.Expression>, errorData: Abstract.ErrorData?, params: Void?) = false
                                    override fun visitSigma(data: Any?, parameters: Collection<Abstract.Parameter>, errorData: Abstract.ErrorData?, params: Void?) = false
                                    override fun visitClassExt(data: Any?, isNew: Boolean, baseClass: Abstract.Expression?, implementations: Collection<Abstract.ClassFieldImpl>?, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?) = false
                                }, null) ?: true
                            }
                        }
                    }

                    if (!ok) {
                        holder.createErrorAnnotation(element, "Either the last parameter or the result type (but not both) of \\coerce must be the parent definition")
                        color = null
                    }
                }
            }

            fun checkReference(oldRef: LocatedReferable?, newRef: LocatedReferable, parentRef: LocatedReferable?): Boolean {
                if (oldRef == null || oldRef == newRef) {
                    return true
                }
                val newName = newRef.textRepresentation()
                if (newName.isEmpty() || newName == "_" || newName != oldRef.textRepresentation()) {
                    return true
                }
                return nameResolvingChecker.checkReference(oldRef, newRef, parentRef)
            }

            fun checkDuplicateDefinitions(parent: Abstract.ReferableDefinition, internal: Boolean) {
                val pparent = parent.parentSourceNode
                if (pparent is Group) {
                    val parentRef = if (internal) pparent.referable else null
                    for (ref in pparent.constructors) {
                        if (!checkReference(ref.referable, definition, parentRef)) return
                    }
                    for (ref in pparent.fields) {
                        if (!checkReference(ref.referable, definition, parentRef)) return
                    }

                    for (subgroup in pparent.subgroups) {
                        if (!checkReference(subgroup.referable, definition, parentRef)) return
                        if (internal) {
                            for (ref in subgroup.constructors) {
                                if (ref.isVisible && !checkReference(ref.referable, definition, parentRef)) return
                            }
                            for (ref in subgroup.fields) {
                                if (ref.isVisible && !checkReference(ref.referable, definition, parentRef)) return
                            }
                        }
                    }
                    for (subgroup in pparent.dynamicSubgroups) {
                        if (!checkReference(subgroup.referable, definition, parentRef)) return
                        if (internal) {
                            for (ref in subgroup.constructors) {
                                if (ref.isVisible && !checkReference(ref.referable, definition, parentRef)) return
                            }
                            for (ref in subgroup.fields) {
                                if (ref.isVisible && !checkReference(ref.referable, definition, parentRef)) return
                            }
                        }
                    }
                }
            }

            if (definition is Abstract.ReferableDefinition) {
                checkDuplicateDefinitions(definition, false)

                val parent = definition.parent
                if (definition !is Abstract.Definition && parent is Abstract.Definition) {
                    checkDuplicateDefinitions(parent, true)
                }
            }
        }

        val colorCopy = color
        if (colorCopy != null) {
            holder.createInfoAnnotation(element, null).textAttributes = colorCopy.textAttributesKey
        }

        if (element is VcStatCmd) {
            var defined: MutableSet<String>? = null
            if (element.nsUsing?.nsIdList?.isEmpty() == false) {
                defined = (element.parentSourceNode as? Group)?.let { collectDefined(it) }
            }
            nameResolvingChecker.checkNamespaceCommand(element, defined)

            val nsCommands = (element.parentSourceNode as? Group)?.namespaceCommands ?: emptyList()
            if (nsCommands.size <= 1) {
                return
            }

            val scope = element.scope
            val commandNames = NamespaceCommandNamespace.resolveNamespace(scope, element).elements.map { it.textRepresentation() }.toSet()
            if (commandNames.isEmpty()) {
                return
            }

            for (cmd in nsCommands) {
                if (cmd == element) {
                    continue
                }
                for (other in NamespaceCommandNamespace.resolveNamespace(scope, cmd).elements) {
                    val otherName = other.textRepresentation()
                    if (commandNames.contains(otherName)) {
                        if (defined == null) {
                            defined = (element.parentSourceNode as? Group)?.let { collectDefined(it) }
                        }
                        if (defined == null || !defined.contains(otherName)) {
                            nameResolvingChecker.annotateNamespacesClash(element, cmd, otherName, Error.Level.WARNING)
                        }
                    }
                }
            }

            return
        }

        if (element is VcFieldTele) {
            val definition = element.parent
            if (definition is VcDefClass && definition.fatArrow != null && definition.fieldTeleList.firstOrNull() == element) {
                holder.createAnnotation(HighlightSeverity.ERROR, TextRange(element.textRange.startOffset, (definition.fieldTeleList.lastOrNull() ?: element).textRange.endOffset), "Class synonyms cannot have parameters")
            }
            return
        }

        if (element is VcRefIdentifier && element.parent is VcDefClass && resolved != null) {
            when {
                resolved !is VcDefClass -> "Expected a class"
                resolved.recordKw != null -> "Expected a class, got a record"
                resolved.fatArrow != null -> "Expected a class, got a class synonym"
                else -> null
            }?.let { msg -> holder.createAnnotation(HighlightSeverity.ERROR, element.textRange, msg) }
            return
        }

        if (element is VcLongName) {
            val parent = element.parent
            when (parent) {
                is VcDefClass -> {
                    val superClass = element.refIdentifierList.lastOrNull()?.reference?.resolve()
                    if (superClass != null) {
                        if (superClass !is VcDefClass) {
                            holder.createErrorAnnotation(element, "Expected a class")
                        } else if (parent.fatArrow != null) {
                            nameResolvingChecker.checkSuperClassOfSynonym(superClass, parent.refIdentifier?.reference?.resolve() as? ClassReferable, element)
                        }
                    }
                }
                is Abstract.ClassFieldImpl -> if ((parent is VcCoClause && parent.lbrace != null || parent is VcClassImplement && parent.lbrace != null) && parent.classReference == null) {
                    holder.createErrorAnnotation(element, "Expected either a class or a field which has a class as its type")
                }
            }
            return
        }

        if (element is LeafPsiElement) {
            when (element.node.elementType) {
                VcElementTypes.COERCE_KW -> {
                    val parent = (element.parent as? VcDefFunction)?.parentGroup
                    if (parent is VcFile) {
                        holder.createErrorAnnotation(element as PsiElement, "\\coerce is not allowed on the top level")
                    } else if (parent !is VcDefData && parent !is VcDefClass) {
                        holder.createErrorAnnotation(element as PsiElement, "\\coerce is allowed only in \\where block of \\data and \\class")
                    }
                }
                VcElementTypes.IMPORT_KW ->
                    if ((element.parent as? Abstract.NamespaceCommandHolder)?.parentSourceNode !is VcFile) {
                        holder.createErrorAnnotation(element as PsiElement, "\\import is allowed only on the top level")
                    }
            }
            return
        }

        if (element is VcExpr && element.topmostEquivalentSourceNode == element) {
            var expectedType = ExpectedTypeVisitor(element, holder).getExpectedType()
            if (expectedType is ExpectedTypeVisitor.Error) {
                expectedType.createErrorAnnotation(element, holder)
                expectedType = null
            }
            element.accept(TypecheckingVisitor(element, holder), expectedType)
        }
    }

    private fun checkPattern(pattern: VcCompositeElement, elim: VcElim?, teleList: List<Abstract.Parameter>, patternList: List<Abstract.Pattern>, holder: AnnotationHolder) {
        if (elim == null || elim.withKw != null) {
            checkPattern(pattern, teleList, patternList, holder)
        } else {
            checkPattern(pattern, elim.refIdentifierList.size, patternList, holder)
        }
    }

    private fun checkPattern(element: VcCompositeElement, teleList: List<Abstract.Parameter>, patternList: List<Abstract.Pattern>, holder: AnnotationHolder) {
        if (patternList.isEmpty()) {
            return
        }

        var i = 0
        var j = 0
        var refListSize = teleList.firstOrNull()?.referableList?.size ?: 0
        for (pattern in patternList) {
            val isExplicit = pattern.isExplicit
            if (isExplicit) {
                if (i < teleList.size && !teleList[i].isExplicit) {
                    i++
                    while (i < teleList.size && !teleList[i].isExplicit) {
                        i++
                    }
                    if (i < teleList.size) {
                        j = 0
                        refListSize = teleList[i].referableList.size
                    }
                }
            }

            while (j >= refListSize) {
                i++
                if (i >= teleList.size) {
                    break
                }
                j = 0
                refListSize = teleList[i].referableList.size
            }
            if (i >= teleList.size) {
                if (pattern == element) {
                    holder.createErrorAnnotation(TextRange(element.textRange.startOffset, (patternList.lastOrNull() as? PsiElement ?: element).textRange.endOffset), "Too many patterns. Expected " + teleList.sumBy { it.referableList.size })
                }
                return
            }

            if (isExplicit == teleList[i].isExplicit) {
                j++
                while (j >= refListSize) {
                    i++
                    if (i >= teleList.size) {
                        break
                    }
                    j = 0
                    refListSize = teleList[i].referableList.size
                }
            } else {
                if (pattern == element) {
                    holder.createErrorAnnotation(element, "Expected an explicit pattern")
                    return
                }
            }
        }

        if (i < teleList.size && element == patternList[patternList.size - 1]) {
            while (i < teleList.size) {
                if (teleList[i].isExplicit) {
                    holder.createErrorAnnotation(TextRange((patternList.firstOrNull() as? PsiElement ?: element).textRange.startOffset, element.textRange.endOffset), "Not enough patterns. Expected " + teleList.sumBy { it.referableList.size })
                    return
                }
                i++
            }
        }
    }

    private fun checkPattern(element: VcCompositeElement, numberOfPatterns: Int, patternList: List<Abstract.Pattern>, holder: AnnotationHolder) {
        if (numberOfPatterns == 0) {
            return
        }
        for (i in 0 until patternList.size) {
            if (element == patternList[i]) {
                if (!patternList[i].isExplicit) {
                    holder.createErrorAnnotation(element, "Expected an explicit pattern")
                }
                if (i == numberOfPatterns) {
                    holder.createErrorAnnotation(TextRange(element.textRange.startOffset, (patternList.lastOrNull() as? PsiElement ?: element).textRange.endOffset), "Too many patterns. Expected $numberOfPatterns")
                } else if (i == patternList.size - 1 && numberOfPatterns > patternList.size) {
                    holder.createErrorAnnotation(TextRange((patternList.firstOrNull() as? PsiElement ?: element).textRange.startOffset, element.textRange.endOffset), "Not enough patterns. Expected $numberOfPatterns")
                }
                return
            }
        }
    }

    private fun collectDefined(group: Group): HashSet<String> {
        val result = HashSet<String>()
        for (subgroup in group.subgroups) {
            result.add(subgroup.referable.textRepresentation())
            for (ref in subgroup.constructors) {
                if (ref.isVisible) {
                    result.add(ref.referable.textRepresentation())
                }
            }
            for (ref in subgroup.fields) {
                if (ref.isVisible) {
                    result.add(ref.referable.textRepresentation())
                }
            }
        }
        for (subgroup in group.dynamicSubgroups) {
            result.add(subgroup.referable.textRepresentation())
            for (ref in subgroup.constructors) {
                if (ref.isVisible) {
                    result.add(ref.referable.textRepresentation())
                }
            }
            for (ref in subgroup.fields) {
                if (ref.isVisible) {
                    result.add(ref.referable.textRepresentation())
                }
            }
        }
        for (ref in group.constructors) {
            result.add(ref.referable.textRepresentation())
        }
        for (ref in group.fields) {
            result.add(ref.referable.textRepresentation())
        }
        return result
    }

    private fun levelToSeverity(level: Error.Level) = when (level) {
        Error.Level.ERROR -> HighlightSeverity.ERROR
        Error.Level.WARNING -> HighlightSeverity.WARNING
        Error.Level.GOAL -> HighlightSeverity.WARNING
        Error.Level.INFO -> HighlightSeverity.INFORMATION
    }
}
