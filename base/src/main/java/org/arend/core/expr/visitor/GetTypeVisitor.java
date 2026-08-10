package org.arend.core.expr.visitor;

import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.ClassField;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.*;
import org.arend.core.expr.let.HaveClause;
import org.arend.core.expr.let.LetClause;
import org.arend.core.sort.SortExpression;
import org.arend.core.subst.ExprSubstitution;
import org.arend.core.subst.Levels;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.prelude.Prelude;
import org.arend.util.SingletonList;

import java.util.*;

import static org.arend.core.expr.ExpressionFactory.*;

public class GetTypeVisitor implements ExpressionVisitor<Void, Expression> {
  public final static GetTypeVisitor INSTANCE = new GetTypeVisitor(true);
  public final static GetTypeVisitor NN_INSTANCE = new GetTypeVisitor(false);

  private final boolean myNormalizing;

  private GetTypeVisitor(boolean normalizing) {
    myNormalizing = normalizing;
  }

  GetTypeVisitor() {
    this(true);
  }

  @Override
  public Expression visitApp(AppExpression expr, Void params) {
    Expression result = expr.getFunction().accept(this, null).applyExpression(expr.getArgument(), myNormalizing);
    return result == null ? new ErrorExpression() : result;
  }

  @Override
  public Expression visitFunCall(FunCallExpression expr, Void params) {
    FunctionDefinition definition = expr.getDefinition();
    List<? extends Expression> arguments = expr.getDefCallArguments();
    if (definition == Prelude.DIV_MOD || definition == Prelude.MOD) {
      Expression arg2 = arguments.get(1);
      IntegerExpression integer = arg2.cast(IntegerExpression.class);
      ConCallExpression conCall = arg2.cast(ConCallExpression.class);
      if (integer != null && !integer.isZero() || conCall != null && conCall.getDefinition() == Prelude.SUC) {
        return definition == Prelude.MOD ? Fin(arg2) : finDivModType(arg2);
      } else {
        return definition == Prelude.MOD ? Nat() : Prelude.DIV_MOD_TYPE;
      }
    }

    List<DependentLink> defParams = new ArrayList<>();
    Expression type = definition.getTypeWithParams(defParams, expr.getLevels());
    assert arguments.size() == defParams.size();

    if (type instanceof UniverseExpression universe) {
      return new UniverseExpression(universe.getSortExpression().subst(arguments, LevelSubstitution.EMPTY, this));
    } else {
      return type.subst(DependentLink.Helper.toSubstitution(defParams, arguments));
    }
  }

  @Override
  public UniverseExpression visitDataCall(DataCallExpression expr, Void params) {
    return new UniverseExpression(expr.getDefinition().getSortExpression().subst(expr.getDefCallArguments(), expr.getLevels().makeSubstitution(expr.getDefinition()), this));
  }

  public Expression getFieldCallType(ClassField field, ClassCallExpression type, Expression argument) {
    if (type.getDefinition().getOverriddenType(field) != null) {
      return type.getDefinition().getOverriddenType(field, type.getLevels()).applyExpression(argument);
    }
    return type.getFieldType(field, argument);
  }

  @Override
  public Expression visitFieldCall(FieldCallExpression expr, Void params) {
    Expression type = expr.getArgument().accept(this, null);
    if (myNormalizing) {
      type = type.normalize(NormalizationMode.WHNF);
    }
    if (type instanceof ClassCallExpression classCall && classCall.getDefinition().isSubClassOf(expr.getDefinition().getParentClass())) {
      if (expr.getDefinition().isInfiniteField()) {
        Expression normExpr = NormalizeVisitor.INSTANCE.evalFieldCall(expr.getDefinition(), expr.getArgument());
        if (normExpr != null) {
          return normExpr.accept(this, null);
        }
      }
      return getFieldCallType(expr.getDefinition(), classCall, expr.getArgument());
    }
    return new ErrorExpression();
  }

