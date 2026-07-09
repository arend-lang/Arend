package org.arend.naming.binOp;

import org.arend.error.ParsingError;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.reference.Precedence;
import org.arend.ext.error.NameResolverError;
import org.arend.ext.util.Pair;
import org.arend.naming.error.PrecedenceError;
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
import java.util.List;

// package-local on purpose, this class should be accessed via its users like ExpressionBinOpEngine
class BinOpParser<T extends Concrete.SourceNode> {
  private final TypingInfo myTypingInfo;
  private final ErrorReporter myErrorReporter;
  private final List<StackElem<T>> myStack;
  private final BinOpEngine<T> myEngine;
  // Data of the whole sequence being parsed, or null when the caller does not supply it (pattern
  // parsing). Used by wrapperData as the `data` of a synthetic application wrapper around a
  // complete-value operand (e.g. a section lambda) so the wrapper does not reuse (and thus collide
  // with) that operand's data. See ArendBinOpUtils.exprToConcrete1, which relies on `data`
  // identifying a unique PSI node.
  private final Object mySequenceData;

  BinOpParser(TypingInfo typingInfo, ErrorReporter errorReporter, BinOpEngine<T> engine) {
    this(typingInfo, errorReporter, engine, null);
  }

  BinOpParser(TypingInfo typingInfo, ErrorReporter errorReporter, BinOpEngine<T> engine, Object sequenceData) {
    myTypingInfo = typingInfo;
    myErrorReporter = errorReporter;
    myEngine = engine;
    mySequenceData = sequenceData;
    myStack = new ArrayList<>();
  }

  private static class StackElem<T> {
    public @NotNull T component;
    public @Nullable Precedence precedence;

    StackElem(@NotNull T component, @Nullable Precedence precedence) {
      this.component = component;
      this.precedence = precedence;
    }
  }

  @NotNull T parse(@NotNull List<Concrete.BinOpSequenceElem<T>> sequence) {
    for (Concrete.BinOpSequenceElem<T> elem : sequence) {
      Referable referable = myEngine.getReferable(elem.getComponent());
      Precedence precedence = referable instanceof GlobalReferable ? myTypingInfo.getRefPrecedence((GlobalReferable) referable) : null;

      if (referable != null && (elem.fixity == Fixity.INFIX || elem.fixity == Fixity.POSTFIX || elem.fixity == Fixity.UNKNOWN && precedence != null && precedence.isInfix)) {
        if (precedence == null) {
          precedence = Precedence.DEFAULT;
        }
        push(elem.getComponent(), precedence, elem.fixity == Fixity.POSTFIX && !precedence.isInfix);
      } else {
        push(elem.getComponent(), elem.isExplicit);
      }
    }

    return rollUp();
  }

  public void push(T component, boolean isExplicit) {
    if (myStack.isEmpty()) {
      if (!isExplicit) {
        // This should never happen if the binOp expression is correct
        myErrorReporter.report(new ParsingError("Expected an explicit " + myEngine.getPresentableComponentName(), component));
      }
      myStack.add(new StackElem<>(component, null));
      return;
    }

    StackElem<T> topElem = myStack.get(myStack.size() - 1);
    if (topElem.precedence == null || !isExplicit) {
      topElem.component = myEngine.wrapSequence(wrapperData(topElem.component), topElem.component, List.of(Pair.create(component, isExplicit)));
    } else {
      myStack.add(new StackElem<>(component, null));
    }
  }

  // Data for a synthetic application wrapper built around `operand`. Normally the operand's own data
  // (the operand is the application head, whose data is the natural source anchor that consumers such
  // as ArendSubExprUtils.rangeOfConcrete rely on). But a complete-value expression operand -- e.g. the
  // lambda produced for a `` `op `` section -- has its own PSI, so reusing its data would make the
  // wrapper collide with it in ArendBinOpUtils.exprToConcrete1; such wrappers get the sequence data.
  private Object wrapperData(T operand) {
    return operand instanceof Concrete.Expression expr && !(expr instanceof Concrete.ReferenceExpression || expr instanceof Concrete.FieldCallExpression)
      ? mySequenceData
      : operand.getData();
  }

