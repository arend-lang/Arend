package org.arend.frontend.repl.action;

import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.naming.scope.Scope;
import org.arend.repl.QuitReplException;
import org.arend.repl.action.DirectoryArgumentCommand;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class UnloadLibraryCommand implements CliReplCommand, DirectoryArgumentCommand {
  public static final @NotNull UnloadLibraryCommand INSTANCE = new UnloadLibraryCommand();

  private UnloadLibraryCommand() {
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Unload an Arend library loaded before";
  }

  @Override
  public void invoke(@NotNull String line, @NotNull CommonCliRepl api, @NotNull Supplier<@NotNull String> scanner) throws QuitReplException {
    if (!FileUtils.isLibraryName(line)) {
      api.eprintln("[ERROR] `" + line + "` is not a valid library name.");
      return;
    }
    SourceLibrary library = api.createLibrary(line);
    if (library == null) {
      api.eprintln("[ERROR] Library " + line + "` is not exist.");
      return;
    }
    String libraryName = library.getLibraryName();
    if (!api.getLibraries().contains(libraryName)) {
      api.eprintln("[ERROR] Library " + library + " is not loaded.");
      return;
    }
    boolean isUnloaded = api.unloadLibrary(libraryName);
    assert isUnloaded;
    api.checkErrors();
  }
}
