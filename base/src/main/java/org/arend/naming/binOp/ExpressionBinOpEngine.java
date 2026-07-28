package org.arend.naming.binOp;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.reference.Precedence;
import org.arend.ext.util.Pair;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocalReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.renamer.Renamer;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.term.Fixity;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ExpressionBinOpEngine implements BinOpEngine<Concrete.Expression> {

  private static final ExpressionBinOpEngine engine = new ExpressionBinOpEngine();

  private ExpressionBinOpEngine() {
  }

  @Override
  public @Nullable Referable getReferable(@NotNull Concrete.Expression elem) {
    return elem instanceof Concrete.ReferenceExpression ? ((Concrete.ReferenceExpression) elem).getReferent()
            : elem instanceof Concrete.AppExpression appExpr && appExpr.getFunction() instanceof Concrete.ReferenceExpression refExpr ? refExpr.getReferent()
            : elem instanceof Concrete.FieldCallExpression fieldCall ? fieldCall.getField() : null;
  }

  @Override
  public @NotNull Concrete.Expression wrapSequence(Object data, Concrete.@NotNull Expression base, List<@NotNull Pair<? extends Concrete.Expression, Boolean>> explicitComponents) {
    return Concrete.AppExpression.make(data, base, explicitComponents.stream().map((pair) -> new Concrete.Argument(pair.proj1, pair.proj2)).collect(Collectors.toList()));
  }


  @Override
  public @NotNull Concrete.Expression augmentWithLeftReferable(Object data, @NotNull Referable leftRef, Concrete.@NotNull Expression mid, Concrete.Expression right) {
    return new Concrete.LamExpression(data, Collections.singletonList(new Concrete.NameParameter(data, true, leftRef)), BinOpParser.makeBinOp(new Concrete.ReferenceExpression(data, leftRef), mid, right, this));
  }

  @Override
  public @NotNull String getPresentableComponentName() {
    return "expression";
  }

  public static @NotNull Concrete.Expression parse(@NotNull Concrete.BinOpSequenceExpression expression, @NotNull ErrorReporter reporter, @NotNull TypingInfo typingInfo) {
    List<Concrete.BinOpSequenceElem<Concrete.Expression>> sequence = expression.getSequence();
    Concrete.BinOpSequenceElem<Concrete.Expression> first = sequence.getFirst();
    boolean isSection = first.fixity == Fixity.INFIX || first.fixity == Fixity.POSTFIX;
    boolean rebuildFirstAsUnknown = false;
    if (!isSection && first.fixity == Fixity.NONFIX && sequence.size() == 2) {
      // A plain (non-backtick) infix operator applied to exactly one argument in prefix
      // position is treated as a right section, just like `(op x) works today.
      Referable referable = engine.getReferable(first.getComponent());
      Precedence precedence = referable instanceof GlobalReferable ? typingInfo.getRefPrecedence((GlobalReferable) referable) : null;
      if (precedence != null && precedence.isInfix) {
        isSection = true;
        rebuildFirstAsUnknown = true;
      }
    }

    if (isSection) {
      LocalReferable firstArg = new LocalReferable(Renamer.UNNAMED);
      List<Concrete.BinOpSequenceElem<Concrete.Expression>> newSequence = new ArrayList<>(sequence.size() + 1);
      newSequence.add(new Concrete.BinOpSequenceElem<>(new Concrete.ReferenceExpression(expression.getData(), firstArg)));
      if (rebuildFirstAsUnknown) {
        newSequence.add(new Concrete.BinOpSequenceElem<>(first.getComponent(), Fixity.UNKNOWN, first.isExplicit));
        newSequence.addAll(sequence.subList(1, sequence.size()));
      } else {
        newSequence.addAll(sequence);
      }
      return new Concrete.LamExpression(expression.getData(), Collections.singletonList(new Concrete.NameParameter(expression.getData(), true, firstArg)), parse(new Concrete.BinOpSequenceExpression(expression.getData(), newSequence, expression.getClauses()), reporter, typingInfo));
    }

    Concrete.Expression parsed = new BinOpParser<>(typingInfo, reporter, engine).parse(sequence);
    return parsed instanceof Concrete.AppExpression && parsed.getData() != expression.getData()
        ? Concrete.AppExpression.make(expression.getData(), ((Concrete.AppExpression) parsed).getFunction(), ((Concrete.AppExpression) parsed).getArguments())
        : parsed;
  }
}
