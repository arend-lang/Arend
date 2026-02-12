package org.arend.aifeatures

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

//TODO : rename to RequestWithModules

data class TypecheckRequestData(
  val modulePaths: List<String>,
  val libraryName: String
)

fun parseTypecheckData(encodedPayload: String): TypecheckRequestData {
  // println("[DEBUG_LOG] parseTypecheckData called with: '$encodedPayload'")
  if (encodedPayload.isBlank()) {
    // println("[DEBUG_LOG] Payload is blank, returning empty TypecheckRequestData")
    return TypecheckRequestData(emptyList(), "")
  }

  return try {
    // Parse as JSON
    // println("[DEBUG_LOG] Attempting JSON parsing...")
    val jsonElement = Json.parseToJsonElement(encodedPayload)
    val jsonObject = jsonElement.jsonObject

    val libraryName = jsonObject["libraryPath"]?.jsonPrimitive?.contentOrNull ?: ""
    val modulePaths = jsonObject["modulePaths"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    // println("[DEBUG_LOG] JSON parsing successful: libraryName='$libraryName', modulePaths=$modulePaths")
    TypecheckRequestData(modulePaths, libraryName)
  } catch (e: Exception) {
    // println("Failed to parse JSON: ${e.message}, payload: $encodedPayload")
    // Fallback to delimiter-based format: libraryName:locationKind:modulePath%%libraryPath
    // The module identifier format is: libraryName:SOURCE:modulePath (e.g., arend-lib:SOURCE:Paths.Meta)
    val delimiter = "%%"
    // println("[DEBUG_LOG] Checking for delimiter '%%' in payload...")
    if (encodedPayload.contains(delimiter)) {
      val parts = encodedPayload.split(delimiter)
      // println("[DEBUG_LOG] Split by '%%': parts=$parts")
      val libraryPath = parts.last()
      // println("[DEBUG_LOG] libraryPath (last part) = '$libraryPath'")
      
      // Extract actual module paths from full module identifiers (libraryName:locationKind:modulePath)
      val modulePaths = parts.dropLast(1).map { fullModuleId ->
        // println("[DEBUG_LOG] Processing fullModuleId: '$fullModuleId'")
        // Split by colon and take the last part as the actual module path
        // Format: libraryName:SOURCE:modulePath -> we need modulePath
        val colonParts = fullModuleId.split(":")
        // println("[DEBUG_LOG] colonParts: $colonParts")
        if (colonParts.size >= 3) {
          // Join all parts after the second colon (in case module path contains colons)
          val modulePath = colonParts.drop(2).joinToString(":")
          // println("[DEBUG_LOG] Extracted modulePath: '$modulePath'")
          modulePath
        } else {
          // If format doesn't match, use as-is
          // println("[DEBUG_LOG] Format doesn't match (colonParts.size=${colonParts.size}), using fullModuleId as-is")
          fullModuleId
        }
      }
      
      // Also extract the library name from the first module identifier
      val firstModuleId = parts.firstOrNull() ?: ""
      val extractedLibraryName = if (firstModuleId.contains(":")) {
        firstModuleId.split(":").firstOrNull() ?: ""
      } else {
        ""
      }
      // println("[DEBUG_LOG] Extracted libraryName from first module: '$extractedLibraryName'")
      
      // println("[DEBUG_LOG] Fallback parsing result: libraryName=$extractedLibraryName, modulePaths=$modulePaths")
      TypecheckRequestData(modulePaths, extractedLibraryName)
    } else {
      // println("[DEBUG_LOG] No delimiter found, returning empty TypecheckRequestData")
      TypecheckRequestData(emptyList(), "")
    }
  }
}