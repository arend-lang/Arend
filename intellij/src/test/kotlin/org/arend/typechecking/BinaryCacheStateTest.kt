package org.arend.typechecking

import com.intellij.openapi.components.service
import org.arend.ArendTestBase
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.TCDefReferable
import org.arend.server.ArendServerService

/**
 * When the project is closed, a module whose .arc has what it holds is not saved again: it was loaded whole from the
 * .arc or saved to it, and nothing in it was typechecked or loaded since.
 */
class BinaryCacheStateTest : ArendTestBase() {
    private val location: ModuleLocation
        get() = ModuleLocation(module.name, ModuleLocation.LocationKind.SOURCE, ModulePath("Main"))

    private val group
        get() = project.service<ArendServerService>().server.getRawGroup(location)!!

    fun `test a module is unchanged until something in it is typechecked again`() {
        val cache = project.service<ArendBinaryCacheService>()
        cache.invalidate(listOf(module.name))
        InlineFile("\\func f => 0\n\\func g => 1")
        typecheck()
        assertFalse("nothing was loaded or saved yet", cache.isUnchangedSinceCached(location, group))

        cache.saved(location, group)
        assertTrue(cache.isUnchangedSinceCached(location, group))
        typecheck()
        assertTrue("typechecking again with nothing changed typechecks nothing", cache.isUnchangedSinceCached(location, group))

        InlineFile("\\func f => 0\n\\func g => 2")
        typecheck()
        assertFalse("g was typechecked again", cache.isUnchangedSinceCached(location, group))

        cache.saved(location, group)
        assertTrue(cache.isUnchangedSinceCached(location, group))
        cache.invalidate(listOf(module.name))
        assertFalse("reloading the libraries forgets what was cached", cache.isUnchangedSinceCached(location, group))
    }

    private fun referable(name: String) =
        group.statements.mapNotNull { it.group?.referable as? TCDefReferable }.first { it.refName == name }

    fun `test a module loaded in part is not saved until its dropped definitions are typechecked again`() {
        val cache = project.service<ArendBinaryCacheService>()
        cache.invalidate(listOf(module.name))
        InlineFile("\\func f => 0\n\\func g => 1")
        typecheck()
        val g = referable("g")
        val core = g.typechecked
        g.typechecked = null
        cache.loadedInPart(location, group, setOf(g))
        assertTrue("g is in the .arc but not in the module", cache.losesDroppedDefinitions(location))
        assertTrue(cache.isUnchangedSinceCached(location, group))

        g.typechecked = core
        assertFalse("g is typechecked again", cache.losesDroppedDefinitions(location))

        cache.loadedInPart(location, group, setOf(g))
        cache.saved(location, group)
        assertFalse("the .arc has what the module holds", cache.losesDroppedDefinitions(location))
        cache.loadedInPart(location, group, setOf(g))
        cache.invalidate(listOf(module.name))
        assertFalse("reloading the libraries forgets what was dropped", cache.losesDroppedDefinitions(location))
    }
}
