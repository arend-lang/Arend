package org.arend.typechecking

import org.arend.naming.reference.TCDefReferable
import org.arend.term.group.ConcreteGroup

/**
 * The cores of the definitions of a module, by identity: typechecking a definition, or loading it from an .arc, gives
 * it a new one, so two equal lists mean that nothing in the module was typechecked or loaded in between.
 */
fun coreIdentities(group: ConcreteGroup): List<Int> {
    val result = ArrayList<Int>()
    fun visit(group: ConcreteGroup) {
        (group.referable as? TCDefReferable)?.let { result.add(System.identityHashCode(it.typechecked)) }
        for (statement in group.statements) statement.group?.let(::visit)
        for (dynamic in group.dynamicGroups) visit(dynamic)
    }
    visit(group)
    return result
}
