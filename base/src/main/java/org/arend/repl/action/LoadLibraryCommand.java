package org.arend.repl.action;

import org.arend.repl.Repl;
import org.arend.server.ArendLibrary;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class LoadLibraryCommand implements ReplCommand, DirectoryArgumentCommand {
  public static final @NotNull LoadLibraryCommand INSTANCE = new LoadLibraryCommand();
  public static final @NotNull String CUR_DIR = ".";

  private LoadLibraryCommand() {
  }

  @Override
  public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
    if (!FileUtils.isLibraryName(line) && !line.equals(CUR_DIR)) {
      api.eprintln("[ERROR] `" + line + "` is not a valid library name.");
      return;
    }
    ArendLibrary library = api.createLibrary(line);
    if (library != null) {
      api.loadLibrary(library);
      if (api.checkErrors()) {
        api.eprintln("[ERROR] Cannot find a library at '" + line + "'.");
      } else {
        api.println("[INFO] Library " + library.getLibraryName() + " has been loaded.");
      }
    } else {
      api.eprintln("[ERROR] No library loaded.");
    }
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Load a library of given directory or arend.yaml file";
  }
}
