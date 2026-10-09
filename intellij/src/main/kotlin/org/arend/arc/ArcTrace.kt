package org.arend.arc

import com.intellij.openapi.diagnostic.Logger
import org.arend.ext.module.ModuleLocation
import org.arend.naming.reference.TCDefReferable
import org.arend.server.ArendServer
import org.arend.term.group.ConcreteGroup

/**
 * Diagnostics of the binary cache and the .arc views, in idea.log: what is saved when the project is closed, what is
 * loaded from the cache, and what preparing a view typechecks. Every line starts with [arc].
 */
object ArcTrace {
    private val LOG = Logger.getInstance("#org.arend.arc.trace")

    fun log(message: String) {
        LOG.info("[arc] $message")
    }

    fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val result = block()
        return result to (System.nanoTime() - start) / 1_000_000
    }

    suspend fun <T> timedSuspend(block: suspend () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val result = block()
        return result to (System.nanoTime() - start) / 1_000_000
    }

    // The typechecked definitions of the source modules, by identity, as a typechecked definition is a new one
    fun snapshot(server: ArendServer): Map<ModuleLocation, List<Int>> =
        server.modules.filter { it.locationKind == ModuleLocation.LocationKind.SOURCE }
            .associateWith { module -> definitions(server.getRawGroup(module)) }

    private fun definitions(group: ConcreteGroup?): List<Int> {
        val result = ArrayList<Int>()
        fun visit(group: ConcreteGroup) {
            (group.referable as? TCDefReferable)?.let { result.add(System.identityHashCode(it.typechecked)) }
            for (statement in group.statements) statement.group?.let(::visit)
            for (dynamic in group.dynamicGroups) visit(dynamic)
        }
        group?.let(::visit)
        return result
    }

    class Counts(val total: Int, val typechecked: Int, val errors: Int, val goals: Int)

    // The definitions of a module that can be typechecked, and how many of them are typechecked, with errors, with goals
    fun counts(group: ConcreteGroup): Counts {
        var total = 0
        var typechecked = 0
        var errors = 0
        var goals = 0
        fun count(ref: TCDefReferable) {
            if (!ref.kind.isTypecheckable) return
            total++
            val definition = ref.typechecked ?: return
            if (!definition.status().needsTypeChecking()) typechecked++
            if (definition.status().hasErrors()) errors++
            if (definition.goals.contains(definition)) goals++
        }
        fun visit(group: ConcreteGroup) {
            (group.referable as? TCDefReferable)?.let(::count)
            for (internal in group.internalReferables) (internal as? TCDefReferable)?.let(::count)
            for (statement in group.statements) statement.group?.let(::visit)
            for (dynamic in group.dynamicGroups) visit(dynamic)
        }
        visit(group)
        return Counts(total, typechecked, errors, goals)
    }

    // The modules whose typechecked definitions changed between two snapshots, with the number of changed ones
    fun changed(before: Map<ModuleLocation, List<Int>>, after: Map<ModuleLocation, List<Int>>): Map<ModuleLocation, Int> =
        after.mapNotNull { (module, definitions) ->
            val old = before[module] ?: emptyList()
            val count = definitions.indices.count { it >= old.size || old[it] != definitions[it] }
            if (count > 0) module to count else null
        }.toMap()
}
