package org.arend.aifeatures.mcpTools

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.arend.aifeatures.McpTool
import org.arend.aifeatures.parseTypecheckData
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModuleLocation.LocationKind
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.server.ArendServerService
import org.arend.term.concrete.Concrete
import org.arend.term.group.ConcreteGroup

class ListSimplifiedModulesTool : McpTool {
  override val name: String = "mcp_arend_List_modules_content"
  override val description: String = ""
  override fun getInputSchema(): JsonObject =
    buildJsonObject {
    putJsonObject("libraryPath") {
      put("type", "string")
    }
    putJsonObject("modulePaths") {
      put("type", "array")
      putJsonObject("items") {
        put("type", "string")
      }
    }
  }

  override fun execute(project: Project, arguments: String): String {
    val parsedUserRequest = parseTypecheckData(arguments)
    
    // Try both SOURCE and GENERATED location kinds since modules like Paths.Meta can be GENERATED
    val modules: List<ModuleLocation> = parsedUserRequest.modulePaths.flatMap { modulePath ->
      val path = ModulePath.fromString(modulePath.split("/").last())
      val sourceLocation = ModuleLocation(parsedUserRequest.libraryName, LocationKind.SOURCE, path)
      val generatedLocation = ModuleLocation(parsedUserRequest.libraryName, LocationKind.GENERATED, path)
      listOf(sourceLocation, generatedLocation)
    }
    
    val result = listSimplifiedModules(project, modules)
    println("[DEBUG_LOG] ListSimplifiedModulesTool.execute() $result")
    return result.toString()
  }

  fun listSimplifiedModules(project : Project, modules: List<ModuleLocation>) : Map<ModuleLocation, String> {
    if (modules.isEmpty()){
      return emptyMap()}
    val simplifiedModules = mutableMapOf<ModuleLocation, String>()
    val server = project.service<ArendServerService>().server
      for(module in modules){
        simplifiedModules[module] = ""
        val group: ConcreteGroup = server.getRawGroup(module) ?: continue
        
        // First, collect all import statements from the top-level group
        for (statement in group.statements()) {
          statement.command()?.let { cmd ->
            if (cmd.isImport) {
              simplifiedModules[module] += cmd.toString() + "\n"
            }
          }
        }
        // Add a blank line after imports if there were any
        if (simplifiedModules[module]?.isNotEmpty() == true) {
          simplifiedModules[module] += "\n"
        }
        
        group.traverseGroup { x ->
          x.definition()?.let { def ->
            if (def is Concrete.BaseFunctionDefinition) {
              if (def.kind == FunctionKind.INSTANCE) {
                //if this is instance, just leave it as it is
                // TODO : leave only the classifying field
                val text = getOriginalText(def)

                simplifiedModules[module] += (text ?: run {
                  val sb = StringBuilder()
                  def.prettyPrint(sb, PrettyPrinterConfig.DEFAULT)
                  sb.toString()
                })
              } else {
                // For other functions, print only the signature without body (preserving comments before =>)
                val signature = getSignatureFromSource(def)
                simplifiedModules[module] += ((signature ?: buildFunctionSignature(def)) + " {?LIBRARY_STATEMENT}\n\n")
              }
            } else if (def is Concrete.MetaDefinition) {
              // For meta definitions, preserve comments and output signature with [LIBRARY STATEMENT]
              val signature = getMetaSignatureFromSource(def)
              simplifiedModules[module] += ((signature ?: buildMetaSignature(def)) + " {?LIBRARY_STATEMENT}\n\n")
            }
          }
        }
    }

    return simplifiedModules
  }
  // Try to get original text from source file using PsiElement
  private fun getOriginalText(def: Concrete.BaseFunctionDefinition): String? {
    val locatedReferable = def.data as? org.arend.naming.reference.LocatedReferableImpl
    val psiElement = locatedReferable?.data as? PsiElement
    if (psiElement == null) {
      return null
    }
    return runReadAction {
      // Get the whole definition element including any preceding comments
      val defElement = psiElement.parent ?: psiElement
      defElement.text
    }
  }

