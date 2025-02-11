package org.arend.typechecking.error.local;

import org.arend.core.context.binding.Binding;
import org.arend.core.expr.Expression;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.error.GeneralError;
import org.arend.ext.prettifier.ExpressionPrettifier;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.typechecking.GoalSolver;
import org.arend.ext.typechecking.InteractiveGoalSolver;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.TypecheckingContext;
import org.arend.typechecking.patternmatching.Condition;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class GoalError extends GoalDataHolder {
  public final Concrete.Expression result;
  public final List<GeneralError> errors;
  public final GoalSolver goalSolver;
  public final Collection<? extends InteractiveGoalSolver> additionalSolvers;
  public final String goalName;
  public final Concrete.GoalExpression goalExpression;

  private List<Condition> myConditions = Collections.emptyList();

  public GoalError(ExpressionPrettifier prettifier,
                   TypecheckingContext typecheckingContext,
                   Map<Binding, Expression> bindingTypes,
                   Expression expectedType,
                   Concrete.Expression result,
                   List<GeneralError> errors,
                   GoalSolver goalSolver,
                   Concrete.GoalExpression expression) {
    super(prettifier, Level.GOAL, "Goal" + (expression.getName() == null ? "" : " " + expression.getName()), expression,
            typecheckingContext, bindingTypes, expectedType);
    this.result = result;
    this.errors = errors;
    this.goalSolver = goalSolver;
    this.additionalSolvers = goalSolver.getAdditionalSolvers();
    this.goalExpression = expression;
    goalName = expression.getName() == null ? "" : expression.getName();
  }

  @Override
  public void setCauseSourceNode(ConcreteSourceNode sourceNode) {
    super.setCauseSourceNode(sourceNode);
    goalExpression.setData(sourceNode.getData());
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    Doc expectedDoc = getExpectedDoc(ppConfig);
    Doc contextDoc = getContextDoc(ppConfig);
    Doc conditionsDoc = getConditionsDoc(ppConfig);
    Doc errorsDoc = getErrorsDoc(ppConfig);
    return vList(expectedDoc, contextDoc, conditionsDoc, errorsDoc);
  }

  @NotNull
  private Doc getConditionsDoc(PrettyPrinterConfig ppConfig) {
    if (myConditions != null && !myConditions.isEmpty()) {
      List<Doc> conditionsDocs = new ArrayList<>(myConditions.size());
      for (Condition condition : myConditions) {
        conditionsDocs.add(condition.toDoc(ppConfig));
      }
      return hang(text("Conditions:"), vList(conditionsDocs));
    }
    return nullDoc();
  }

  @NotNull
  private Doc getErrorsDoc(PrettyPrinterConfig ppConfig) {
    if (!errors.isEmpty()) {
      List<Doc> errorsDocs = new ArrayList<>(errors.size());
      for (GeneralError error : errors) {
        errorsDocs.add(hang(error.getHeaderDoc(ppConfig), error.getBodyDoc(ppConfig)));
      }
      return hang(text("Errors:"), vList(errorsDocs));
    }
    return nullDoc();
  }

  public void removeConditions() {
    myConditions = null;
  }

  public boolean hasConditions() {
    return myConditions != null;
  }

  public void addCondition(Condition condition) {
    if (myConditions != null) {
      if (myConditions.isEmpty()) {
        myConditions = new ArrayList<>();
      }
      myConditions.add(condition);
    }
  }

  public List<? extends Condition> getConditions() {
    return myConditions == null ? Collections.emptyList() : myConditions;
  }
}
