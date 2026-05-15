package org.arend.frontend.cli.commands;

import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.SourceLibrary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The {@code -ag} (ai-guide) handler. Prints the contents of an {@code .aiGuide/<...>.md}
 * file shipped alongside a library's {@code arend.yaml}.
 *
 * <p>Ported from the {@code ShowAiGuideTool} MCP tool on the {@code noannotator-mcp}
 * branch: same file-resolution rules (module .md, then directory README.md), now driven
 * by a CLI flag instead of a JSON request.
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

    List<String> errors = new ArrayList<>();
    for (SourceLibrary library : libraries) {
      if (!(library instanceof FileSourceLibrary fileLib)) {
        errors.add("Library '" + library.getLibraryName() + "' is not a file-based library");
        continue;
      }
      Path aiGuideDir = aiGuideRoot(fileLib);
      if (aiGuideDir == null) {
        errors.add("Library '" + library.getLibraryName() + "' has no resolvable root");
        continue;
      }

      String content = readGuide(aiGuideDir, module, errors, library.getLibraryName());
      if (content != null) {
        System.out.print(content);
        if (!content.endsWith("\n")) System.out.println();
        return true;
      }
    }

    for (String err : errors) {
      System.err.println("[ERROR] " + err);
    }
    return false;
  }

  /**
   * Library root used as the .aiGuide parent. Prefers the basePath (the directory
   * holding {@code arend.yaml}); falls back to {@code sourceBasePath.parent} for
   * libraries built without going through {@code fromConfigFile} (e.g. {@code -s}).
   */
  private static Path aiGuideRoot(FileSourceLibrary lib) {
    Path base = lib.getBasePath();
    if (base == null) {
      Path src = lib.getSourceBasePath();
      base = src == null ? null : src.getParent();
    }
    return base == null ? null : base.resolve(".aiGuide");
  }

  /**
   * Try {@code <aiGuideDir>/<dotted>.md}, then {@code <aiGuideDir>/<dotted>/README.md}.
   * Empty dotted path = root README.md. Returns null and appends a descriptive
   * error if nothing matches.
   */
  private static String readGuide(Path aiGuideDir, String dotted, List<String> errors, String libName) {
    if (dotted.isEmpty()) {
      Path readme = aiGuideDir.resolve("README.md");
      if (Files.isRegularFile(readme)) {
        return readFile(readme, errors);
      }
      errors.add("README.md not found for library '" + libName + "' (looked at " + readme + ")");
      return null;
    }

    List<String> parts = Arrays.asList(dotted.split("\\."));
    Path mdFile = aiGuideDir.resolve(String.join("/", parts) + ".md");
    if (Files.isRegularFile(mdFile)) {
      return readFile(mdFile, errors);
    }

    Path dirReadme = aiGuideDir.resolve(String.join("/", parts)).resolve("README.md");
    if (Files.isRegularFile(dirReadme)) {
      return readFile(dirReadme, errors);
    }

    errors.add("AI guide not found for module '" + dotted + "' in library '" + libName
        + "' (looked at " + mdFile + " and " + dirReadme + ")");
    return null;
  }

  private static String readFile(Path file, List<String> errors) {
    try {
      return Files.readString(file);
    } catch (java.io.IOException e) {
      errors.add("Failed to read " + file + ": " + e.getMessage());
      return null;
    }
  }
}