  // Collect preceding comments (and whitespace between them) before a definition element
  private fun collectPrecedingComments(defElement: PsiElement): String {
    val comments = mutableListOf<PsiElement>()
    var sibling = defElement.prevSibling

    // Collect comments and whitespace going backwards
    while (sibling != null) {
      when (sibling) {
        is PsiComment -> comments.add(0, sibling)
        is PsiWhiteSpace -> {
          // Keep whitespace only if there's a comment before it
          val prevSibling = sibling.prevSibling
          if (prevSibling is PsiComment) {
            comments.add(0, sibling)
          } else {
            // Stop if we hit whitespace with no comment before it
            break
          }
        }
        else -> break // Stop at any other element
      }
      sibling = sibling.prevSibling
    }

    return if (comments.isNotEmpty()) {
      comments.joinToString("") { it.text }
    } else {
      ""
    }
  }

  // Try to get signature (up to and including =>) from source file
  private fun getSignatureFromSource(def: Concrete.BaseFunctionDefinition): String? {
    // def.data is LocatedReferableImpl, we need to get its data which contains the PsiElement
    val locatedReferable = def.data as? org.arend.naming.reference.LocatedReferableImpl
    val psiElement = locatedReferable?.data as? PsiElement
    if (psiElement == null) {
      return null
    }
    return runReadAction {
      val defElement = psiElement.parent ?: psiElement
      val text = defElement.text
      // Find the => and include it, but exclude everything after
      val arrowIndex = text.indexOf("=>")
      if (arrowIndex >= 0) {
        val precedingComments = collectPrecedingComments(defElement)
        precedingComments + text.substring(0, arrowIndex + 2)
      } else {
        // No =>, might be a cowith or elim definition, just return the signature part
        null
      }
    }
  }

  private fun buildFunctionSignature(def: Concrete.BaseFunctionDefinition): String {
    val sb = StringBuilder()

    // Function kind (\func, \lemma, \sfunc, etc.)
    sb.append("\\${def.kind.name.lowercase()}")

    // Function name
    sb.append(" ${def.data.refName}")

    // Parameters
    for (param in def.parameters) {
      sb.append(" ")
      val paramSb = StringBuilder()
      param.prettyPrint(paramSb, PrettyPrinterConfig.DEFAULT)
      sb.append(paramSb)
    }

    // Result type
    def.resultType?.let { resultType ->
      sb.append(" : ")
      val typeSb = StringBuilder()
      resultType.prettyPrint(typeSb, PrettyPrinterConfig.DEFAULT)
      sb.append(typeSb)
    }

    // Add => to indicate there's a body (but don't print the body)
    sb.append(" =>")

    return sb.toString()
  }

  // Try to get signature (up to and including =>) from source file for meta definitions
  private fun getMetaSignatureFromSource(def: Concrete.MetaDefinition): String? {
    // def.data is MetaReferable, we need to get its data which contains the PsiElement
    val metaReferable = def.data
    val psiElement = (metaReferable as? org.arend.naming.reference.LocatedReferableImpl)?.data as? PsiElement
    if (psiElement == null) {
      return null
    }
    return runReadAction {
      val defElement = psiElement.parent ?: psiElement
      val text = defElement.text
      // Find the => and include it, but exclude everything after
      val arrowIndex = text.indexOf("=>")
      if (arrowIndex >= 0) {
        val precedingComments = collectPrecedingComments(defElement)
        precedingComments + text.substring(0, arrowIndex + 2)
      } else {
        // No =>, return the whole definition with comments
        val precedingComments = collectPrecedingComments(defElement)
        precedingComments + text
      }
    }
  }

  private fun buildMetaSignature(def: Concrete.MetaDefinition): String {
    val sb = StringBuilder()

    // Meta keyword
    sb.append("\\meta")

    // Meta name
    sb.append(" ${def.data.refName}")

    // Parameters
    for (param in def.parameters) {
      sb.append(" ")
      val paramSb = StringBuilder()
      param.prettyPrint(paramSb, PrettyPrinterConfig.DEFAULT)
      sb.append(paramSb)
    }

    // Add => to indicate there's a body (but don't print the body)
    sb.append(" =>")

    return sb.toString()
  }
}