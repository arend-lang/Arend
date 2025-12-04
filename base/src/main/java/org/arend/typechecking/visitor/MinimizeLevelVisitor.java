package org.arend.typechecking.visitor;

import org.arend.core.context.LinkList;
import org.arend.core.context.param.*;
import org.arend.core.expr.*;
import org.arend.core.expr.visitor.BaseExpressionVisitor;
import org.arend.core.subst.ExprSubstitution;

public class MinimizeLevelVisitor extends BaseExpressionVisitor<Void, Expression> {
  @Override
  public Expression visitApp(AppExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitDefCall(DefCallExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitReference(ReferenceExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitInferenceReference(InferenceReferenceExpression expr, Void params) {
    return expr.getSubstExpression() == null ? null : expr.getSubstExpression().accept(this, null);
  }

  @Override
  public Expression visitSubst(SubstExpression expr, Void params) {
    return expr.getSubstExpression().accept(this, null);
  }

  @Override
  public Expression visitLam(LamExpression expr, Void params) {
    return null;
  }

  private Expression visit(Expression expr) {
    Expression type = expr.accept(this, null);
    return type != null ? type : expr;
  }

  @Override
  public Expression visitPi(PiExpression expr, Void params) {
    Expression dom = visit(expr.getParameters().getTypeExpr());
    Expression cod = visit(expr.getCodomain());

    ExprSubstitution substitution = new ExprSubstitution();
    LinkList list = new LinkList();
    for (SingleDependentLink param = expr.getParameters(); param.hasNext(); param = param.getNext()) {
      SingleDependentLink newParam = param instanceof TypedSingleDependentLink ? new TypedSingleDependentLink(param.isExplicit(), param.getName(), dom, param.isHidden()) : new UntypedSingleDependentLink(param.getName());
      list.append(newParam);
      substitution.add(param, new ReferenceExpression(newParam));
    }
    return new PiExpression((SingleDependentLink) list.getFirst(), cod.subst(substitution));
  }

  @Override
  public Expression visitSigma(SigmaExpression expr, Void params) {
    LinkList list = new LinkList();
    ExprSubstitution substitution = new ExprSubstitution();
    for (DependentLink param = expr.getParameters(); param.hasNext(); param = param.getNext()) {
      DependentLink newParam;
      if (param instanceof TypedDependentLink) {
        Expression type = visit(param.getTypeExpr());
        newParam = new TypedDependentLink(param.isExplicit(), param.getName(), type.subst(substitution), param.isHidden(), EmptyDependentLink.getInstance());
      } else {
        newParam = new UntypedDependentLink(param.getName());
      }
      list.append(newParam);
      substitution.add(param, new ReferenceExpression(newParam));
    }
    return new SigmaExpression(list.getFirst());
  }

  @Override
  public Expression visitUniverse(UniverseExpression expr, Void params) {
    return expr;
  }

  @Override
  public Expression visitError(ErrorExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitTuple(TupleExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitProj(ProjExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitNew(NewExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitPEval(PEvalExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitBox(BoxExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitLet(LetExpression expr, Void params) {
    return expr.getResult().accept(this, null);
  }

  @Override
  public Expression visitCase(CaseExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitOfType(OfTypeExpression expr, Void params) {
    return expr.getExpression().accept(this, null);
  }

  @Override
  public Expression visitInteger(IntegerExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitString(StringExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitTypeConstructor(TypeConstructorExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitTypeDestructor(TypeDestructorExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitArray(ArrayExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitPath(PathExpression expr, Void params) {
    return null;
  }

  @Override
  public Expression visitAt(AtExpression expr, Void params) {
    return null;
  }
}
