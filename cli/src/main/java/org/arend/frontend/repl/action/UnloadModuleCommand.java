package org.arend.frontend.repl.action;

import org.arend.ext.module.ModulePath;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.naming.scope.Scope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

import static org.arend.frontend.repl.action.LoadModuleCommand.ALL_MODULES;

public final class UnloadModuleCommand implements CliReplCommand {
  public static final @NotNull UnloadModuleCommand INSTANCE = new UnloadModuleCommand();

  private UnloadModuleCommand() {
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Unload an Arend module loaded before or all modules (:unload all)";
  }

  @Override
  public void invoke(@NotNull String line, @NotNull CommonCliRepl api, @NotNull Supplier<@NotNull String> scanner) {
    var modulePath = ModulePath.fromString(line);
    if (!Objects.equals(modulePath.toString(), ALL_MODULES) && !api.getLoadedModules().contains(modulePath)) {
      api.eprintln("[ERROR] Module " + modulePath + " is not loaded.");
      return;
    }
    if (Objects.equals(modulePath.toString(), ALL_MODULES)) {
      api.clearScope();
    }
    Scope scope = api.getAvailableModuleScopeProvider().forModule(modulePath);
    if (scope != null) api.removeScope(scope);
    api.unloadModule(modulePath);
    api.checkErrors();
  }
}
