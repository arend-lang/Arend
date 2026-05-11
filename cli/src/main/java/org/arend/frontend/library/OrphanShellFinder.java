package org.arend.frontend.library;

import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.ClassDefinition;
import org.arend.core.definition.ClassField;
import org.arend.core.definition.Constructor;
import org.arend.core.definition.DataDefinition;
import org.arend.core.definition.Definition;
import org.arend.core.definition.FunctionDefinition;
import org.arend.core.expr.AbsExpression;
import org.arend.core.expr.ClassCallExpression;
import org.arend.core.expr.ConCallExpression;
import org.arend.core.expr.DataCallExpression;
import org.arend.core.expr.Expression;
import org.arend.core.expr.FieldCallExpression;
import org.arend.core.expr.FunCallExpression;
import org.arend.core.expr.visitor.VoidExpressionVisitor;

import java.util.Map;

/**
 * Walks a {@link Definition}'s parameters, result type, and body looking for any
 * referenced {@link Definition} whose {@code status() == NEEDS_TYPE_CHECKING}.
 * Such a referenced definition is an orphan shell — a placeholder created in
 * cache-load phase 2a that never got its result type, parameters, or body filled
 * in because its owning module's phase 2b threw partway through. Holding such a
 * shell makes later normalization NPE in {@code GetTypeVisitor.visitFunCall} when
 * {@code FunctionDefinition.getTypeWithParams} short-circuits to null.
 *
 * Stops early once {@link #found} flips to true.
 */
final class OrphanShellFinder extends VoidExpressionVisitor<Void> {
  boolean found = false;

  void scan(Definition def) {
    if (found) return;
    if (def instanceof FunctionDefinition fn) {
      scanParameters(fn.getParameters());
      if (found) return;
      if (fn.getResultType() != null) fn.getResultType().accept(this, null);
      if (found) return;
      if (fn.getResultTypeLevel() != null) fn.getResultTypeLevel().accept(this, null);
      if (found) return;
      if (fn.getReallyActualBody() instanceof Expression bodyExpr) {
        bodyExpr.accept(this, null);
      } else if (fn.getReallyActualBody() != null) {
        // ElimBody / IntervalElim are handled by visitBody on the base class,
        // but we want a single entry point that also covers plain Expression
        // bodies.  Re-dispatch through the base helper:
        super.visitBody(fn.getReallyActualBody(), null);
      }
    } else if (def instanceof DataDefinition data) {
      scanParameters(data.getParameters());
      for (Constructor c : data.getConstructors()) {
        if (found) return;
        scanParameters(c.getParameters());
        if (found) return;
        if (c.getBody() != null) super.visitBody(c.getBody(), null);
      }
    } else if (def instanceof ClassDefinition cls) {
      for (ClassField field : cls.getPersonalFields()) {
        if (found) return;
        if (field.getType() != null) field.getType().accept(this, null);
      }
      for (Map.Entry<ClassField, AbsExpression> entry : cls.getImplemented()) {
        if (found) return;
        entry.getValue().getExpression().accept(this, null);
      }
    }
  }

  private void scanParameters(DependentLink link) {
    while (link.hasNext() && !found) {
      Expression t = link.getTypeExpr();
      if (t != null) t.accept(this, null);
      link = link.getNext();
    }
  }

  private void check(Definition def) {
    if (def == null) return;
    if (def.status() == Definition.TypeCheckingStatus.NEEDS_TYPE_CHECKING) {
      found = true;
      return;
    }
    // The referenced definition may be filled (status NO_ERRORS/HAS_*) but still
    // dangling — its TCDefReferable was cleared because some other definition in
    // the same module failed phase 2b, so source re-typechecking will produce a
    // fresh, structurally-equal-but-non-identical Definition object.  Any module
    // that captured the original would diverge in object-identity comparisons
    // against the fresh one.  Treat that as an orphan too.
    Definition live = def.getRef() == null ? null : def.getRef().getTypechecked();
    if (live != def) {
      found = true;
    }
  }

  @Override
  public Void visitFunCall(FunCallExpression expr, Void params) {
    check(expr.getDefinition());
    return found ? null : super.visitFunCall(expr, params);
  }

  @Override
  public Void visitDataCall(DataCallExpression expr, Void params) {
    check(expr.getDefinition());
    return found ? null : super.visitDataCall(expr, params);
  }

  @Override
  public Void visitClassCall(ClassCallExpression expr, Void params) {
    check(expr.getDefinition());
    return found ? null : super.visitClassCall(expr, params);
  }

  @Override
  public Void visitFieldCall(FieldCallExpression expr, Void params) {
    // ClassField is not itself a Definition with status, but its parent class is.
    ClassField field = expr.getDefinition();
    if (field != null) check(field.getParentClass());
    return found ? null : super.visitFieldCall(expr, params);
  }

  @Override
  public Void visitConCall(ConCallExpression expr, Void params) {
    Constructor c = expr.getDefinition();
    if (c != null) check(c.getDataType());
    return found ? null : super.visitConCall(expr, params);
  }
}