  public void push(T component, @NotNull Precedence precedence, boolean isPostfix) {
    if (myStack.isEmpty()) {
      myStack.add(new StackElem<>(component, precedence));
      return;
    }

    while (true) {
      StackElem<T> topElem = myStack.get(myStack.size() - 1);
      if (topElem.precedence != null) {
        myErrorReporter.report(new NameResolverError("Expected " + myEngine.getPresentableComponentName() + " after an infix operator", topElem.component));
        return;
      }

      StackElem<T> nextElem = myStack.size() == 1 ? null : myStack.get(myStack.size() - 2);
      if (nextElem == null || nextElem.precedence == null || nextElem.precedence.priority < precedence.priority || nextElem.precedence.priority == precedence.priority && nextElem.precedence.associativity == Precedence.Associativity.RIGHT_ASSOC && (isPostfix || precedence.associativity == Precedence.Associativity.RIGHT_ASSOC)) {
        if (isPostfix) {
          myStack.set(myStack.size() - 1, new StackElem<>(myEngine.wrapSequence(component.getData(), component, List.of(Pair.create(topElem.component, true))), null));
        } else {
          myStack.add(new StackElem<>(component, precedence));
        }
        return;
      }

      if (!(nextElem.precedence.priority > precedence.priority || nextElem.precedence.associativity == Precedence.Associativity.LEFT_ASSOC && (isPostfix || precedence.associativity == Precedence.Associativity.LEFT_ASSOC))) {
        myErrorReporter.report(new PrecedenceError(myEngine.getReferable(nextElem.component), nextElem.precedence, myEngine.getReferable(component), precedence, component));
      }

      foldTop();
    }
  }

  private void foldTop() {
    StackElem<T> topElem = myStack.remove(myStack.size() - 1);
    if (topElem.precedence != null && myStack.size() > 1) {
      StackElem<T> nextElem = myStack.get(myStack.size() - 2);
      myErrorReporter.report(new NameResolverError("The operator " + myEngine.getReferable(topElem.component) + " [" + topElem.precedence + "] of a section must have lower precedence than that of the operand, namely " + myEngine.getReferable(nextElem.component) + " [" + nextElem.precedence + "]", topElem.component));
      topElem = myStack.remove(myStack.size() - 1);
    }
    StackElem<T> midElem = myStack.remove(myStack.size() - 1);
    StackElem<T> botElem = myStack.isEmpty() ? null : myStack.remove(myStack.size() - 1);

    if (botElem == null) {
      if (topElem.precedence != null) {
        myStack.add(new StackElem<>(myEngine.wrapSequence(midElem.component.getData(), topElem.component, List.of(Pair.create(midElem.component, true))), null));
      } else {
        Referable leftRef = new LocalReferable(Renamer.UNNAMED);
        myStack.add(new StackElem<>(myEngine.augmentWithLeftReferable(midElem.component.getData(), leftRef, midElem.component, topElem.component), null));
      }
    } else {
      myStack.add(new StackElem<>(makeBinOp(botElem.component, midElem.component, topElem.component, myEngine), null));
    }
  }

  static <T extends Concrete.SourceNode> T makeBinOp(T left, T var, T right, BinOpEngine<T> engine) {
    T expr = engine.wrapSequence(var.getData(), var, List.of(Pair.create(left, true)));
    return right == null ? expr : engine.wrapSequence(var.getData(), expr, List.of(Pair.create(right, true)));
  }

  public T rollUp() {
    while (myStack.size() > 1) {
      foldTop();
    }

    T result = myStack.get(0).component;
    myStack.clear();
    return result;
  }
}
