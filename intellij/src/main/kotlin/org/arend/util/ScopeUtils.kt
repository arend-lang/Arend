package org.arend.util

import com.intellij.openapi.components.service
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.MergeScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.local.ListScope
import org.arend.psi.ext.ReferableBase
import org.arend.server.ArendServerService
import org.arend.util.LocalVariablesScopeCollector.Companion.getLocalVariables

fun getReferableScope(referable: ReferableBase<*>?): Scope {
  val server = referable?.project?.service<ArendServerService>()?.server
  val tcReferable = referable?.tcReferable
  val definitionData = tcReferable?.let { server?.getResolvedDefinition(it) }

  val referableScope = tcReferable?.let { server?.getReferableScope(it) } ?: EmptyScope.INSTANCE
  val localReferables = getLocalVariables(definitionData?.definition, null)
  if (referableScope == EmptyScope.INSTANCE && localReferables.isEmpty()) {
    return EmptyScope.INSTANCE
  }
  return MergeScope(ListScope(localReferables), referableScope)
}
