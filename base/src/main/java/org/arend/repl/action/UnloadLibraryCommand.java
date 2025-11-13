package org.arend.repl.action;

import org.arend.prelude.Prelude;
import org.arend.repl.QuitReplException;
import org.arend.repl.Repl;
import org.arend.server.ArendLibrary;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static org.arend.repl.Repl.REPL_NAME;

public class UnloadLibraryCommand implements ReplCommand, DirectoryArgumentCommand {
  public static final @NotNull UnloadLibraryCommand INSTANCE = new UnloadLibraryCommand();

  private UnloadLibraryCommand() {
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Unload an Arend library loaded before";
  }

  @Override
  public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) throws QuitReplException {
    if (!FileUtils.isLibraryName(line)) {
      api.eprintln("[ERROR] `" + line + "` is not a valid library name.");
      return;
    }
    ArendLibrary library = api.createLibrary(line);
    if (library == null) {
      api.eprintln("[ERROR] Library " + line + "` is not exist.");
      return;
    }
    String libraryName = library.getLibraryName();
    if (libraryName.equals(REPL_NAME)) {
      api.eprintln("[ERROR] Repl library cannot be unloaded.");
    } else if (libraryName.equals(Prelude.LIBRARY_NAME)) {
      api.eprintln("[ERROR] Prelude library cannot be unloaded.");
    } else if (!api.getLibraries().contains(libraryName)) {
      api.eprintln("[ERROR] Library " + library + " is not loaded.");
      return;
    }
    api.unloadLibrary(libraryName);
    api.println("[INFO] Library " + libraryName + " has been unloaded.");
    api.checkErrors();
  }
}
