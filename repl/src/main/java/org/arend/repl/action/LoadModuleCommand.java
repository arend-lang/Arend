package org.arend.repl.action;

import org.arend.ext.module.ModulePath;
import org.arend.naming.scope.Scope;
import org.arend.repl.ReplApi;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.util.Scanner;

public final class LoadModuleCommand implements ReplCommand {
  public static final @NotNull LoadModuleCommand INSTANCE = new LoadModuleCommand();

  private LoadModuleCommand() {
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Load an Arend module from working directory.";
  }

  @Override
  public void invoke(@NotNull String line, @NotNull ReplApi api, @NotNull Scanner scanner) {
    try {
      loadModule(api, ModulePath.fromString(line));
    } catch (InvalidPathException e) {
      api.eprintln("The path `" + line + "` is not good because:");
      api.eprintln(e.getLocalizedMessage());
    }
  }

  private static void loadModule(@NotNull ReplApi api, ModulePath modulePath) {
    Scope existingScope = api.getAvailableModuleScopeProvider().forModule(modulePath);
    if (existingScope != null) api.removeScope(existingScope);
    Scope scope = api.loadModule(modulePath);
    if (scope != null) api.addScope(scope);
    else api.println("[INFO] No module loaded.");
    if (!api.checkErrors()) ReloadModuleCommand.lastModulePath = modulePath;
  }

  public static class ReloadModuleCommand implements ReplCommand {
    public static final @NotNull ReloadModuleCommand INSTANCE = new ReloadModuleCommand();

    private ReloadModuleCommand() {
    }

    private volatile static @Nullable ModulePath lastModulePath = null;

    @Override
    public void invoke(@NotNull String line, @NotNull ReplApi api, @NotNull Scanner scanner) {
      if (lastModulePath != null)
        LoadModuleCommand.loadModule(api, lastModulePath);
      else api.eprintln("[ERROR] No previous module to load.");
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @Nullable String description() {
      return lastModulePath == null
          ? "Reload the module loaded last time"
          : "Reload module `" + lastModulePath + "`";
    }
  }
}
