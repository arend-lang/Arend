package com.jetbrains.jetpad.vclang.term.expr.visitor;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.term.context.binding.Binding;
import com.jetbrains.jetpad.vclang.term.context.binding.TypedBinding;
import com.jetbrains.jetpad.vclang.term.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.term.definition.ClassField;
import com.jetbrains.jetpad.vclang.term.definition.DataDefinition;
import com.jetbrains.jetpad.vclang.term.definition.Function;
import com.jetbrains.jetpad.vclang.term.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.term.expr.*;
import com.jetbrains.jetpad.vclang.term.pattern.elimtree.ElimTreeNode;
import com.jetbrains.jetpad.vclang.term.pattern.elimtree.LeafElimTreeNode;

import java.util.*;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;

public class NormalizeVisitor extends BaseExpressionVisitor<NormalizeVisitor.Mode, Expression>  {
  public enum Mode { WHNF, NF, NFH, TOP }

  private Expression visitApps(Expression expr, List<ArgumentExpression> exprs, Mode mode) {
    if (expr instanceof LamExpression) {
      int i = 0;
      DependentLink link = ((LamExpression) expr).getParameters();
      Substitution subst = new Substitution();
      while (link.hasNext() && i < exprs.size()) {
        subst.addMapping(link, exprs.get(i++).getExpression());
        link = link.getNext();
      }
      expr = ((LamExpression) expr).getBody();
      if (link.hasNext()) {
        expr = Lam(link, expr);
      }
      expr = expr.subst(subst);
      for (ArgumentExpression argumentExpression : exprs.subList(i, exprs.size())) {
        expr = Apps(expr, argumentExpression);
      }
      return mode == Mode.TOP ? expr : expr.accept(this, mode);
    }

    if (expr instanceof DefCallExpression) {
      return visitDefCall((DefCallExpression) expr, exprs, mode);
    } else
    if (expr instanceof ReferenceExpression) {
      Binding binding = ((ReferenceExpression) expr).getBinding();
      if (binding instanceof Function) {
        return visitFunctionCall((Function) binding, expr, exprs, mode);
      }
    }

    if (mode == Mode.TOP) return null;
    Expression newExpr = expr.accept(this, Mode.TOP);
    if (newExpr != null) {
      newExpr = newExpr.getFunctionArgs(exprs);
      Collections.reverse(exprs);
      return visitApps(newExpr, exprs, mode);
    }

    for (int i = exprs.size() - 1; i >= 0; --i) {
      if (mode == Mode.NF || mode == Mode.NFH) {
        expr = Apps(expr, new ArgumentExpression(exprs.get(i).getExpression().accept(this, mode), exprs.get(i).isExplicit(), exprs.get(i).isHidden()));
      } else {
        expr = Apps(expr, exprs.get(i));
      }
    }
    return expr;
  }

  @Override
  public Expression visitApp(AppExpression expr, Mode mode) {
    List<ArgumentExpression> exprs = new ArrayList<>();
    Expression expr1 = expr.getFunctionArgs(exprs);
    Collections.reverse(exprs);
    return visitApps(expr1, exprs, mode);
  }

  public Expression applyDefCall(Expression defCallExpr, List<ArgumentExpression> args, Mode mode) {
    if (mode == Mode.TOP) return null;

    Expression expr = defCallExpr;
    for (ArgumentExpression arg : args) {
      if (mode == Mode.NF || mode == Mode.NFH) {
        expr = Apps(expr, new ArgumentExpression(arg.getExpression().accept(this, mode), arg.isExplicit(), arg.isHidden()));
      } else {
        expr = Apps(expr, arg);
      }
    }
    return expr;
  }

