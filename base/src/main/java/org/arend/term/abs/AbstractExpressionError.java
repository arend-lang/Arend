package org.arend.term.abs;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.error.LocalError;
import org.jetbrains.annotations.NotNull;

public class AbstractExpressionError extends LocalError {
  private Object myCause;

  public AbstractExpressionError(@NotNull Level level, String message, Object cause) {
    super(level, message);
    myCause = cause;
  }

  @Override
  public Object getCause() {
    return myCause;
  }

  @Override
  public void setCauseSourceNode(ConcreteSourceNode sourceNode) {
    myCause = sourceNode.getData();
  }

  @NotNull
  @Override
  public Stage getStage() {
    return Stage.PARSER;
  }
}
