package org.arend.term.concrete;

import org.arend.naming.reference.Referable;

import java.util.List;

public class SearchConcreteVisitor<P,R> implements ConcreteExpressionVisitor<P,R>, ConcreteResolvableDefinitionVisitor<P,R> {
  protected R checkSourceNode(Concrete.SourceNode sourceNode, P params) {
    return null;
  }

  @Override
  public R visitApp(Concrete.AppExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    for (Concrete.Argument arg : expr.getArguments()) {
      result = arg.expression.accept(this, params);
      if (result != null) return result;
    }
    return expr.getFunction().accept(this, params);
  }

  @Override
  public R visitReference(Concrete.ReferenceExpression expr, P params) {
    return checkSourceNode(expr, params);
  }

  @Override
  public R visitFieldCall(Concrete.FieldCallExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    return expr.argument.accept(this, params);
  }

  @Override
  public R visitThis(Concrete.ThisExpression expr, P params) {
    return checkSourceNode(expr, params);
  }

  public void visitReferable(Referable referable, P params) {}

  public R visitParameter(Concrete.Parameter parameter, P params) {
    R result = checkSourceNode(parameter, params);
    if (result != null) return result;
    result = parameter.getType() == null ? null : parameter.getType().accept(this, params);
    if (result != null) return result;
    for (Referable referable : parameter.getReferableList()) {
      if (referable != null) visitReferable(referable, params);
    }
    return null;
  }

  public R visitParameters(List<? extends Concrete.Parameter> parameters, P params) {
    for (Concrete.Parameter parameter : parameters) {
      R result = visitParameter(parameter, params);
      if (result != null) return result;
    }
    return null;
  }

  public void freeReferable(Referable referable, P params) {}

  public void freeParameter(Concrete.Parameter parameter, P params) {
    for (Referable referable : parameter.getReferableList()) {
      if (referable != null) freeReferable(referable, params);
    }
  }

  public void freeParameters(List<? extends Concrete.Parameter> parameters, P params) {
    for (Concrete.Parameter parameter : parameters) {
      freeParameter(parameter, params);
    }
  }

  public void freeEliminatedParameters(List<? extends Concrete.Parameter> parameters, List<? extends Concrete.ReferenceExpression> eliminated, P params) {
    for (Concrete.ReferenceExpression reference : eliminated) {
      freeReferable(reference.getReferent(), params);
    }
  }

  public void freeNotEliminatedParameters(List<? extends Concrete.Parameter> parameters, List<? extends Concrete.ReferenceExpression> eliminated, P params) {
    if (eliminated.isEmpty()) freeParameters(parameters, params);
  }

  @Override
  public R visitLam(Concrete.LamExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    result = visitParameters(expr.getParameters(), params);
    if (result != null) return result;
    result = expr.getBody().accept(this, params);
    if (result != null) return result;
    freeParameters(expr.getParameters(), params);
    return null;
  }

  @Override
  public R visitPi(Concrete.PiExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    result = visitParameters(expr.getParameters(), params);
    if (result != null) return result;
    result = expr.codomain.accept(this, params);
    if (result != null) return result;
    freeParameters(expr.getParameters(), params);
    return null;
  }

  @Override
  public R visitUniverse(Concrete.UniverseExpression expr, P params) {
    return checkSourceNode(expr, params);
  }

  @Override
  public R visitHole(Concrete.HoleExpression expr, P params) {
    return checkSourceNode(expr, params);
  }

  @Override
  public R visitApplyHole(Concrete.ApplyHoleExpression expr, P params) {
    return checkSourceNode(expr, params);
  }

  @Override
  public R visitGoal(Concrete.GoalExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    return expr.expression != null ? expr.expression.accept(this, params) : null;
  }

  @Override
  public R visitTuple(Concrete.TupleExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    for (Concrete.Expression field : expr.getFields()) {
      result = field.accept(this, params);
      if (result != null) return result;
    }
    return null;
  }

