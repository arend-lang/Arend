package org.arend.error.doc;

import org.arend.core.expr.Expression;
import org.arend.term.prettyprint.PrettyPrinterConfig;

public class TermDoc extends CachingDoc {
  private final Expression myTerm;
  private final PrettyPrinterConfig myPPConfig;

  TermDoc(Expression term, PrettyPrinterConfig ppConfig) {
    myTerm = term;
    myPPConfig = ppConfig;
  }

  public Expression getTerm() {
    return myTerm;
  }

  @Override
  public String getString() {
    StringBuilder builder = new StringBuilder();
    myTerm.prettyPrint(builder, myPPConfig);
    return builder.toString();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public <P, R> R accept(DocVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitTerm(this, params);
  }
}
