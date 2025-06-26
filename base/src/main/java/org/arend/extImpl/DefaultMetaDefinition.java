package org.arend.extImpl;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteCoclauses;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.error.ArgumentExplicitnessError;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.naming.reference.Referable;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.SubstConcreteVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultMetaDefinition implements MetaDefinition {
  private final Concrete.MetaDefinition myDefinition;

  public DefaultMetaDefinition(Concrete.MetaDefinition definition) {
    myDefinition = definition;
  }

  public Concrete.MetaDefinition getConcrete() {
    return myDefinition;
  }

  @Override
  public boolean checkArguments(@NotNull List<? extends ConcreteArgument> arguments) {
    return checkArguments(arguments, null, null, null);
  }

  protected boolean checkArguments(@NotNull List<? extends ConcreteArgument> arguments, @Nullable ConcreteCoclauses coclauses, @Nullable ErrorReporter errorReporter, @Nullable ConcreteSourceNode marker) {
    for (var argument : arguments) {
      if (!argument.isExplicit()) {
        if (errorReporter != null) {
          errorReporter.report(new ArgumentExplicitnessError(false, argument.getExpression()));
        }
        return false;
      }
    }

    int params = 0;
    for (Concrete.Parameter parameter : myDefinition.getParameters()) {
      params += parameter.getRefList().size();
    }
    boolean ok = arguments.size() >= params;
    if (!ok && errorReporter != null) {
      errorReporter.report(new TypecheckingError("Expected " + params + " arguments, found " + arguments.size(), marker));
    }
    return ok;
  }

  @Override
  public boolean checkContextData(@NotNull ContextData contextData, @NotNull ErrorReporter errorReporter) {
    return checkArguments(contextData.getArguments(), contextData.getCoclauses(), errorReporter, contextData.getMarker());
  }

  protected @Nullable ConcreteExpression getConcreteRepresentation(@Nullable Object data, @Nullable List<Concrete.LevelExpression> pLevels, @Nullable List<Concrete.LevelExpression> hLevels, @NotNull List<? extends ConcreteArgument> arguments, @Nullable ConcreteCoclauses coclauses) {
    if (myDefinition.body == null) return null;
    List<Referable> refs = new ArrayList<>();
    for (Concrete.Parameter parameter : myDefinition.getParameters()) {
      refs.addAll(parameter.getRefList());
    }
    assert refs.size() <= arguments.size();

    var subst = new SubstConcreteVisitor(data);
    for (int i = 0; i < refs.size(); i++) {
      Referable ref = refs.get(i);
      if (ref != null) {
        subst.bind(ref, (Concrete.Expression) arguments.get(i).getExpression());
      }
    }

    binLevelParameters(subst, pLevels, myDefinition.getPLevelParameters());
    binLevelParameters(subst, hLevels, myDefinition.getHLevelParameters());
    Concrete.Expression result = myDefinition.body.accept(subst, null);
    if (result == null) return null;
    if (arguments.size() > refs.size()) {
      result = new ConcreteFactoryImpl(result.getData()).app(result, arguments.subList(refs.size(), arguments.size()));
    }
    if (coclauses != null) {
      if (!(coclauses instanceof Concrete.Coclauses)) {
        throw new IllegalArgumentException();
      }
      result = Concrete.ClassExtExpression.make(result.getData(), result, (Concrete.Coclauses) coclauses);
    }
    return result;
  }

  private void binLevelParameters(SubstConcreteVisitor subst, @Nullable List<Concrete.LevelExpression> levels, Concrete.LevelParameters levelParameters) {
    if (levelParameters != null && levels != null) {
      for (int i = 0; i < levelParameters.referables.size() && i < levels.size(); i++) {
        subst.bind(levelParameters.referables.get(i), levels.get(i));
      }
    }
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    ConcreteReferenceExpression refExpr = contextData.getReferenceExpression();
    if (!(refExpr instanceof Concrete.ReferenceExpression)) {
      throw new IllegalStateException();
    }
    ConcreteExpression result = getConcreteRepresentation(refExpr.getData(), ((Concrete.ReferenceExpression) refExpr).getPLevels(), ((Concrete.ReferenceExpression) refExpr).getHLevels(), contextData.getArguments(), contextData.getCoclauses());
    if (result == null) {
      typechecker.getErrorReporter().report(new TypecheckingError("Meta '" + myDefinition.getData().getRefName() + "' is not defined", contextData.getMarker()));
      return null;
    }
    return typechecker.typecheck(result, contextData.getExpectedType());
  }
}
