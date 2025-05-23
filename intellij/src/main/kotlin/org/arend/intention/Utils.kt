package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.MergeScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.local.ListScope
import org.arend.psi.ext.ArendDefinition
import org.arend.psi.topmostAncestor
import org.arend.server.ArendServerService
import org.arend.term.concrete.LocalVariablesCollector.getLocalReferables

fun getElementScope(element: PsiElement): Scope {
  val server = element.project.service<ArendServerService>().server
  val tcReferable = element.topmostAncestor<ArendDefinition<*>>()?.tcReferable
  val definitionData = tcReferable?.let { server.getResolvedDefinition(it) }

  val referableScope = tcReferable?.let { server.getReferableScope(it) } ?: EmptyScope.INSTANCE
  val localReferables = getLocalReferables(definitionData?.definition, null)
  return MergeScope(ListScope(localReferables), referableScope)
}