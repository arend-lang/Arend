package org.arend.frontend.cli.commands;

import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.library.SourceLibrary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code -ag} (ai-guide) handler. Prints the contents of an {@code .aiGuide/<...>.md}
 * file shipped alongside a library's {@code arend.yaml}.
 *
 * <p>Ported from the {@code ShowAiGuideTool} MCP tool on the {@code noannotator-mcp}
 * branch: same resolution rules (module .md, then directory README.md). All storage
 * concerns live behind {@link SourceLibrary#openAuxFile}, so this command works
 * uniformly against directory- and zip-packaged libraries.
 */
public final class AiGuide {
  private AiGuide() {}

  /**
   * Resolve and print the .md guide for the given dotted module path (empty = library
   * root README). Searches every requested library; on a miss in the first match it
   * falls through to the next library, mirroring the original MCP behaviour. Returns
   * false only on usage errors.
   */
  public static boolean run(CommandContext ctx, String modulePath) {
    String module = modulePath == null ? "" : modulePath.trim();

    List<SourceLibrary> libraries = ctx.requestedLibraries;
    if (libraries.isEmpty()) {
      System.err.println("[ERROR] -ag: no library loaded");
      return false;
    }

    List<String> attempted = new ArrayList<>();
    for (SourceLibrary library : libraries) {
      for (String relPath : candidateEntries(module)) {
        attempted.add(library.getLibraryName() + "!/" + relPath);
        String content = tryRead(library, relPath);
        if (content != null) {
          System.out.print(content);
          if (!content.endsWith("\n")) System.out.println();
          return true;
        }
      }
    }

    System.err.println("[ERROR] AI guide not found"
        + (module.isEmpty() ? " (library root README)" : " for module '" + module + "'")
        + ". Tried: " + String.join(", ", attempted));
    return false;
  }

  /**
   * Resolution order: {@code .aiGuide/<dotted>.md}, then {@code .aiGuide/<dotted>/README.md}.
   * Empty module = library-root {@code .aiGuide/README.md} only.
   */
  private static List<String> candidateEntries(String dotted) {
    if (dotted.isEmpty()) return List.of(".aiGuide/README.md");
    String slashed = dotted.replace('.', '/');
    return List.of(".aiGuide/" + slashed + ".md", ".aiGuide/" + slashed + "/README.md");
  }

  private static String tryRead(SourceLibrary library, String relPath) {
    try (InputStream in = library.openAuxFile(relPath)) {
      if (in == null) return null;
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      System.err.println("[ERROR] " + library.getLibraryName() + "!/" + relPath + ": " + e.getMessage());
      return null;
    }
  }
}
