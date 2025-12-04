package org.arend.typechecking.termination;

import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.*;
import org.arend.core.elimtree.Body;
import org.arend.core.elimtree.ElimClause;
import org.arend.core.elimtree.IntervalElim;
import org.arend.core.expr.*;
import org.arend.core.expr.visitor.NormalizeVisitor;
import org.arend.core.pattern.*;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.prelude.Prelude;
import org.arend.typechecking.visitor.SearchVisitor;
import org.arend.ext.util.Pair;

import java.util.*;

public class CollectCallVisitor extends SearchVisitor<Void> {
  private final Set<BaseCallMatrix<Definition>> myCollectedCalls;
  private final FunctionDefinition myDefinition;
  private final Set<? extends Definition> myCycle;
  private List<? extends ExpressionPattern> myPatterns;

  public CollectCallVisitor(FunctionDefinition def, Set<? extends Definition> cycle) {
    assert cycle != null;
    myDefinition = def;
    myCycle = cycle;
    myCollectedCalls = new HashSet<>();

    Body body = def.getReallyActualBody();
    if (body instanceof IntervalElim elim) {
      List<ExpressionPattern> patternList = new ArrayList<>();
      for (DependentLink link = myDefinition.getParameters(); link.hasNext(); link = link.getNext()) {
        patternList.add(new BindingPattern(link));
      }

      myPatterns = patternList;
      int i = patternList.size() - elim.getCases().size();

      for (Pair<Expression, Expression> pair : elim.getCases()) {
        ExpressionPattern old = patternList.get(i);
        patternList.set(i, new ConstructorExpressionPattern(ExpressionFactory.Left(), Collections.emptyList()));
        if (pair.proj1 != null) {
          pair.proj1.accept(this, null);
        }
        patternList.set(i, new ConstructorExpressionPattern(ExpressionFactory.Right(), Collections.emptyList()));
        if (pair.proj2 != null) {
          pair.proj2.accept(this, null);
        }
        patternList.set(i, old);
      }

      body = elim.getOtherwise();
    }

    List<ExpressionPattern> patternList = new ArrayList<>();
    for (DependentLink param = myDefinition.getParameters(); param.hasNext(); param = param.getNext()) {
      patternList.add(new BindingPattern(param));
    }

    myPatterns = patternList;
    myDefinition.getResultType().accept(this, null);
    if (body instanceof Expression) {
      ((Expression) body).accept(this, null);
    }
  }

  public void collect(ElimClause<ExpressionPattern> clause) {
    if (clause.getExpression() != null) {
      myPatterns = clause.getPatterns();
      clause.getExpression().accept(this, null);
    }
  }

  public Set<BaseCallMatrix<Definition>> getResult() {
    return myCollectedCalls;
  }

  private static Expression normalizeFieldCall(Expression expr) {
    while (expr instanceof FieldCallExpression fieldCall) {
      expr = NormalizeVisitor.INSTANCE.evalFieldCall(fieldCall.getDefinition(), fieldCall.getArgument());
    }
    return expr;
  }

  private static Expression removeArgsTyped(Expression expr, Expression type, DataDefinition dataDef) {
    List<Eliminator> eliminators = new ArrayList<>();
    expr = removeArgs(expr, eliminators);

    for (int i = eliminators.size() - 1; i >= 0; i--) {
      type = normalizeFieldCall(type);
      switch (eliminators.get(i)) {
        case PiEliminator ignored -> {
          if (!(type instanceof PiExpression piExpr)) return null;
          type = piExpr.getParameters().getNext().hasNext() ? new PiExpression(piExpr.getParameters().getNext(), piExpr.getCodomain()) : piExpr.getCodomain();
        }
        case SigmaEliminator sigmaEliminator -> {
          if (!(type instanceof SigmaExpression sigmaExpr)) return null;
          DependentLink param = DependentLink.Helper.get(sigmaExpr.getParameters(), sigmaEliminator.field);
          if (!param.hasNext()) return null;
          type = param.getType();
        }
        case PathEliminator ignored -> {
          if (type instanceof FunCallExpression funCall && funCall.getDefinition() == Prelude.PATH_INFIX) {
            type = funCall.getDefCallArguments().getFirst();
          } else if (type instanceof DataCallExpression dataCall && dataCall.getDefinition() == Prelude.PATH) {
            type = dataCall.getDefCallArguments().getFirst();
            if (!(type instanceof LamExpression lamExpr)) return null;
            type = lamExpr.getBody();
          } else {
            return null;
          }
        }
        case ClassEliminator classEliminator -> {
          if (type instanceof FunCallExpression funCall && funCall.getDefinition() == Prelude.ARRAY) {
            type = type.normalize(NormalizationMode.WHNF);
          }
          if (!(type instanceof ClassCallExpression classCall)) return null;
          type = classCall.getDefinition().getFieldType(classEliminator.classField).applyExpression(new ReferenceExpression(classCall.getThisBinding()));
        }
      }
    }

    type = normalizeFieldCall(type);
    return type instanceof DataCallExpression dataCall && dataDef.getRecursiveDefinitions().contains(dataCall.getDefinition()) ? expr : null;
  }

  private sealed interface Eliminator {}
  private record PiEliminator() implements Eliminator {}
  private record SigmaEliminator(int field) implements Eliminator {}
  private record PathEliminator() implements Eliminator {}
  private record ClassEliminator(ClassField classField) implements Eliminator {}

