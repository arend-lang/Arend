package org.arend.aifeatures

data class RequestDataWithModules(
  val modulePaths: List<String>,
  val libPath: String
)

fun parseDataWithModules(encodedPayload: String): RequestDataWithModules {
  if (encodedPayload.isBlank()) {
    return RequestDataWithModules(emptyList(), "")
  }
  val splitPayload = encodedPayload.split("|||")
  if (splitPayload.size == 1) {
    throw IllegalArgumentException("Invalid payload format: missing library path or modules")
  }
  val libPath = splitPayload.last()
  val modulePaths = splitPayload.dropLast(1)

  return RequestDataWithModules(modulePaths, libPath)
}