  public Expression visitDefCall(DefCallExpression defCallExpr, List<ArgumentExpression> args, Mode mode) {
    if (defCallExpr.getDefinition().hasErrors()) {
      return mode == Mode.TOP ? null : applyDefCall(defCallExpr, args, mode);
    }

    if (defCallExpr.getDefinition() instanceof ClassField) {
      if (args.isEmpty()) {
        if (mode == Mode.TOP) {
          return null;
        }
        return FieldCall((ClassField) defCallExpr.getDefinition());
      }

      ArgumentExpression thisArg = args.get(0);
      Expression thisType = thisArg.getExpression().getType();
      if (thisType == null) {
        assert false;
      } else {
        thisType = thisType.normalize(Mode.WHNF);
        if (thisType instanceof ClassCallExpression) {
          ClassCallExpression.ImplementStatement elem = ((ClassCallExpression) thisType).getImplementStatements().get(defCallExpr.getDefinition());
          if (elem != null && elem.term != null) {
            args = args.subList(1, args.size());
            if (mode == Mode.TOP) {
              return Apps(elem.term, args.toArray(new ArgumentExpression[args.size()]));
            } else {
              return visitApps(elem.term, args, mode);
            }
          }
        }
      }
    }

    if (defCallExpr.getDefinition() instanceof Function) {
      return visitFunctionCall((Function) defCallExpr.getDefinition(), defCallExpr, args, mode);
    } else if (defCallExpr instanceof ConCallExpression) {
      return visitConstructorCall((ConCallExpression) defCallExpr, args, mode);
    }

    if (mode == Mode.TOP) return null;

    if (defCallExpr instanceof ClassCallExpression || defCallExpr instanceof FieldCallExpression) {
      return applyDefCall(defCallExpr, args, mode);
    }

    if (!(defCallExpr instanceof DataCallExpression)) {
      throw new IllegalStateException();
    }

    DataDefinition dataDefinition = ((DataCallExpression) defCallExpr).getDefinition();
    DependentLink parameters = dataDefinition.getParameters();

    Expression result = defCallExpr;
    for (ArgumentExpression arg : args) {
      if (mode == Mode.NF || mode == Mode.NFH) {
        arg = new ArgumentExpression(arg.getExpression().accept(this, mode), arg.isExplicit(), arg.isHidden());
      }
      result = Apps(result, arg);
      parameters = parameters.getNext();
    }

    if (parameters.hasNext()) {
      parameters = parameters.subst(new Substitution());
      for (DependentLink link = parameters; link.hasNext(); link = link.getNext()) {
        result = Apps(result, Reference(link));
      }
      return Lam(parameters, result);
    } else {
      return result;
    }
  }

  private Expression visitConstructorCall(ConCallExpression conCallExpression, List<ArgumentExpression> args, Mode mode) {
    int take = DependentLink.Helper.size(conCallExpression.getDefinition().getDataTypeParameters()) - conCallExpression.getDataTypeArguments().size();
    if (take > 0) {
      if (take >= args.size()) {
        take = args.size();
      }
      List<Expression> parameters = new ArrayList<>(conCallExpression.getDataTypeArguments().size() + take);
      parameters.addAll(conCallExpression.getDataTypeArguments());
      for (int i = 0; i < take; i++) {
        parameters.add(args.get(i).getExpression());
      }
      conCallExpression = ConCall(conCallExpression.getDefinition(), parameters);
      args = args.subList(take, args.size());
    }
    if (args.size() == 0) {
      return applyDefCall(conCallExpression, args, mode);
    }

    DependentLink excessiveParams = conCallExpression.getDefinition().getParameters();
    int i = 0;
    for (; i < args.size(); i++) {
      if (!excessiveParams.hasNext()) {
        break;
      }
      excessiveParams = excessiveParams.getNext();
    }

    if (mode == Mode.WHNF && excessiveParams.hasNext()) {
      return applyDefCall(conCallExpression, args, mode);
    }

    excessiveParams = excessiveParams.subst(new Substitution());
    Substitution args2subst = completeArgs(args, conCallExpression.getDefinition().getParameters(), excessiveParams);
    DependentLink link = conCallExpression.getDefinition().getDataTypeParameters();
    for (Expression argument : conCallExpression.getDataTypeArguments()) {
      args2subst.addMapping(link, argument);
      link = link.getNext();
    }

    if (conCallExpression.getDefinition().getDataType().getCondition(conCallExpression.getDefinition()) == null) {
      return applyDefCall(conCallExpression, args, mode);
    }

    ElimTreeNode node = conCallExpression.getDefinition().getDataType().getCondition(conCallExpression.getDefinition()).getElimTree().matchUntilStuck(args2subst);
    if (!(node instanceof LeafElimTreeNode)) {
      return applyDefCall(conCallExpression, args, mode);
    }
    Expression result = ((LeafElimTreeNode) node).getExpression().subst(args2subst);
    result = excessiveParams.hasNext() ? Lam(excessiveParams, result) : result;
    return mode == Mode.TOP ? result : result.accept(this, mode);
  }