  private static Expression removeArgs(Expression expr, List<Eliminator> eliminators) {
    while (true) {
      switch (expr) {
        case AppExpression ignored -> {
          expr = expr.getFunction();
          if (eliminators != null) eliminators.add(new PiEliminator());
        }
        case ProjExpression projExpression -> {
          expr = projExpression.getExpression();
          if (eliminators != null) eliminators.add(new SigmaEliminator(projExpression.getField()));
        }
        case AtExpression atExpression -> {
          expr = atExpression.getPathArgument();
          if (eliminators != null) eliminators.add(new PathEliminator());
        }
        case FieldCallExpression fieldCallExpression -> {
          expr = fieldCallExpression.getArgument();
          if (eliminators != null) eliminators.add(new ClassEliminator(fieldCallExpression.getDefinition()));
        }
        default -> {
          return expr;
        }
      }
    }
  }

  private static BaseCallMatrix.R isLess(Expression expr1, ExpressionPattern pattern2) {
    if (pattern2 instanceof ConstructorExpressionPattern conPattern) {
      List<? extends Expression> exprArguments = conPattern.getMatchingExpressionArguments(expr1, true);
      DependentLink conParam = conPattern.getParameters();
      List<? extends ExpressionPattern> subPatterns = conPattern.getSubPatterns();
      for (ExpressionPattern arg : subPatterns) {
        if (!conParam.hasNext()) break;
        Expression newExpr1 = conPattern.getConstructor() instanceof Constructor constructor ? removeArgsTyped(expr1, conParam.getType(), constructor.getDataType()) : conPattern.isArray() ? expr1 : null;
        if (newExpr1 != null && isLess(newExpr1, arg) != BaseCallMatrix.R.Unknown) return BaseCallMatrix.R.LessThan;
        conParam = conParam.getNext();
      }

      if (exprArguments != null) {
        for (int i = conPattern.getDefinition() == Prelude.ARRAY_CONS && subPatterns.size() == 3 ? 1 : 0; i < Math.min(exprArguments.size(), subPatterns.size()); i++) {
          BaseCallMatrix.R ord = isLess(exprArguments.get(i), subPatterns.get(i));
          if (ord != BaseCallMatrix.R.Equal) return ord;
        }

        if (exprArguments.size() >= subPatterns.size()) return BaseCallMatrix.R.Equal;
        return BaseCallMatrix.R.Unknown;
      }

      return BaseCallMatrix.R.Unknown;
    } else if (pattern2 instanceof BindingPattern) {
      DependentLink binding2 = ((BindingPattern) pattern2).getBinding();
      if (expr1 instanceof ReferenceExpression refExpr && refExpr.getBinding() == binding2) {
        return BaseCallMatrix.R.Equal;
      }
    }
    return BaseCallMatrix.R.Unknown;
  }

  private static void initMatrixBlock(CallMatrix callMatrix,
                                      Expression expr1, DependentLink param1,
                                      ExpressionPattern pattern2, DependentLink param2) {
    DependentLink param1u = CallMatrix.tryUnfoldDependentLink(param1);
    DependentLink param2u = CallMatrix.tryUnfoldDependentLink(param2);
    List<? extends ExpressionPattern> patternList;
    List<? extends Expression> expressionList;
    if (param2u != null && pattern2 instanceof ConstructorExpressionPattern) {
      patternList = pattern2.getSubPatterns();
    } else {
      patternList = Collections.singletonList(pattern2);
    }
    expressionList = CallMatrix.tryUnfoldExpression(expr1);
    if (param1u == null || expressionList == null) {
      expressionList = Collections.singletonList(expr1);
      param1u = null;
    }

    if (param1u != null || param2u != null) {
      if (param1u == null) param1u = param1;
      if (param2u == null) param2u = param2;
      doProcessLists(callMatrix, param2u, patternList, param1u, expressionList);
      return;
    }

    callMatrix.setBlock(param2, param1, isLess(expr1, pattern2));
  }

  private static void doProcessLists(CallMatrix cm, DependentLink patternIndex, List<? extends ExpressionPattern> patternList, DependentLink argumentIndex, List<? extends Expression> argumentList) {
    for (ExpressionPattern pattern : patternList) {
      DependentLink argIndex = argumentIndex;
      for (Expression argument : argumentList) {
        initMatrixBlock(cm, argument, argIndex, pattern, patternIndex);
        argIndex = argIndex.getNext();
      }
      patternIndex = patternIndex.getNext();
    }
  }

  @Override
  protected CoreExpression.FindAction processDefCall(DefCallExpression expression, Void param) {
    if (!myCycle.contains(expression.getDefinition())) {
      return CoreExpression.FindAction.CONTINUE;
    }

    CallMatrix cm = new CallMatrix(myDefinition, expression);
    List<Expression> args = new ArrayList<>();
    for (Expression arg : expression.getDefCallArguments()) {
      while (arg instanceof TypeConstructorExpression) {
        arg = ((TypeConstructorExpression) arg).getArgument();
      }
      args.add(arg);
    }
    doProcessLists(cm, myDefinition.getParameters(), myPatterns, expression.getDefinition().getParameters(), args);

    myCollectedCalls.add(cm);
    return CoreExpression.FindAction.CONTINUE;
  }

  @Override
  public Boolean visitTypeConstructor(TypeConstructorExpression expr, Void params) {
    if (!myCycle.contains(expr.getDefinition())) {
      return false;
    }
    processDefCall(expr.getType(), null);
    return false;
  }

  public static Set<BaseCallMatrix<Definition>> collectCalls(FunctionDefinition def, Collection<? extends ElimClause<ExpressionPattern>> clauses, Set<? extends Definition> cycle) {
    CollectCallVisitor visitor = new CollectCallVisitor(def, cycle);
    for (ElimClause<ExpressionPattern> clause : clauses) {
      visitor.collect(clause);
    }
    return visitor.getResult();
  }
}