  @Override
  public R visitSigma(Concrete.SigmaExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    result = visitParameters(expr.getParameters(), params);
    if (result != null) return result;
    freeParameters(expr.getParameters(), params);
    return null;
  }

  @Override
  public R visitBinOpSequence(Concrete.BinOpSequenceExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    for (Concrete.BinOpSequenceElem<Concrete.Expression> elem : expr.getSequence()) {
      result = elem.getComponent().accept(this, params);
      if (result != null) return result;
    }
    return visitClauses(expr.getClauseList(), params);
  }

  public R visitPatterns(List<? extends Concrete.Pattern> patterns, P params) {
    for (Concrete.Pattern pattern : patterns) {
      R result = visitPattern(pattern, params);
      if (result != null) return result;
    }
    return null;
  }

  public R visitPattern(Concrete.Pattern pattern, P params) {
    R result = checkSourceNode(pattern, params);
    if (result != null) return result;

    if (pattern.getAsReferable() != null && pattern.getAsReferable().type != null) {
      result = pattern.getAsReferable().type.accept(this, params);
      if (result != null) return result;
    }

    result = switch (pattern) {
      case Concrete.NamePattern namePattern -> namePattern.type != null ? namePattern.type.accept(this, params) : null;
      case Concrete.ConstructorPattern constructorPattern -> visitPatterns(constructorPattern.getPatterns(), params);
      case Concrete.TuplePattern tuplePattern -> visitPatterns(tuplePattern.getPatterns(), params);
      default -> null;
    };
    if (result != null) return result;

    if (pattern instanceof Concrete.NamePattern namePattern && namePattern.getReferable() != null) {
      visitReferable(namePattern.getReferable(), params);
    }
    if (pattern.getAsReferable() != null) {
      Referable ref = pattern.getAsReferable().referable;
      if (ref != null) visitReferable(ref, params);
    }

    return null;
  }

  public void freePattern(Concrete.Pattern pattern, P params) {
    if (pattern.getAsReferable() != null && pattern.getAsReferable().referable != null) {
      freeReferable(pattern.getAsReferable().referable, params);
    }
    if (pattern instanceof Concrete.NamePattern namePattern && namePattern.getReferable() != null) {
      freeReferable(namePattern.getReferable(), params);
    }
    freePatterns(pattern.getPatterns(), params);
  }

  public void freePatterns(List<? extends Concrete.Pattern> patterns, P params) {
    if (patterns != null) for (Concrete.Pattern pattern : patterns) {
      freePattern(pattern, params);
    }
  }

  public R visitClause(Concrete.Clause clause, P params) {
    R result = checkSourceNode(clause, params);
    if (result != null) return result;
    result = clause.getPatterns() == null ? null : visitPatterns(clause.getPatterns(), params);
    if (result != null) return result;
    result = clause.getExpression() != null ? clause.getExpression().accept(this, params) : null;
    if (result != null) return result;
    if (clause instanceof Concrete.ConstructorClause conClause) {
      for (Concrete.Constructor constructor : conClause.getConstructors()) {
        result = visitConstructor(constructor, params);
        if (result != null) return result;
      }
    }
    freePatterns(clause.getPatterns(), params);
    return null;
  }

  public R visitClauses(List<? extends Concrete.Clause> clauses, P params) {
    for (Concrete.Clause clause : clauses) {
      R result = visitClause(clause, params);
      if (result != null) return result;
    }
    return null;
  }

  @Override
  public R visitCase(Concrete.CaseExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;

    for (Concrete.CaseArgument arg : expr.getArguments()) {
      result = arg.expression.accept(this, params);
      if (result != null) return result;
      result = arg.type != null ? arg.type.accept(this, params) : null;
      if (result != null) return result;
      if (arg.referable != null) visitReferable(arg.referable, params);
    }
    if (expr.getResultType() != null) {
      result = expr.getResultType().accept(this, params);
      if (result != null) return result;
    }
    if (expr.getResultTypeLevel() != null) {
      result = expr.getResultTypeLevel().accept(this, params);
      if (result != null) return result;
    }
    for (Concrete.CaseArgument arg : expr.getArguments()) {
      if (arg.referable != null) freeReferable(arg.referable, params);
    }
    return visitClauses(expr.getClauses(), params);
  }

