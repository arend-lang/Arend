package org.arend.repl;

import org.arend.repl.action.AliasableCommand;
import org.arend.repl.action.ReplCommand;
import org.arend.ext.util.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CommandHandler implements ReplHandler {
  public static final @NotNull CommandHandler INSTANCE = new CommandHandler();
  public static final @NotNull HelpCommand HELP_COMMAND_INSTANCE = INSTANCE.createHelpCommand();
  public final @NotNull Map<String, ReplCommand> commandMap = new LinkedHashMap<>();

  private @NotNull HelpCommand createHelpCommand() {
    return new HelpCommand();
  }

  @Override
  public boolean isApplicable(@NotNull String line) {
    return line.startsWith(":");
  }

  /**
   * Split a command.
   * @param line Example: <code>:f a</code>
   * @return Example: <code>new Pair("f", "a")</code>
   */
  public static @NotNull Pair<@Nullable String, @NotNull String> splitCommand(@NotNull String line) {
    if (line.isBlank() || !line.startsWith(":")) return new Pair<>(null, line);
    int indexOfSpace = line.indexOf(' ');
    var command = indexOfSpace > 0 ? line.substring(1, indexOfSpace) : line.substring(1);
    var arguments = indexOfSpace > 0 ? line.substring(indexOfSpace + 1) : "";
    return new Pair<>(command, arguments.trim());
  }

  @Override
  public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> lineSupplier) throws QuitReplException {
    var command = splitCommand(line);
    if (command.proj1 == null) return;
    var replCommand = commandMap.get(command.proj1);
    if (replCommand != null) replCommand.invoke(command.proj2, api, lineSupplier);
    else {
      var suitableCommands = determineEntries(command.proj1).collect(Collectors.toList());
      if (suitableCommands.isEmpty())
        api.eprintln("[ERROR] Unrecognized command: " + command.proj1 + ".");
      else if (suitableCommands.size() >= 2)
        api.eprintln("[ERROR] Cannot distinguish among commands :"
            + suitableCommands.stream().map(Map.Entry::getKey).collect(Collectors.joining(", :"))
            + ", please be more specific.");
      else
        suitableCommands.get(0).getValue().invoke(command.proj2, api, lineSupplier);
    }
  }

  public @NotNull Stream<Map.Entry<String, ReplCommand>> determineEntries(@NotNull String command) {
    return commandMap.entrySet().stream().filter(entry -> entry.getKey().startsWith(command));
  }

  public final class HelpCommand extends AliasableCommand {
    private HelpCommand() {
      super(new ArrayList<>());
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
      return "Show this message (`:? [command name]` to describe a command)";
    }

    @Override
    public @Nls @NotNull String help(@NotNull Repl api) {
      return "Command to show the help message.\n" +
        "Use `:? [command name]` to describe a command (like `:? ?` to show this message).";
    }

    @Override
    public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
      if (line.isBlank()) {
        noArg(api);
        return;
      }
      var replCommand = commandMap.get(line);
      if (replCommand == null) {
        api.eprintln("[ERROR] Cannot find command `:" + line + "`.");
        return;
      }
      api.println(replCommand.help(api));
    }

    /**
     * The {@code :?} listing, as an aligned three-column table: full command name, short
     * name(s), description. Aliasable commands (whose several names would otherwise be joined
     * into one long {@code ":long, :short"} prefix and push their description out of the shared
     * column) contribute their longest alias to the name column and the rest to the short
     * column, so every description lines up regardless of how many aliases a command has.
     */
    private void noArg(@NotNull Repl api) {
      record Row(String name, String shortForm, String description) {}
      List<Row> rows = new ArrayList<>();
      Set<AliasableCommand> seen = new HashSet<>();
      for (var entry : commandMap.entrySet()) {
        ReplCommand cmd = entry.getValue();
        if (cmd instanceof AliasableCommand ac) {
          // A command is registered under each alias, so dedup by the command instance.
          if (!seen.add(ac)) continue;
          // Longest alias is the full name; the shorter ones are the short forms.
          List<String> byLength = ac.aliases.stream()
              .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
              .toList();
          String name = byLength.isEmpty() ? entry.getKey() : byLength.get(0);
          String shortForm = byLength.size() > 1
              ? byLength.subList(1, byLength.size()).stream().map(a -> ":" + a).collect(Collectors.joining(", "))
              : "";
          rows.add(new Row(":" + name, shortForm, cmd.description()));
        } else {
          rows.add(new Row(":" + entry.getKey(), "", cmd.description()));
        }
      }

      int nameWidth = rows.stream().mapToInt(r -> r.name().length()).max().orElse(0);
      int shortWidth = rows.stream().mapToInt(r -> r.shortForm().length()).max().orElse(0);

      api.println("Enter Arend expression, statement (e. g. an \\import-command) or a REPL command");
      api.println("There are " + rows.size() + " commands available.");
      for (Row r : rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.name()).append(" ".repeat(nameWidth - r.name().length() + 2));
        if (shortWidth > 0) {
          sb.append(r.shortForm()).append(" ".repeat(shortWidth - r.shortForm().length() + 2));
        }
        sb.append(r.description());
        api.println(sb.toString().stripTrailing());
      }
      api.println("Note: to use an Arend symbol beginning with `:`, start the line with a whitespace.");
    }
  }
}
