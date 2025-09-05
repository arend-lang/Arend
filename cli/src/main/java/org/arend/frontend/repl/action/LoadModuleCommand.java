package org.arend.frontend.repl.action;

import org.arend.ext.module.ModulePath;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.naming.scope.Scope;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class LoadModuleCommand implements CliReplCommand {
  public static final @NotNull LoadModuleCommand INSTANCE = new LoadModuleCommand();
  public static final @NotNull String ALL_MODULES = "all";

  private LoadModuleCommand() {
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Load an Arend module from loaded libraries. Use `:load all` to load all modules.";
  }

  @Override
  public void invoke(@NotNull String line, @NotNull CommonCliRepl api, @NotNull Supplier<@NotNull String> scanner) {
    try {
      if (line.endsWith(FileUtils.EXTENSION)) {
        line = line.substring(0, line.length() - FileUtils.EXTENSION.length());
        var paths = StreamSupport
          .stream(Paths.get(line).normalize().spliterator(), false)
          .map(Objects::toString)
          .collect(Collectors.toList());
        if (Objects.equals(paths.getFirst(), ".")) paths.removeFirst();
        line = String.join(".", paths);
      }
      loadModule(api, ModulePath.fromString(line));
    } catch (InvalidPathException e) {
      api.eprintln("[ERROR] Path `" + line + "` invalid\n" + e.getLocalizedMessage());
    }
  }

  private static void loadModule(@NotNull CommonCliRepl api, ModulePath modulePath) {
    if (!Objects.equals(modulePath.toString(), ALL_MODULES) && !api.getAllModules().contains(modulePath)) {
      api.eprintln("[ERROR] Module " + modulePath + " does not exist.");
      return;
    }
    if (Objects.equals(modulePath.toString(), ALL_MODULES)) {
      api.clearScope();
    }
    Scope existingScope = api.getAvailableModuleScopeProvider().forModule(modulePath);
    if (existingScope != null) api.removeScope(existingScope);
    Scope scope = api.loadModule(modulePath);
    if (scope != null) api.addScope(scope);
    else api.println("[INFO] No module loaded.");
    if (!api.checkErrors()) ReloadModuleCommand.lastModulePath = modulePath;
    else api.unloadModule(modulePath);
  }

  public static class ReloadModuleCommand implements CliReplCommand {
    public static final @NotNull ReloadModuleCommand INSTANCE = new ReloadModuleCommand();

    private ReloadModuleCommand() {
    }

    private volatile static @Nullable ModulePath lastModulePath = null;

    @Override
    public void invoke(@NotNull String line, @NotNull CommonCliRepl api, @NotNull Supplier<@NotNull String> scanner) {
      if (lastModulePath != null)
        LoadModuleCommand.loadModule(api, lastModulePath);
      else api.eprintln("[ERROR] No previous module to load.");
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
      return lastModulePath == null
          ? "Reload the last loaded module"
          : "Reload module `" + lastModulePath + "`";
    }
  }
}
