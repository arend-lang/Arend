package org.arend.frontend.repl.action;

import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.repl.action.DirectoryArgumentCommand;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class LoadLibraryCommand implements CliReplCommand, DirectoryArgumentCommand {
  public static final @NotNull LoadLibraryCommand INSTANCE = new LoadLibraryCommand();
  public static final @NotNull String CUR_DIR = ".";

  private LoadLibraryCommand() {
  }

  @Override
  public void invoke(@NotNull String line, @NotNull CommonCliRepl api, @NotNull Supplier<@NotNull String> scanner) {
    if (!FileUtils.isLibraryName(line) && !line.equals(CUR_DIR)) {
      api.eprintln("[ERROR] `" + line + "` is not a valid library name.");
      return;
    }
    SourceLibrary library = api.createLibrary(line);
    if (library == null || api.checkErrors()) {
      api.eprintln("[ERROR] Cannot find a library at '" + line + "'.");
      // check again in case `library == null`
      api.checkErrors();
      return;
    }
    if (!api.loadLibrary(library)) {
      api.checkErrors();
      api.eprintln("[ERROR] No library loaded.");
    }
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Load a library of given directory or arend.yaml file";
  }
}