  private Expression visitFunctionCall(Function func, Expression defCallExpr, List<ArgumentExpression> args, Mode mode) {
    if (func instanceof FunctionDefinition && func.equals(Prelude.COERCE) && args.size() == 3) {
      Binding binding = new TypedBinding("i", DataCall(Prelude.INTERVAL));
      Expression expr = Apps(args.get(0).getExpression(), Reference(binding)).accept(this, NormalizeVisitor.Mode.NF);
      if (!expr.findBinding(binding)) {
        return mode == Mode.TOP ? args.get(1).getExpression() : args.get(1).getExpression().accept(this, mode);
      }
      List<Expression> mbIsoArgs = new ArrayList<>();
      Expression mbIso = expr.getFunction(mbIsoArgs);
      if (mbIso instanceof FunCallExpression && Prelude.isIso(((FunCallExpression) mbIso).getDefinition()) && mbIsoArgs.size() == 7) {
        boolean noFreeVar = true;
        for (int i = 1; i < mbIsoArgs.size(); i++) {
          if (mbIsoArgs.get(i).findBinding(binding)) {
            noFreeVar = false;
            break;
          }
        }
        if (noFreeVar) {
          Expression normedPt = args.get(2).getExpression().accept(this, Mode.NF);
          if (normedPt instanceof ConCallExpression && ((ConCallExpression) normedPt).getDefinition() == Prelude.RIGHT) {
            Expression result = Apps(mbIsoArgs.get(4), args.get(1));
            return mode == Mode.TOP ? result : result.accept(this, mode);
          }
        }
      }
    }

    DependentLink excessiveParams = func.getParameters();
    int i = 0;
    for (; i < args.size(); i++) {
      if (!excessiveParams.hasNext()) {
        break;
      }
      excessiveParams = excessiveParams.getNext();
    }

    if (mode == Mode.WHNF && excessiveParams.hasNext() || func.getElimTree() == null) {
      return applyDefCall(defCallExpr, args, mode);
    }

    excessiveParams = excessiveParams.subst(new Substitution());
    Substitution args2subst = completeArgs(args, func.getParameters(), excessiveParams);

    ElimTreeNode node = func.getElimTree().matchUntilStuck(args2subst);
    if (!(node instanceof LeafElimTreeNode)) {
      return applyDefCall(defCallExpr, args, mode);
    }
    LeafElimTreeNode leaf = (LeafElimTreeNode) node;
    Expression result = leaf.getExpression().subst(args2subst);
    if ((mode == Mode.NFH || mode == Mode.TOP) && leaf.getArrow() == Abstract.Definition.Arrow.LEFT) {
      result = result.accept(this, Mode.TOP);
      if (result == null) {
        return applyDefCall(defCallExpr, args, mode);
      }
    }

    for (ArgumentExpression arg : args.subList(i, args.size())) {
      result = Apps(result, arg);
    }
    result = excessiveParams.hasNext() ? Lam(excessiveParams, result) : result;
    return mode == Mode.TOP ? result : result.accept(this, mode);
  }

  private Substitution completeArgs(List<ArgumentExpression> args, DependentLink params, DependentLink excessiveParams) {
    Substitution result = new Substitution();
    for (ArgumentExpression arg : args) {
      if (!params.hasNext()) {
        break;
      }
      result.addMapping(params, arg.getExpression());
      params = params.getNext();
    }
    for (; excessiveParams.hasNext(); excessiveParams = excessiveParams.getNext()) {
      result.addMapping(excessiveParams, Reference(excessiveParams));
    }
    return result;
  }

  @Override
  public Expression visitDefCall(DefCallExpression expr, Mode mode) {
    return visitDefCall(expr, Collections.<ArgumentExpression>emptyList(), mode);
  }

  @Override
  public Expression visitClassCall(ClassCallExpression expr, Mode mode) {
    if (mode == Mode.TOP) return null;
    if (mode == Mode.WHNF) return expr;

    Map<ClassField, ClassCallExpression.ImplementStatement> statements = new HashMap<>();
    for (Map.Entry<ClassField, ClassCallExpression.ImplementStatement> elem : expr.getImplementStatements().entrySet()) {
      statements.put(elem.getKey(), new ClassCallExpression.ImplementStatement(elem.getValue().type == null ? null : elem.getValue().type.accept(this, mode), elem.getValue().term == null ? null : elem.getValue().term.accept(this, mode)));
    }

    return ClassCall(expr.getDefinition(), statements);
  }