  @Override
  public R visitEval(Concrete.EvalExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    return expr.getExpression().accept(this, params);
  }

  @Override
  public R visitBox(Concrete.BoxExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    return expr.getExpression().accept(this, params);
  }

  @Override
  public R visitProj(Concrete.ProjExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    return expr.expression.accept(this, params);
  }

  public R visitClassFieldImpl(Concrete.ClassFieldImpl fieldImpl, P params) {
    R result = checkSourceNode(fieldImpl, params);
    if (result != null) return result;

    result = fieldImpl.implementation == null ? null : fieldImpl.implementation.accept(this, params);
    if (result != null) return result;
    for (Concrete.ClassFieldImpl subFieldImpl : fieldImpl.getSubCoclauseList()) {
      result = visitClassFieldImpl(subFieldImpl, params);
      if (result != null) return result;
    }
    return null;
  }

  @Override
  public R visitClassExt(Concrete.ClassExtExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;

    for (Concrete.ClassFieldImpl fieldImpl : expr.getStatements()) {
      result = visitClassFieldImpl(fieldImpl, params);
      if (result != null) return result;
    }
    return expr.getBaseClassExpression().accept(this, params);
  }

  @Override
  public R visitNew(Concrete.NewExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    return expr.expression.accept(this, params);
  }

  @Override
  public R visitLet(Concrete.LetExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;

    for (Concrete.LetClause clause : expr.getClauses()) {
      result = visitPattern(clause.getPattern(), params);
      if (result != null) return result;
      result = visitParameters(clause.getParameters(), params);
      if (result != null) return result;
      if (clause.resultType != null) {
        result = clause.resultType.accept(this, params);
        if (result != null) return result;
      }
      result = clause.term.accept(this, params);
      if (result != null) return result;
    }

    result = expr.expression.accept(this, params);
    if (result != null) return result;

    for (Concrete.LetClause clause : expr.getClauses()) {
      freeParameters(clause.getParameters(), params);
      freePattern(clause.getPattern(), params);
    }

    return null;
  }

  @Override
  public R visitNumericLiteral(Concrete.NumericLiteral expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    return expr.getResolvedExpression() == null ? null : expr.getResolvedExpression().accept(this, params);
  }

  @Override
  public R visitStringLiteral(Concrete.StringLiteral expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;
    return expr.getResolvedExpression() == null ? null : expr.getResolvedExpression().accept(this, params);
  }

  @Override
  public R visitTyped(Concrete.TypedExpression expr, P params) {
    R result = checkSourceNode(expr, params);
    if (result != null) return result;

    result = expr.expression.accept(this, params);
    return result != null ? result : expr.type.accept(this, params);
  }

  @Override
  public R visitFunction(Concrete.BaseFunctionDefinition def, P params) {
    R result = checkSourceNode(def, params);
    if (result != null) return result;

    result = visitParameters(def.getParameters(), params);
    if (result != null) return result;
    if (def.getResultType() != null) {
      result = def.getResultType().accept(this, params);
      if (result != null) return result;
    }
    if (def.getResultTypeLevel() != null) {
      result = def.getResultTypeLevel().accept(this, params);
      if (result != null) return result;
    }

    Concrete.FunctionBody body = def.getBody();
    freeEliminatedParameters(def.getParameters(), body.getEliminatedReferences(), params);
    if (body instanceof Concrete.TermFunctionBody) {
      result = checkSourceNode(body, params);
      if (result != null) return result;
      result = ((Concrete.TermFunctionBody) body).getTerm().accept(this, params);
      if (result != null) return result;
    }
    result = visitClauses(body.getClauses(), params);
    if (result != null) return result;
    result = visitClassElements(body.getCoClauseElements(), params);
    if (result != null) return result;
    freeNotEliminatedParameters(def.getParameters(), body.getEliminatedReferences(), params);
    return null;
  }

