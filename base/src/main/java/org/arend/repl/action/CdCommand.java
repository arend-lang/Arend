package org.arend.repl.action;

import org.arend.repl.Repl;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class CdCommand implements ReplCommand, DirectoryArgumentCommand {
  public static final @NotNull String PARENT_DIR = "..";

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "Modify current working directory";
  }

  @Override
  public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
    if (line.isBlank()) {
      api.pwd = FileUtils.USER_HOME;
      return;
    }
    Path newPath = api.pwd.resolve(line).normalize();
    if (Files.notExists(newPath)) {
      api.eprintln("[ERROR] No such file or directory: `" + newPath + "`.");
      return;
    }
    if (Files.isDirectory(newPath))
      api.pwd = newPath;
    else {
      api.eprintln("[ERROR] Directory expected, found file: `" + newPath + "`.");
    }
  }
}
