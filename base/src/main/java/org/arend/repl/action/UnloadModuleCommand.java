package org.arend.repl.action;

import org.arend.ext.module.ModulePath;
import org.arend.prelude.Prelude;
import org.arend.repl.Repl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

import static org.arend.repl.Repl.replModuleLocation;
import static org.arend.repl.action.ListModulesCommand.ALL_MODULES;

public final class UnloadModuleCommand implements ReplCommand {
  public static final @NotNull UnloadModuleCommand INSTANCE = new UnloadModuleCommand();

  private UnloadModuleCommand() {
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Unload an Arend module loaded before or all modules (:unload all)";
  }

  @Override
  public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
    var modulePath = ModulePath.fromString(line);
    if (!Objects.equals(modulePath.toString(), ALL_MODULES) && !api.getLoadedModules().contains(modulePath)) {
      api.eprintln("[ERROR] Module " + modulePath + " is not loaded.");
      return;
    }
    if (modulePath == replModuleLocation.getModulePath()) {
      api.eprintln("[ERROR] Repl module cannot be unloaded.");
      return;
    }
    if (modulePath == Prelude.MODULE_PATH) {
      api.eprintln("[ERROR] Repl module cannot be unloaded.");
      return;
    }
    api.unloadModule(modulePath);
    if (Objects.equals(modulePath.toString(), ALL_MODULES)) {
      api.println("[INFO] All modules have been unloaded.");
    } else {
      api.println("[INFO] Module " + modulePath + " has been unloaded.");
    }
    api.checkErrors();
  }
}