  protected R visitClassElements(List<? extends Concrete.ClassElement> elements, P params) {
    for (Concrete.ClassElement element : elements) {
      R result = checkSourceNode(element, params);
      if (result != null) return result;

      switch (element) {
        case Concrete.ClassField field -> {
          result = visitParameters(field.getParameters(), params);
          if (result != null) return result;
          result = field.getResultType().accept(this, params);
          if (result != null) return result;
          if (field.getResultTypeLevel() != null) {
            result = field.getResultTypeLevel().accept(this, params);
            if (result != null) return result;
          }
          freeParameters(field.getParameters(), params);
        }
        case Concrete.ClassFieldImpl classField -> {
          result = visitClassFieldImpl(classField, params);
          if (result != null) return result;
        }
        case Concrete.OverriddenField field -> {
          result = visitParameters(field.getParameters(), params);
          if (result != null) return result;
          result = field.getResultType().accept(this, params);
          if (result != null) return result;
          if (field.getResultTypeLevel() != null) {
            result = field.getResultTypeLevel().accept(this, params);
            if (result != null) return result;
          }
          freeParameters(field.getParameters(), params);
        }
        case null, default -> throw new IllegalStateException();
      }
    }
    return null;
  }

  public R visitConstructor(Concrete.Constructor constructor, P params) {
    R result = checkSourceNode(constructor, params);
    if (result != null) return result;

    result = visitParameters(constructor.getParameters(), params);
    if (result != null) return result;
    if (constructor.getResultType() != null) {
      result = constructor.getResultType().accept(this, params);
      if (result != null) return result;
    }
    if (!constructor.getEliminatedReferences().isEmpty()) {
      freeEliminatedParameters(constructor.getParameters(), constructor.getEliminatedReferences(), params);
      result = visitClauses(constructor.getClauses(), params);
      if (result != null) return result;
    }

    freeNotEliminatedParameters(constructor.getParameters(), constructor.getEliminatedReferences(), params);
    return null;
  }

  @Override
  public R visitData(Concrete.DataDefinition def, P params) {
    R result = checkSourceNode(def, params);
    if (result != null) return result;

    result = visitParameters(def.getParameters(), params);
    if (result != null) return result;
    if (def.getUniverse() != null) {
      result = def.getUniverse().accept(this, params);
      if (result != null) return result;
    }
    if (def.getEliminatedReferences() != null) {
      freeEliminatedParameters(def.getParameters(), def.getEliminatedReferences(), params);
    }
    visitClauses(def.getConstructorClauses(), params);

    if (def.getEliminatedReferences() == null) {
      freeParameters(def.getParameters(), params);
    } else {
      freeNotEliminatedParameters(def.getParameters(), def.getEliminatedReferences(), params);
    }
    return null;
  }

  @Override
  public R visitClass(Concrete.ClassDefinition def, P params) {
    R result = checkSourceNode(def, params);
    if (result != null) return result;

    for (Concrete.ReferenceExpression superClass : def.getSuperClasses()) {
      result = visitReference(superClass, params);
      if (result != null) return result;
    }
    return visitClassElements(def.getElements(), params);
  }

  @Override
  public R visitMeta(Concrete.MetaDefinition def, P params) {
    R result = checkSourceNode(def, params);
    if (result != null) return result;
    result = visitParameters(def.getParameters(), params);
    if (result != null) return result;
    result = def.body != null ? def.body.accept(this, params) : null;
    if (result != null) return result;
    freeParameters(def.getParameters(), params);
    return null;
  }
}
