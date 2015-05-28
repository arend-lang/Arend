package com.jetbrains.jetpad.vclang.term.error;

import com.jetbrains.jetpad.vclang.term.Abstract;

import java.util.ArrayList;
import java.util.List;

public class TypeCheckingError {
  private final String myMessage;
  private final Abstract.SourceNode myExpression;
  private final List<String> myNames;

  public TypeCheckingError(String message, Abstract.SourceNode expression, List<String> names) {
    myMessage = message;
    myExpression = expression;
    myNames = names;
  }

  public static List<String> getNames(List<? extends Abstract.Binding> context) {
    List<String> names = new ArrayList<>(context.size());
    for (Abstract.Binding binding : context) {
      names.add(binding.getName());
    }
    return names;
  }

  public Abstract.SourceNode getExpression() {
    return myExpression;
  }

  public String getMessage() {
    return myMessage;
  }

  protected String prettyPrint(Abstract.PrettyPrintableSourceNode expression) {
    StringBuilder builder = new StringBuilder();
    expression.prettyPrint(builder, myNames, Abstract.Expression.PREC);
    return builder.toString();
  }

  @Override
  public String toString() {
    String msg = myMessage == null ? "Type checking error" : myMessage;
    if (myExpression instanceof Abstract.PrettyPrintableSourceNode) {
      return msg + " in " + prettyPrint((Abstract.PrettyPrintableSourceNode) myExpression);
    } else {
      return msg;
    }
  }
}
