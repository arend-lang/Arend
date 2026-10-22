package org.arend.frontend.cli;

import org.arend.daemon.mcp.McpRegistration;

import java.nio.file.Path;

/** {@code arend --mcp-init} and {@code arend --mcp-unregister}: the agents' registration of the MCP server, for a project. */
public final class McpCommands {
  private McpCommands() {}

  /**
   * {@code arend --mcp-init [DIR]}: registers the {@code arend-daemons} MCP server in the configs of the
   * project in {@code projectDir} of every agent installed, saying which files it wrote -- as
   * {@code arend --mcp}, so only when the {@code arend} on this PATH, which agents and daemons started
   * from this shell see too, serves this build's daemons.
   */
  public static int initMcp(Path projectDir) {
    McpRegistration.ArendCheck arend = McpRegistration.checkArend(System.getenv("PATH"));
    if (!arend.usable()) {
      System.err.println("[ERROR] --mcp-init: " + McpRegistration.arendAdvice(arend) + " Nothing was registered.");
      return 1;
    }
    McpRegistration.Result result = McpRegistration.initProject(projectDir, McpRegistration.PORTABLE);
    result.failures().forEach(failure -> System.err.println("[WARN]  --mcp-init: " + failure));
    if (result.done().isEmpty()) {
      if (result.failures().isEmpty()) System.out.println(McpRegistration.NO_AGENT);
    } else {
      System.out.println("Registered the " + McpRegistration.SERVER_NAME + " MCP server for the project in " + projectDir
          + ", for agents started there from now on:");
      result.done().forEach(file -> System.out.println("  " + file));
      System.out.println("`arend --mcp-unregister` takes it back out.");
    }
    // After the files: what they say would be lost above them, and it is what the user has to decide on.
    result.warnings().forEach(warning -> System.err.println("[WARN]  --mcp-init: " + warning));
    result.notes().forEach(note -> System.out.println("[INFO] " + note));
    return result.failures().isEmpty() ? 0 : 1;
  }

  /**
   * {@code arend --mcp-unregister [DIR]}: takes the server out of the configs of the project in
   * {@code projectDir}, and nowhere else, printing each entry it removed so that it can be put back.
   */
  public static int unregisterMcp(Path projectDir) {
    McpRegistration.Result result = McpRegistration.unregisterProject(projectDir);
    result.failures().forEach(failure -> System.err.println("[WARN]  --mcp-unregister: " + failure));
    if (result.done().isEmpty()) {
      if (result.failures().isEmpty()) System.out.println("No agent's config of the project in " + projectDir + " has the "
          + McpRegistration.SERVER_NAME + " MCP server.");
    } else {
      result.done().forEach(file -> System.out.println("Removed the " + McpRegistration.SERVER_NAME + " MCP server from " + file));
    }
    result.warnings().forEach(warning -> System.err.println("[WARN]  --mcp-unregister: " + warning));
    return result.failures().isEmpty() ? 0 : 1;
  }
}
