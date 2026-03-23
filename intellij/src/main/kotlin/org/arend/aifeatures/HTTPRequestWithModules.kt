package org.arend.aifeatures

data class RequestDataWithModules(
  val modulePaths: List<String>,
  val libPath: String,
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


data class RequestDataWithModuleAndLines(
  val modulePaths: String,
  val libPath: String,
  val lineStart: Int,
  val lineEnd: Int,
)

fun parseDataWithModulesAndLines(encodedPayload: String): RequestDataWithModuleAndLines {
  if (encodedPayload.isBlank()) {
    return RequestDataWithModuleAndLines("", "", 0, 0)
  }
  val splitPayload = encodedPayload.split("|||")
  if (splitPayload.size < 3 ) {
    throw IllegalArgumentException("Invalid payload format: missing library path or module or lines")
  }
//  The format is modulePath|||lines|||libPath
//  Lines is a string of the form "start-end" or "start"
  val libPath = splitPayload.last()
  val linesInStr = splitPayload.dropLast(1).last()
  val modulePath = splitPayload.dropLast(2).first()
  val line1 = linesInStr.split("-").first().toIntOrNull() ?: 0
  val line2 = linesInStr.split("-").last().toIntOrNull() ?: (line1 + 500)

  return RequestDataWithModuleAndLines(modulePath, libPath, line1, line2)
}