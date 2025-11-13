package org.arend.repl.action;

import org.arend.ext.module.ModulePath;
import org.arend.repl.QuitReplException;
import org.arend.repl.Repl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class ImportCommand implements ReplCommand {
  public static final @NotNull ImportCommand INSTANCE = new ImportCommand();

  private ImportCommand() {
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Import an Arend module";
  }

  @Override
  public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) throws QuitReplException {
    ModulePath modulePath = ModulePath.fromString(line);
    if (!api.getLoadedModules().contains(modulePath)) {
      api.println("[INFO] The module `" + modulePath + "` is not loaded.");
      return;
    }
    api.checkStatements("\\import " + modulePath);
  }
}