  @Override
  public Expression visitReference(ReferenceExpression expr, Mode mode) {
    if (mode == Mode.TOP) {
      return null;
    }
    Binding binding = expr.getBinding();
    if (binding instanceof Function) {
      return visitFunctionCall((Function) binding, expr, new ArrayList<ArgumentExpression>(), mode);
    } else {
      return expr;
    }
  }

  @Override
  public Expression visitLam(LamExpression expr, Mode mode) {
    if (mode == Mode.TOP) {
      return null;
    }
    if (mode == Mode.NFH) {
      Substitution substitution = new Substitution();
      return Lam(visitParameters(expr.getParameters(), substitution, mode), expr.getBody().subst(substitution).accept(this, mode));
    }
    if (mode == Mode.NF) {
      return Lam(expr.getParameters(), expr.getBody().accept(this, mode));
    } else {
      return expr;
    }
  }

  @Override
  public Expression visitPi(PiExpression expr, Mode mode) {
    if (mode == Mode.TOP) {
      return null;
    }
    if (mode == Mode.NFH) {
      Substitution substitution = new Substitution();
      return Pi(visitParameters(expr.getParameters(), substitution, mode), expr.getCodomain().subst(substitution).accept(this, mode));
    } else {
      return expr;
    }
  }

  private DependentLink visitParameters(DependentLink link, Substitution substitution, Mode mode) {
    link = link.subst(substitution);
    for (DependentLink link1 = link; link1.hasNext(); link1 = link1.getNext()) {
      link1 = link1.getNextTyped(null);
      link1.setType(link1.getType().accept(this, mode));
    }
    return link;
  }

  @Override
  public Expression visitUniverse(UniverseExpression expr, Mode mode) {
    return mode == Mode.TOP ? null : expr;
  }

  @Override
  public Expression visitError(ErrorExpression expr, Mode mode) {
    return mode == Mode.TOP ? null : mode != Mode.NF && mode != Mode.NFH || expr.getExpr() == null ? expr : new ErrorExpression(expr.getExpr().accept(this, mode), expr.getError());
  }

  @Override
  public Expression visitInferHole(InferHoleExpression expr, Mode mode) {
    return mode == Mode.TOP ? null : expr;
  }

  @Override
  public Expression visitTuple(TupleExpression expr, Mode mode) {
    if (mode == Mode.TOP) return null;
    if (mode != Mode.NF && mode != Mode.NFH) return expr;
    List<Expression> fields = new ArrayList<>(expr.getFields().size());
    for (Expression field : expr.getFields()) {
      fields.add(field.accept(this, mode));
    }
    return Tuple(fields, expr.getType());
  }

  @Override
  public Expression visitSigma(SigmaExpression expr, Mode mode) {
    return mode == Mode.TOP ? null : mode == Mode.NF || mode == Mode.NFH ? Sigma(visitParameters(expr.getParameters(), new Substitution(), mode)) : expr;
  }

  @Override
  public Expression visitProj(ProjExpression expr, Mode mode) {
    Expression exprNorm = expr.getExpression().normalize(Mode.WHNF);
    if (exprNorm instanceof TupleExpression) {
      Expression result = ((TupleExpression) exprNorm).getFields().get(expr.getField());
      return mode == Mode.TOP ? result : result.accept(this, mode);
    } else {
      return mode == Mode.TOP ? null : mode == Mode.NF || mode == Mode.NFH ? Proj(expr.getExpression().accept(this, mode), expr.getField()) : expr;
    }
  }

  @Override
  public Expression visitNew(NewExpression expr, Mode mode) {
    return mode == Mode.TOP ? null : mode == Mode.WHNF ? expr : New(expr.getExpression().accept(this, mode));
  }

  @Override
  public Expression visitLet(LetExpression letExpression, Mode mode) {
    Expression term = letExpression.getExpression().accept(this, mode);
    Set<Binding> bindings = new HashSet<>();
    for (LetClause clause : letExpression.getClauses()) {
      bindings.add(clause);
    }
    return term.findBinding(bindings) ? Let(letExpression.getClauses(), term) : term;
  }
}
