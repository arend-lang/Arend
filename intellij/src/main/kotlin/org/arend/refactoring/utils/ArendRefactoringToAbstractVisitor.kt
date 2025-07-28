package org.arend.refactoring.utils

import org.arend.core.context.binding.Binding
import org.arend.core.definition.DataDefinition
import org.arend.core.definition.Definition
import org.arend.ext.prettifier.ExpressionPrettifier
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.extImpl.definitionRenamer.ScopeDefinitionRenamer
import org.arend.naming.reference.LocalReferable
import org.arend.naming.renamer.ReferableRenamer
import org.arend.naming.scope.Scope
import org.arend.term.prettyprint.CollectFreeVariablesVisitor
import org.arend.term.prettyprint.ToAbstractVisitor

class ArendRefactoringToAbstractVisitor(prettifier: ExpressionPrettifier?,
                                        config: PrettyPrinterConfig,
                                        definitionRenamer: DefinitionRenamer,
                                        occupiedLocalNames: MutableSet<Variable>,
                                        sampleData: Definition?,
                                        sampleName: String?):
    ToAbstractVisitor(prettifier, config, definitionRenamer, object: CollectFreeVariablesVisitor(definitionRenamer) {
        override fun getFreeVariables(binding: Binding?): MutableSet<Variable> = occupiedLocalNames},
        object: ReferableRenamer() {
            init {
                setParameterName(sampleData, sampleName)
            }

            override fun generateFreshReferable(variable: Variable, freeVars: MutableCollection<out Variable?>): LocalReferable {
                val result: LocalReferable = super.generateFreshReferable(variable, freeVars + occupiedLocalNames)
                occupiedLocalNames.add(VariableImpl(result.refName))
                return result
            }
        }) {

    constructor(scope: Scope?,
                occupiedLocalNames: MutableSet<Variable>,
                sampleData: DataDefinition?,
                sampleName: String?): this(null, PrettyPrinterConfig.DEFAULT, ScopeDefinitionRenamer(scope), occupiedLocalNames, sampleData, sampleName)

}