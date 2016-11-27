package com.jetbrains.jetpad.vclang.term.pattern;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.term.definition.Constructor;
import com.jetbrains.jetpad.vclang.term.definition.Referable;
import com.jetbrains.jetpad.vclang.term.expr.ConCallExpression;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.term.expr.subst.LevelArguments;
import com.jetbrains.jetpad.vclang.term.expr.visitor.NormalizeVisitor;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.ConCall;
import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.Reference;

public class ConstructorPattern extends Pattern implements Abstract.ConstructorPattern {
  private final Constructor myConstructor;
  private final Patterns myArguments;

  public ConstructorPattern(Constructor constructor, Patterns arguments) {
    assert !constructor.typeHasErrors();
    myConstructor = constructor;
    myArguments = arguments;
  }

  public Constructor getConstructor() {
    return myConstructor;
  }

  @Override
  public String getConstructorName() {
    return myConstructor.getName();
  }

  public Patterns getPatterns() {
    return myArguments;
  }

  @Override
  public List<PatternArgument> getArguments() {
    return myArguments.getPatterns();
  }

  @Override
  public DependentLink getParameters() {
    return myArguments.getParameters();
  }

  @Override
  public Expression toExpression(ExprSubstitution subst, LevelArguments polyParams) {
    List<Expression> params = new ArrayList<>();
    for (DependentLink link = myConstructor.getDataTypeParameters(); link.hasNext(); link = link.getNext()) {
      Expression param = subst.get(link);
      params.add(param == null ? Reference(link) : param);
    }

    DependentLink constructorParameters = myConstructor.getParameters();
    DependentLink link = constructorParameters;
    List<Expression> arguments = new ArrayList<>();
    for (PatternArgument patternArgument : myArguments.getPatterns()) {
      assert link.hasNext();
      LevelArguments polyParams1 = null;
      if (patternArgument.getPattern() instanceof ConstructorPattern) {
        assert link.getType().toExpression() != null;
        Expression type = link.getType().toExpression().subst(subst).normalize(NormalizeVisitor.Mode.WHNF);
        assert type.toDataCall() != null && type.toDataCall().getDefinition() == ((ConstructorPattern) patternArgument.getPattern()).getConstructor().getDataType();
        ExprSubstitution subSubst = ((ConstructorPattern) patternArgument.getPattern()).getMatchedArguments(new ArrayList<>(type.toDataCall().getDefCallArguments()));
        polyParams1 = type.toDataCall().getPolyArguments();
        for (Referable binding : subSubst.getDomain()) {
          subst.add(binding, subSubst.get(binding));
        }
      }
      Expression param = patternArgument.getPattern().toExpression(subst, polyParams1);
      if (patternArgument.getPattern() instanceof ConstructorPattern) {
        DependentLink.Helper.freeSubsts(getParameters(), subst);
      }

      arguments.add(param);
      subst.add(link, param);
      link = link.getNext();
    }
    DependentLink.Helper.freeSubsts(constructorParameters, subst);
    return ConCall(myConstructor, polyParams, params, arguments);
  }

  public ExprSubstitution getMatchedArguments(List<Expression> dataTypeArguments) {
    return DependentLink.Helper.toSubstitution(myConstructor.getDataTypeParameters(), myConstructor.matchDataTypeArguments(dataTypeArguments));
  }

  @Override
  public MatchResult match(Expression expr, boolean normalize) {
    if (normalize) {
      expr = expr.normalize(NormalizeVisitor.Mode.WHNF);
    }

    ConCallExpression conCall = expr.toConCall();
    if (conCall == null) {
      return new MatchMaybeResult(this, expr);
    }
    if (conCall.getDefinition() != myConstructor) {
      return new MatchFailedResult(this, expr);
    }

    if (conCall.getDefCallArguments().size() != myArguments.getPatterns().size()) {
      throw new IllegalStateException();
    }
    return myArguments.match(conCall.getDefCallArguments(), normalize);
  }
}
