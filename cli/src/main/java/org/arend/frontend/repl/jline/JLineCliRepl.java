package org.arend.frontend.repl.jline;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.repl.CommandHandler;
import org.arend.repl.action.PrettyPrintFlagCommand;
import org.arend.repl.action.DirectoryArgumentCommand;
import org.arend.repl.action.FileArgumentCommand;
import org.arend.repl.action.NormalizeCommand;
import org.arend.server.ArendLibrary;
import org.arend.server.ArendServer;
import org.arend.server.impl.ArendServerImpl;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class JLineCliRepl extends CommonCliRepl {
  private static final String INSERT_NEWLINE_WIDGET = "insert-newline";
  private static final String CLEAR_INPUT_WIDGET = "arend-clear-input";
  private static final String CLOSE_HINT = "Press Ctrl+D to close";
  /**
   * Escape sequences for the "new line, keep editing" key on terminals that need one beyond Ctrl+J.
   * Alt+Enter (ESC-prefixed Enter) is emitted distinctly by most terminals but may be swallowed by
   * the window manager; Shift+Enter only by terminals that report it separately from Enter (the
   * CSI-u/kitty protocol or xterm {@code modifyOtherKeys}). Ctrl+J (bound directly) is the portable
   * fallback: it always arrives as LF, distinct from the Enter key's CR.
   */
  private static final List<String> NEWLINE_KEY_SEQUENCES = List.of(
    "\033\r", "\033\n",            // Alt+Enter
    "\033[13;2u", "\033[27;2;13~"  // Shift+Enter
  );

  private final Terminal myTerminal;

  public JLineCliRepl(@NotNull Terminal terminal, @NotNull ArendServer server, @NotNull Collection<? extends Path> libDirs) {
    super(server, libDirs);
    myTerminal = terminal;
  }

  @Override
  public void eprintln(Object anything) {
    println(new AttributedStringBuilder()
        .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
        .append(String.valueOf(anything))
        .style(AttributedStyle.DEFAULT)
        .toAnsi());
  }

  @Override
  public void print(Object anything) {
    var writer = myTerminal.writer();
    writer.print(anything);
    writer.flush();
  }

  @Override
  public void println(Object anything) {
    myTerminal.writer().println(anything);
  }

  @Override
  public void println() {
    myTerminal.writer().println();
  }

  public void runRepl() {
    Path dir = FileUtils.USER_HOME.resolve(FileUtils.USER_CONFIG_DIR);
    Path history = dir.resolve("history");
    try {
      // Assuming user.home exists
      if (Files.notExists(dir) || Files.isRegularFile(dir)) {
        Files.deleteIfExists(dir);
        Files.createDirectory(dir);
      }
      if (Files.notExists(history) || Files.isDirectory(history)) {
        Files.deleteIfExists(history);
        Files.createFile(history);
      }
    } catch (IOException e) {
      eprintln("[ERROR] Failed to load REPL history: " + e.getLocalizedMessage());
      history = null;
    }
    // Built by hand (rather than via LineReaderBuilder) so we can use an ArendLineReader subclass
    // that exposes the region below the prompt for the Ctrl+C hint.
    Map<String, Object> variables = new HashMap<>();
    variables.put(LineReader.HISTORY_FILE, history);
    // Show the prompt only on the first line; continuation lines of multi-line input get none.
    variables.put(LineReader.SECONDARY_PROMPT_PATTERN, "");
    var reader = new ArendLineReader(myTerminal, APP_NAME, variables);
    reader.setHistory(new DefaultHistory());
    reader.setParser(new ArendReplParser());
    reader.setCompleter(new AggregateCompleter(
      new SpecialCommandCompleter(DirectoryArgumentCommand.class, new Completers.DirectoriesCompleter(() -> pwd)),
      new SpecialCommandCompleter(FileArgumentCommand.class, new Completers.FilesCompleter(() -> pwd)),
      new SpecialCommandCompleter(NormalizeCommand.class, new StringsCompleter(NormalizeCommand.AVAILABLE_OPTIONS)),
      new SpecialCommandCompleter(PrettyPrintFlagCommand.class, new StringsCompleter(PrettyPrintFlagCommand.AVAILABLE_OPTIONS)),
      new SpecialCommandCompleter(CommandHandler.HelpCommand.class, new StringsCompleter(CommandHandler.INSTANCE.commandMap.keySet())),
      new ScopeCompleter(() -> getInScopeElements(myServer, getStatements())),
      new ModuleCompleter(this, this::modulePaths),
      KeywordCompleter.INSTANCE,
      CommandsCompleter.INSTANCE
    ));
    configureKeyBindings(reader);

    // Disable terminal signal generation so Ctrl+C arrives as a keystroke (handled by the
    // CLEAR_INPUT_WIDGET) instead of raising SIGINT. enterRawMode preserves this flag, so it holds
    // for the whole session; we restore the original attributes when the REPL exits.
    Attributes originalAttributes = myTerminal.getAttributes();
    Attributes replAttributes = new Attributes(originalAttributes);
    replAttributes.setLocalFlag(Attributes.LocalFlag.ISIG, false);
    myTerminal.setAttributes(replAttributes);
    try {
      while (true) try {
        reader.clearMessage();
        reader.readLine(prompt());
        String buffer = reader.getBuffer().toString();
        if (processCommand(buffer, reader)) break;
      } catch (UserInterruptException e) {
        // Safety net: on terminals where ISIG cannot be disabled, Ctrl+C still arrives as an
        // interrupt. Discard the line (erase input) and keep the REPL running.
      } catch (EndOfFileException e) {
        // Ctrl+D on an empty line quits
        break;
      }
    } finally {
      myTerminal.setAttributes(originalAttributes);
    }
    saveUserConfig();
  }

  /**
   * Ctrl+Space triggers completion just like Tab; Ctrl+J inserts a line break so that a definition
   * can span several lines instead of being submitted (the Enter key still submits); and Ctrl+C
   * erases the current input, hinting how to quit when the input is already empty. Alt+Enter and
   * Shift+Enter also insert a line break on terminals that report them distinctly from Enter.
   */
  private void configureKeyBindings(@NotNull ArendLineReader reader) {
    reader.getWidgets().put(INSERT_NEWLINE_WIDGET, () -> {
      reader.getBuffer().write('\n');
      return true;
    });
    reader.getWidgets().put(CLEAR_INPUT_WIDGET, () -> {
      if (reader.getBuffer().length() > 0) {
        reader.getBuffer().clear();
        reader.clearMessage();
      } else {
        // Nothing to erase: tell the user how to actually leave the REPL.
        reader.setMessage(CLOSE_HINT);
      }
      return true;
    });
    KeyMap<Binding> keyMap = reader.getKeyMaps().get(LineReader.MAIN);
    keyMap.bind(new Reference(LineReader.EXPAND_OR_COMPLETE), KeyMap.ctrl(' '));
    keyMap.bind(new Reference(CLEAR_INPUT_WIDGET), KeyMap.ctrl('C'));
    // Ctrl+J (LF) is distinct from the Enter key (CR) on every terminal, so it works everywhere.
    keyMap.bind(new Reference(INSERT_NEWLINE_WIDGET), KeyMap.ctrl('J'));
    for (String sequence : NEWLINE_KEY_SEQUENCES) {
      keyMap.bind(new Reference(INSERT_NEWLINE_WIDGET), sequence);
    }
  }

  /**
   * A {@link LineReaderImpl} that exposes the region rendered below the prompt (the same area used
   * for completion candidates) so the REPL can show — and clear — a transient hint there.
   */
  private static final class ArendLineReader extends LineReaderImpl {
    ArendLineReader(@NotNull Terminal terminal, @NotNull String appName, @NotNull Map<String, Object> variables) {
      super(terminal, appName, variables);
    }

    void setMessage(@NotNull String message) {
      post = () -> new AttributedString(message);
    }

    void clearMessage() {
      post = null;
    }

    @Override
    protected boolean selfInsert() {
      // Any typed character dismisses the hint.
      post = null;
      return super.selfInsert();
    }
  }

  /**
   * Runs a command with terminal echo and the cursor turned off, so that neither the
   * caret nor any keys typed while the command executes are shown until it finishes.
   * Keystrokes entered meanwhile stay buffered and are handled by the next
   * {@link LineReader#readLine}. If a command asks for more input interactively, echo
   * and the cursor are restored for the duration of that read.
   */
  private boolean processCommand(@NotNull String buffer, @NotNull LineReader reader) {
    Attributes savedAttributes = myTerminal.enterRawMode();
    setCursorVisible(false);
    Supplier<String> lineSupplier = () -> {
      setCursorVisible(true);
      try {
        return reader.readLine();
      } finally {
        setCursorVisible(false);
      }
    };
    try {
      return repl(buffer, lineSupplier);
    } finally {
      myTerminal.setAttributes(savedAttributes);
      setCursorVisible(true);
    }
  }

  private void setCursorVisible(boolean visible) {
    myTerminal.puts(visible ? Capability.cursor_normal : Capability.cursor_invisible);
    myTerminal.flush();
  }

  @NotNull
  private Stream<String> modulePaths() {
    return getAllModules().stream().map(LongName::toString);
  }

  public static void main(String... args) {
    launch(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), new ArendServerImpl(new CliServerRequester(new LibraryManager(DummyErrorReporter.INSTANCE)), false, false, true));
  }

  public static void launch(
    @NotNull Collection<? extends ArendLibrary> requestedLibraries,
    @NotNull Collection<? extends ModulePath> autoloadModules,
    @NotNull Collection<? extends Path> libDirs,
    @NotNull ArendServer server
  ) {
    Terminal terminal;
    try {
      terminal = TerminalBuilder
        .builder()
        .encoding("UTF-8")
        .jansi(true)
        .jna(false)
        .build();
    } catch (IOException e) {
      System.err.println("[FATAL] Failed to create terminal: " + e.getLocalizedMessage());
      System.exit(1);
      return;
    }
    var repl = new JLineCliRepl(terminal, server, libDirs);
    repl.initialize();
    repl.println(ASCII_BANNER);
    repl.println();
    repl.loadStartupTargets(requestedLibraries, autoloadModules);
    repl.runRepl();
  }

}
