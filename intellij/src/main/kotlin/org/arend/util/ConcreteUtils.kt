package org.arend.util

import com.intellij.openapi.components.service
import org.arend.naming.reference.LocatedReferableImpl
import org.arend.psi.ArendFile
import org.arend.psi.ext.ReferableBase
import org.arend.server.ArendServerService
import org.arend.term.group.ConcreteGroup

fun getReferableConcreteGroup(referable: ReferableBase<*>): ConcreteGroup? {
    val project = referable.project
    val server = project.service<ArendServerService>().server
    val concreteGroupFile = (referable.containingFile as? ArendFile)?.moduleLocation?.let { server.getRawGroup(it) } ?: return null
    return findSubGroup(concreteGroupFile, referable) ?: return null
}

private fun findSubGroup(concreteGroup: ConcreteGroup, definition: ReferableBase<*>): ConcreteGroup? {
    for (stat in concreteGroup.statements) {
        val group = stat.group ?: continue
        if ((group.referable as? LocatedReferableImpl)?.data == definition) {
            return group
        }
        val subGroup = findSubGroup(group, definition) ?: continue
        return subGroup
    }
    return null
}
