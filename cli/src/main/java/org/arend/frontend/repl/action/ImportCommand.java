package org.arend.frontend.repl.action;

import org.arend.ext.module.ModulePath;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.repl.QuitReplException;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static org.arend.frontend.repl.jline.ModuleCompleter.IMPORT_KW;

public class ImportCommand implements CliReplCommand {
  public static final @NotNull ImportCommand INSTANCE = new ImportCommand();

  private ImportCommand() {
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Import an Arend module";
  }

  @Override
  public void invoke(@NotNull String line, @NotNull CommonCliRepl api, @NotNull Supplier<@NotNull String> scanner) throws QuitReplException {
    ModulePath modulePath = ModulePath.fromString(line);
    if (!api.getLoadedModules().contains(modulePath)) {
      api.eprintln("[ERROR] Module " + modulePath + " is not loaded.");
      return;
    }
    api.typecheckModules(api.getLocationsByModulePath(modulePath));
    ConcreteGroup group = api.parseStatements(IMPORT_KW + " " + line);
    if (group != null) {
      api.statements.addAll(group.statements());
    }
  }
}