  @Override
  public DataCallExpression visitConCall(ConCallExpression expr, Void params) {
    if (expr.getDefinition() == Prelude.SUC) {
      int sucs = 1;
      Expression expression = expr.getDefCallArguments().getFirst();
      while (expression instanceof ConCallExpression && ((ConCallExpression) expression).getDefinition() == Prelude.SUC) {
        sucs++;
        expression = ((ConCallExpression) expression).getDefCallArguments().getFirst();
      }
      Expression argType = expression.accept(this, null);
      if (myNormalizing) argType = argType.normalize(NormalizationMode.WHNF);
      DataCallExpression dataCall = argType.cast(DataCallExpression.class);
      if (dataCall != null && dataCall.getDefinition() == Prelude.FIN) {
        Expression arg = dataCall.getDefCallArguments().getFirst();
        for (int i = 0; i < sucs; i++) {
          arg = Suc(arg);
        }
        return DataCallExpression.make(dataCall.getDefinition(), dataCall.getLevels(), new SingletonList<>(arg));
      }
      return Nat();
    }
    return expr.getDefinition().getDataTypeExpression(expr.getLevels(), expr.getDataTypeArguments());
  }

  @Override
  public Expression visitClassCall(ClassCallExpression expr, Void params) {
    SortExpression sort = expr.getDefinition().computeSort(expr.getImplementedHere(), expr.getThisBinding(), expr.getLevels(), expr.getLevelSubstitution(), false, this);
    return sort == null ? new ErrorExpression() : new UniverseExpression(sort);
  }

  @Override
  public Expression visitReference(ReferenceExpression expr, Void params) {
    return expr.getBinding().getType();
  }

  @Override
  public Expression visitInferenceReference(InferenceReferenceExpression expr, Void params) {
    return expr.getSubstExpression() != null ? expr.getSubstExpression().accept(this, null) : expr.getVariable().getType();
  }

  @Override
  public Expression visitSubst(SubstExpression expr, Void params) {
    return expr.getExpression().accept(this, null).subst(expr.getSubstitution(), expr.getLevelSubstitution());
  }

  @Override
  public Expression visitLam(LamExpression expr, Void ignored) {
    return new PiExpression(expr.getParameters(), expr.getBody().accept(this, null));
  }

  @Override
  public Expression visitPi(PiExpression expr, Void params) {
    SortExpression sort1 = expr.getParameters().getType().accept(this, null).toSortExpression();
    SortExpression sort2 = expr.getCodomain().accept(this, null).toSortExpression();
    return sort1 == null || sort2 == null ? new ErrorExpression() : new UniverseExpression(SortExpression.makePi(sort1, sort2));
  }

  @Override
  public Expression visitSigma(SigmaExpression expr, Void params) {
    List<SortExpression> sorts = new ArrayList<>();
    for (DependentLink param = expr.getParameters(); param.hasNext(); param = param.getNext()) {
      param = param.getNextTyped(null);
      Expression type = param.getType().accept(this, null);
      SortExpression sort = type.toSortExpression();
      if (sort == null) {
        return new ErrorExpression();
      }
      sorts.add(sort);
    }
    return new UniverseExpression(SortExpression.makeMax(sorts));
  }

  @Override
  public Expression visitUniverse(UniverseExpression expr, Void params) {
    return new UniverseExpression(SortExpression.makeSucc(expr.getSortExpression()));
  }

  @Override
  public Expression visitError(ErrorExpression expr, Void params) {
    return expr.getExpression() == null ? expr : expr.replaceExpression(expr.getExpression().accept(this, null));
  }

  @Override
  public Expression visitTuple(TupleExpression expr, Void params) {
    return expr.getSigmaType();
  }

  @Override
  public Expression visitProj(ProjExpression expr, Void ignored) {
    Expression type = expr.getExpression().accept(this, null);
    if (myNormalizing) {
      type = type.normalize(NormalizationMode.WHNF);
    } else {
      type = type.getUnderlyingExpression();
    }
    if (!(type instanceof SigmaExpression)) {
      return type instanceof ErrorExpression ? type : new ErrorExpression();
    }

    DependentLink params = ((SigmaExpression) type).getParameters();
    if (expr.getField() == 0) {
      return params.getType();
    }

    ExprSubstitution subst = new ExprSubstitution();
    for (int i = 0; i < expr.getField(); i++) {
      subst.add(params, ProjExpression.make(expr.getExpression(), i, params.isProperty()));
      params = params.getNext();
    }
    return params.getType().subst(subst);
  }

  @Override
  public ClassCallExpression visitNew(NewExpression expr, Void params) {
    return expr.getType();
  }

  @Override
  public Expression visitLet(LetExpression expr, Void params) {
    List<HaveClause> clauses = new ArrayList<>(expr.getClauses().size());
    for (HaveClause clause : expr.getClauses()) {
      if (!(clause instanceof LetClause)) {
        clauses.add(clause);
      }
    }
    Expression result = expr.getExpression().accept(this, null);
    return clauses.isEmpty() ? result : new LetExpression(expr.isStrict(), clauses, result);
  }

