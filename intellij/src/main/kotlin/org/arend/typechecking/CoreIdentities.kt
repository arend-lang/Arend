package org.arend.typechecking

import org.arend.server.BinaryCacheFilter
import org.arend.term.group.ConcreteGroup

/**
 * The cores of the definitions of a module, by identity: typechecking a definition, or loading it from an .arc, gives
 * it a new one, so two equal lists mean that nothing in the module was typechecked or loaded in between.
 */
fun coreIdentities(group: ConcreteGroup): List<Int> {
    val result = ArrayList<Int>()
    BinaryCacheFilter.forEachDefinition(group) { result.add(System.identityHashCode(it.typechecked)) }
    return result
}
