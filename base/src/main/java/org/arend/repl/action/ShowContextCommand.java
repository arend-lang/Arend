package org.arend.repl.action;

import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.repl.QuitReplException;
import org.arend.repl.Repl;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class ShowContextCommand implements ReplCommand {
  public static final @NotNull ShowContextCommand INSTANCE = new ShowContextCommand();
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Override
  public @NotNull String description() {
    return "Prints Repl context";
  }

  @Override
  public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) throws QuitReplException {
    StringBuilder builder = new StringBuilder();
    for (ConcreteStatement statement: api.getStatements()) {
      ConcreteNamespaceCommand command = statement.command();
      ConcreteGroup group = statement.group();
      Concrete.ResolvableDefinition definition = group == null ? null : group.definition();
      if (command != null) command.prettyPrint(builder, PrettyPrinterConfig.DEFAULT);
      if (definition != null) definition.prettyPrint(builder, PrettyPrinterConfig.DEFAULT);
      builder.append("\n");
    }
    api.print(builder.toString());
  }
}
