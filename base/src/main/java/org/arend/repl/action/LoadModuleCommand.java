package org.arend.repl.action;

import org.arend.ext.module.ModulePath;
import org.arend.repl.Repl;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.arend.repl.action.ListModulesCommand.ALL_MODULES;

public final class LoadModuleCommand implements ReplCommand {
  public static final @NotNull LoadModuleCommand INSTANCE = new LoadModuleCommand();

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Load an Arend module from loaded libraries. Use `:load all` to load all modules";
  }

  public static ModulePath getModulePath(String line) {
    if (line.endsWith(FileUtils.EXTENSION)) {
      line = line.substring(0, line.length() - FileUtils.EXTENSION.length());
      var paths = StreamSupport
              .stream(Paths.get(line).normalize().spliterator(), false)
              .map(Objects::toString)
              .collect(Collectors.toList());
      if (Objects.equals(paths.getFirst(), ".")) paths.removeFirst();
      line = String.join(".", paths);
    }
    return ModulePath.fromString(line);
  }

  @Override
  public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
    try {
      ModulePath path = getModulePath(line);
      if (api.getLoadedModules().contains(path)) {
        api.eprintln("[ERROR] Module " + path + " is already loaded. Use `:reload` to reload it.");
        return;
      }
      loadModule(api, path);
    } catch (InvalidPathException e) {
      api.eprintln("[ERROR] Path `" + line + "` invalid\n" + e.getLocalizedMessage());
    }
  }

  private static void loadModule(@NotNull Repl api, ModulePath modulePath) {
    if (!Objects.equals(modulePath.toString(), ALL_MODULES) && !api.getAllModules().contains(modulePath)) {
      api.eprintln("[ERROR] Module " + modulePath + " does not exist.");
      return;
    }

    api.loadModule(modulePath);
    if (api.checkErrors()) api.unloadModule(modulePath);
  }

  public static class ReloadModuleCommand implements ReplCommand {
    public static final @NotNull ReloadModuleCommand INSTANCE = new ReloadModuleCommand();

    private ReloadModuleCommand() {
    }

    @Override
    public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
      try {
        LoadModuleCommand.loadModule(api, getModulePath(line));
      } catch (InvalidPathException e) {
        api.eprintln("[ERROR] Path `" + line + "` invalid\n" + e.getLocalizedMessage());
      }
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
      return "Reload module an Arend module from loaded libraries";
    }
  }
}
