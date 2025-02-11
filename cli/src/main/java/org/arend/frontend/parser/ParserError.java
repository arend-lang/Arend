package org.arend.frontend.parser;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.error.GeneralError;
import org.arend.ext.reference.ArendRef;
import org.arend.naming.reference.FullModuleReferable;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public class ParserError extends GeneralError {
  public Position position;

  public ParserError(Position position, String message) {
    super(Level.ERROR, message);
    this.position = position;
  }

  public ParserError(Level level, Position position, String message) {
    super(level, message);
    this.position = position;
  }

  @Override
  public Position getCause() {
    return position;
  }

  @Override
  public void setCauseSourceNode(ConcreteSourceNode sourceNode) {
    if (sourceNode.getData() instanceof Position pos) {
      position = pos;
    }
  }

  @Override
  public void forAffectedDefinitions(BiConsumer<ArendRef, GeneralError> consumer) {
    consumer.accept(new FullModuleReferable(position.module), this);
  }

  @NotNull
  @Override
  public Stage getStage() {
    return Stage.PARSER;
  }
}
