package org.arend.naming.resolving.typing;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.reference.Precedence;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.*;
import org.arend.term.Fixity;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ConcreteResolvableDefinitionVisitor;
import org.arend.term.concrete.DefinableMetaDefinition;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;

import java.util.ArrayList;
import java.util.List;

public class TypingInfoVisitor implements ConcreteResolvableDefinitionVisitor<Scope, GlobalTypingInfo> {
  private final GlobalTypingInfo.Builder myBuilder = new GlobalTypingInfo.Builder();

  private void processGroup(ConcreteGroup group, Scope scope) {
    Scope cachedScope = CachingScope.make(LexicalScope.insideOf(group, scope, false));
    if (group.definition() != null) {
      group.definition().accept(this, cachedScope);
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) {
        processGroup(statement.group(), cachedScope);
      }
    }
    if (!group.dynamicGroups().isEmpty()) {
      Scope dynamicScope = CachingScope.make(LexicalScope.insideOf(group, scope, true));
      for (ConcreteGroup subgroup : group.dynamicGroups()) {
        processGroup(subgroup, dynamicScope);
      }
    }
  }

  public GlobalTypingInfo processGroup(ConcreteGroup group, Scope scope, TypingInfo parent) {
    processGroup(group, scope);
    return myBuilder.build(parent);
  }

  public static Referable tryResolve(Referable ref, Scope scope) {
    while (ref instanceof RedirectingReferable) {
      ref = ((RedirectingReferable) ref).getOriginalReferable();
    }
    return ref instanceof UnresolvedReference unresolved ? unresolved.copy().tryResolve(scope, null, ResolverListener.EMPTY) : ref;
  }

  private void resolveTypeClassReference(Referable referable, List<? extends Concrete.Parameter> parameters, Concrete.Expression expr, Scope scope, boolean isType) {
    if (expr == null) return;
    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(scope, new ArrayList<>(), TypingInfo.EMPTY, DummyErrorReporter.INSTANCE, ResolverListener.EMPTY);
    exprVisitor.updateScope(parameters);

    int paramsNumber = 0;
    for (Concrete.Parameter parameter : parameters) {
      if (parameter.isExplicit()) {
        paramsNumber += parameter.getNumberOfParameters();
      }
    }

    if (isType) {
      while (expr instanceof Concrete.PiExpression piExpr) {
        exprVisitor.updateScope(piExpr.getParameters());
        for (Concrete.TypeParameter parameter : piExpr.getParameters()) {
          if (parameter.isExplicit()) {
            paramsNumber += parameter.getNumberOfParameters();
          }
        }
        expr = piExpr.getCodomain();
      }
    } else {
      while (expr instanceof Concrete.LamExpression lamExpr) {
        exprVisitor.updateScope(lamExpr.getParameters());
        for (Concrete.Parameter parameter : lamExpr.getParameters()) {
          if (parameter.isExplicit()) {
            paramsNumber += parameter.getNumberOfParameters();
          }
        }
        expr = lamExpr.getBody();
      }
    }

    int arguments = 0;
    Referable found = null;
    while (true) {
      if (expr instanceof Concrete.AppExpression appExpr) {
        expr = appExpr.getFunction();
        arguments += appExpr.getNumberOfExplicitArguments();
      } else if (expr instanceof Concrete.ClassExtExpression) {
        expr = ((Concrete.ClassExtExpression) expr).getBaseClassExpression();
        arguments = 0;
      } else if (expr instanceof Concrete.BinOpSequenceExpression binOpExpr) {
        var sequence = binOpExpr.getSequence();
        if (sequence.isEmpty()) return;
        if (sequence.size() == 1) {
          expr = sequence.get(0).getComponent();
          continue;
        }
        Referable first = null;
        Referable best = null;
        Precedence bestPrec = Precedence.DEFAULT;
        int bestIndex = 0;
        for (int i = 0; i < sequence.size(); i++) {
          var elem = sequence.get(i);
          if (elem.fixity == Fixity.POSTFIX) return; // TODO: Just ignore postfix operators for now.
          if (elem.isExplicit && elem.getComponent() instanceof Concrete.ReferenceExpression refExpr) {
            Referable ref = tryResolve(refExpr.getReferent(), exprVisitor.getScope());
            if (ref != null && i == 0) first = ref;
            Precedence prec = ref instanceof GlobalReferable globalRef ? globalRef.getPrecedence() : Precedence.DEFAULT;
            if (prec.isInfix || elem.fixity == Fixity.INFIX) {
              Precedence.ComparisonResult cmp = best == null ? Precedence.ComparisonResult.LESS : prec.compare(bestPrec);
              if (cmp == Precedence.ComparisonResult.UNCOMPARABLE) return;
              if (best == null || cmp == Precedence.ComparisonResult.LESS) {
                best = ref;
                bestIndex = i;
                bestPrec = prec;
              }
            }
          }
        }

        if (best == null) {
          arguments += sequence.size() - 1;
          if (!(sequence.get(0).getComponent() instanceof Concrete.ReferenceExpression)) {
            expr = sequence.get(0).getComponent();
            continue;
          }
          found = first;
        } else {
          found = best;
          arguments += bestIndex == 0 || bestIndex == sequence.size() - 1 ? 1 : 2;
        }
        break;
      } else if (expr instanceof Concrete.ReferenceExpression refExpr) {
        found = tryResolve(refExpr.getReferent(), exprVisitor.getScope());
        break;
      } else {
        break;
      }
    }

    if (found != null) {
      if (isType) {
        myBuilder.addReferableType(referable, paramsNumber, found, arguments);
      } else {
        myBuilder.addReferableBody(referable, paramsNumber, found, arguments);
      }
    }
  }

  @Override
  public GlobalTypingInfo visitMeta(DefinableMetaDefinition def, Scope scope) {
    resolveTypeClassReference(def.getData(), def.getParameters(), def.body, scope, false);
    return null;
  }

  @Override
  public GlobalTypingInfo visitFunction(Concrete.BaseFunctionDefinition def, Scope scope) {
    if (def.getBody() instanceof Concrete.TermFunctionBody body) {
      resolveTypeClassReference(def.getData(), def.getParameters(), body.getTerm(), scope, false);
    }
    resolveTypeClassReference(def.getData(), def.getParameters(), def.getResultType(), scope, true);
    return null;
  }

  @Override
  public GlobalTypingInfo visitData(Concrete.DataDefinition def, Scope scope) {
    return null;
  }

  @Override
  public GlobalTypingInfo visitClass(Concrete.ClassDefinition def, Scope scope) {
    for (Concrete.ClassElement element : def.getElements()) {
      if (element instanceof Concrete.ClassField field) {
        resolveTypeClassReference(field.getData(), field.getParameters(), field.getResultType(), scope, true);
      }
    }
    return null;
  }
}
