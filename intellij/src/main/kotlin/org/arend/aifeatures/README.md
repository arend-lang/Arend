# AI Features in Arend Plugin

This package contains tools and services for AI-assisted features in the Arend IntelliJ plugin, specifically using the Model Context Protocol (MCP).

## How to Add a New MCP Tool

Follow these steps to add a new tool that can be used by an AI assistant via MCP.

### 1. Create a New Tool Class

Create a new Kotlin class in the `org.arend.aifeatures.mcpTools` package. This class must implement the `McpTool` interface.

```kotlin
package org.arend.aifeatures.mcpTools

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.arend.aifeatures.McpTool

class MyNewTool : McpTool {
    override val name = "mcp_arend_my_new_tool"
    override val description = "Describe what your tool does here."

    override fun getInputSchema(): JsonObject = buildJsonObject {
        // Define the parameters your tool accepts
        put("param1", "string")
        put("param2", "integer")
    }

    override fun execute(project: Project, arguments: String): String {
        // Implement the tool logic here
        // 'arguments' is typically a JSON string or a custom formatted string
        return "Result of executing MyNewTool"
    }
}
```

### 2. Implement the Interface Members

- **`name`**: A unique identifier for the tool. Use the prefix `mcp_arend_`.
- **`description`**: A clear explanation of what the tool does and how to use it. This is used by the LLM to understand when to call this tool.
- **`getInputSchema()`**: Returns a `JsonObject` representing the JSON schema for the tool's input parameters.
- **`execute(project: Project, arguments: String)`**: The actual logic of the tool. It receives the current IntelliJ `Project` and the `arguments` string from the LLM. It should return a `String` result.

### 3. Register the Tool

Once you have created your tool class, you must register it in `McpToolFactory.kt` so it can be discovered by the system.

Open `intellij/src/main/kotlin/org/arend/aifeatures/McpToolFactory.kt` and add an instance of your tool to the `createAllTools()` function:

```kotlin
object McpToolFactory {
  fun createAllTools(): List<McpTool> {
    return listOf(
      ProofSearcherTool(),
      ListModulesTool(),
      MyNewTool(), // Add your new tool here
    )
  }
}
```

### 4. Build and Test

After registering the tool, rebuild the project. The tool should now be available via the MCP server provided by the plugin.