  @Override
  public Expression visitCase(CaseExpression expr, Void params) {
    return expr.getResultType().subst(DependentLink.Helper.toSubstitution(expr.getParameters(), expr.getArguments()));
  }

  @Override
  public Expression visitOfType(OfTypeExpression expr, Void params) {
    return expr.getTypeOf();
  }

  @Override
  public Expression visitInteger(IntegerExpression expr, Void params) {
    return Fin(expr.suc());
  }

  @Override
  public Expression visitString(StringExpression expr, Void params) {
    return String();
  }

  @Override
  public Expression visitTypeConstructor(TypeConstructorExpression expr, Void params) {
    return expr.getType();
  }

  @Override
  public Expression visitTypeDestructor(TypeDestructorExpression expr, Void params) {
    Expression type = expr.getArgument().accept(this, null);
    if (myNormalizing) {
      type = type.normalize(NormalizationMode.WHNF);
    } else {
      type = type.getUnderlyingExpression();
    }
    if (!(type instanceof FunCallExpression funCall && funCall.getDefinition() == expr.getDefinition())) {
      return type instanceof ErrorExpression ? type : new ErrorExpression();
    }

    return NormalizeVisitor.INSTANCE.visitBody(funCall.getDefinition().getActualBody(), funCall.getDefCallArguments(), funCall, NormalizationMode.WHNF);
  }

  @Override
  public Expression visitArray(ArrayExpression expr, Void params) {
    Map<ClassField, Expression> implementations = new LinkedHashMap<>();
    if (expr.getTail() == null) {
      implementations.put(Prelude.ARRAY_LENGTH, new SmallIntegerExpression(expr.getElements().size()));
    } else {
      Expression tailType = expr.getTail().accept(this, null).getUnderlyingExpression();
      Expression length = null;
      if (tailType instanceof ClassCallExpression && ((ClassCallExpression) tailType).getDefinition() == Prelude.DEP_ARRAY) {
        length = ((ClassCallExpression) tailType).getImplementationHere(Prelude.ARRAY_LENGTH, expr.getTail());
      }
      if (length == null) {
        length = FieldCallExpression.make(Prelude.ARRAY_LENGTH, expr.getTail());
      }
      length = length.getUnderlyingExpression();
      if (length instanceof IntegerExpression) {
        length = ((IntegerExpression) length).plus(expr.getElements().size());
      } else {
        for (Expression ignored : expr.getElements()) {
          length = Suc(length);
        }
      }
      implementations.put(Prelude.ARRAY_LENGTH, length);
    }
    implementations.put(Prelude.ARRAY_ELEMENTS_TYPE, expr.getElementsType());
    return new ClassCallExpression(Prelude.DEP_ARRAY, Levels.EMPTY, implementations);
  }

  @Override
  public Expression visitPath(PathExpression expr, Void params) {
    Expression left = AppExpression.make(expr.getArgument(), ExpressionFactory.Left(), true);
    Expression right = AppExpression.make(expr.getArgument(), ExpressionFactory.Right(), true);
    return DataCallExpression.make(Prelude.PATH, Levels.EMPTY, Arrays.asList(expr.getArgumentType(), left, right));
  }

  @Override
  public Expression visitAt(AtExpression expr, Void params) {
    Expression type = expr.getPathArgument().accept(this, null);
    type = myNormalizing ? type.normalize(NormalizationMode.WHNF) : type.getUnderlyingExpression();
    if (!(type instanceof DataCallExpression && ((DataCallExpression) type).getDefinition() == Prelude.PATH)) {
      return type instanceof ErrorExpression ? type : new ErrorExpression();
    }
    return AppExpression.make(((DataCallExpression) type).getDefCallArguments().getFirst(), expr.getIntervalArgument(), true);
  }

  @Override
  public Expression visitPEval(PEvalExpression expr, Void params) {
    Expression normExpr = expr.eval();
    if (normExpr == null) {
      return new ErrorExpression();
    }

    List<Expression> args = new ArrayList<>(3);
    args.add(expr.getExpression().accept(this, null));
    args.add(expr.getExpression());
    args.add(normExpr);
    return FunCallExpression.make(Prelude.PATH_INFIX, Levels.EMPTY, args);
  }

  @Override
  public Expression visitBox(BoxExpression expr, Void params) {
    return expr.getType();
  }
}
