package org.arend.frontend.repl.jline;

import org.arend.ext.module.ModuleLocation;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.prelude.Prelude;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.arend.repl.Repl.*;
import static org.arend.repl.action.LoadLibraryCommand.CUR_DIR;
import static org.arend.repl.action.ListModulesCommand.ALL_MODULES;

public class ModuleCompleter implements Completer {
  private final CommonCliRepl repl;
  private final Supplier<Stream<String>> moduleSupplier;
  public static final String IMPORT_KW = "\\import";
  static final String IMPORT_COMMAND = ":import";
  static final String LOAD_COMMAND = ":load";
  static final String UNLOAD_COMMAND = ":unload";
  static final String MODULES_COMMAND = ":modules";
  static final String LIB_COMMAND = ":lib";

  static final int MAX_DEPTH_MODULES = 10;
  static final String MODULE_PATH_DELIMITER_REGEX = "\\.";
  static final String MODULE_PATH_DELIMITER = ".";

  public ModuleCompleter(CommonCliRepl repl, @NotNull Supplier<Stream<String>> moduleSupplier) {
    this.repl = repl;
    this.moduleSupplier = moduleSupplier;
  }

  @Override
  public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    if (line.words().isEmpty()) return;
    String firstWord = line.words().getFirst();
    if (Objects.equals(MODULES_COMMAND, firstWord)) {
      candidates.add(new Candidate(ALL_MODULES));
      return;
    }
    if (Objects.equals(LIB_COMMAND, firstWord)) {
      candidates.add(new Candidate(CUR_DIR));
      return;
    }

    if (!Objects.equals(IMPORT_KW, firstWord)
      && !Objects.equals(LOAD_COMMAND, firstWord)
      && !Objects.equals(UNLOAD_COMMAND, firstWord)
      && !Objects.equals(IMPORT_COMMAND, firstWord)) return;
    List<String> loadedModulePaths = repl.getLoadedModuleLocations().stream()
      .filter(moduleLocation -> moduleLocation != Prelude.MODULE_LOCATION && moduleLocation != replModuleLocation)
      .map(ModuleLocation::getModulePath).map(Object::toString).toList();
    if (line.wordIndex() == 1) {
      if (Objects.equals(LOAD_COMMAND, firstWord)) {
        candidates.addAll(moduleSupplier.get().map(Candidate::new).toList());
      } else {
        candidates.addAll(loadedModulePaths.stream().map(Candidate::new).toList());
      }
      if ((Objects.equals(LOAD_COMMAND, firstWord) || Objects.equals(UNLOAD_COMMAND, firstWord))) {
        candidates.add(new Candidate(ALL_MODULES));
      }
      return;
    }
    for (int i = 2; i <= MAX_DEPTH_MODULES; i++) {
      if (line.wordIndex() == i) {
        if (i % 2 == 0 && line.words().size() > i && !Objects.equals(line.words().get(i), MODULE_PATH_DELIMITER)) {
          break;
        } else if  (i % 2 == 1 && !Objects.equals(line.words().get(i - 1), MODULE_PATH_DELIMITER)) {
          break;
        }
        int index = i / 2;
        List<String> longModules = moduleSupplier.get().filter(s -> s.split(MODULE_PATH_DELIMITER_REGEX).length > index).toList();
        List<String> correctModules = new ArrayList<>();
        for (String longModule : longModules) {
          List<String> path = Arrays.stream(longModule.split(MODULE_PATH_DELIMITER_REGEX)).toList();
          boolean isCorrect = true;
          for (int j = 1; j < i; j += 2) {
            String word = line.words().get(j);
            if (!Objects.equals(path.get(j / 2), word)) {
              isCorrect = false;
              break;
            }
          }
          if ((Objects.equals(UNLOAD_COMMAND, firstWord) || Objects.equals(IMPORT_KW, firstWord) || Objects.equals(IMPORT_COMMAND, firstWord))
              && !loadedModulePaths.contains(longModule)) {
            isCorrect = false;
          }
          if (isCorrect) {
            correctModules.add(String.join(MODULE_PATH_DELIMITER, path.subList(index, path.size())));
          }
        }
        if (i % 2 == 0) {
          candidates.addAll(correctModules.stream().map(s -> new Candidate(MODULE_PATH_DELIMITER + s, s, null, null, null, null, true, 0)).toList());
        } else {
          candidates.addAll(correctModules.stream().map(Candidate::new).toList());
        }
      }
    }
  }
}
