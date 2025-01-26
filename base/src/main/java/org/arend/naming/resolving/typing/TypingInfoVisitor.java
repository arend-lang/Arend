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
import org.arend.term.group.Group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TypingInfoVisitor implements ConcreteResolvableDefinitionVisitor<Scope, GlobalTypingInfo> {
  private final GlobalTypingInfo myTypingInfo;

  public TypingInfoVisitor(GlobalTypingInfo parent) {
    myTypingInfo = parent;
  }

  public void processGroup(ConcreteGroup group, Scope scope) {
    Scope cachedScope = CachingScope.make(LexicalScope.insideOf(group, scope, false));
    if (group.definition() != null) {
      group.definition().accept(this, cachedScope);
    }

    if (group.definition() instanceof Concrete.ClassDefinition classDef) {
      List<GlobalReferable> superRefs = new ArrayList<>(classDef.getSuperClasses().size());
      for (Concrete.ReferenceExpression superClass : classDef.getSuperClasses()) {
        Referable ref = tryResolve(superClass.getReferent(), scope);
        if (ref instanceof GlobalReferable global) {
          superRefs.add(global);
        }
      }

      List<GlobalReferable> dynamicRefs = new ArrayList<>();
      for (Concrete.ClassElement element : classDef.getElements()) {
        if (element instanceof Concrete.ClassField field) {
          dynamicRefs.add(field.getData());
        }
      }
      for (Group subgroup : group.getDynamicSubgroups()) {
        dynamicRefs.add(subgroup.getReferable());
      }

      myTypingInfo.addDynamicScopeProvider(classDef.getData(), new DynamicScopeProviderImpl(classDef.getData(), superRefs, dynamicRefs));
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

  public static Referable tryResolve(Referable ref, Scope scope) {
    while (ref instanceof RedirectingReferable) {
      ref = ((RedirectingReferable) ref).getOriginalReferable();
    }
    return ref instanceof UnresolvedReference unresolved ? unresolved.copy().tryResolve(scope, null, ResolverListener.EMPTY) : ref;
  }

  public static AbstractBody resolveAbstractBody(Concrete.Expression expr) {
    return resolveAbstractBody(Collections.emptyList(), expr, null, true);
  }

  public static AbstractBody resolveAbstractBodyWithoutParameters(Concrete.Expression expr) {
    return resolveAbstractBody(Collections.emptyList(), expr, null, null);
  }

  private static AbstractBody resolveAbstractBody(List<? extends Concrete.Parameter> parameters, Concrete.Expression expr, Scope scope, Boolean isType) {
    if (expr == null) return null;
    ExpressionResolveNameVisitor exprVisitor = scope == null ? null : new ExpressionResolveNameVisitor(scope, new ArrayList<>(), TypingInfo.EMPTY, DummyErrorReporter.INSTANCE, ResolverListener.EMPTY);
    if (exprVisitor != null) exprVisitor.updateScope(parameters);

    int paramsNumber = 0;
    for (Concrete.Parameter parameter : parameters) {
      if (parameter.isExplicit()) {
        paramsNumber += parameter.getNumberOfParameters();
      }
    }

    if (isType != null) {
      if (isType) {
        while (expr instanceof Concrete.PiExpression piExpr) {
          if (exprVisitor != null) exprVisitor.updateScope(piExpr.getParameters());
          for (Concrete.TypeParameter parameter : piExpr.getParameters()) {
            if (parameter.isExplicit()) {
              paramsNumber += parameter.getNumberOfParameters();
            }
          }
          expr = piExpr.getCodomain();
        }
      } else {
        while (expr instanceof Concrete.LamExpression lamExpr) {
          if (exprVisitor != null) exprVisitor.updateScope(lamExpr.getParameters());
          for (Concrete.Parameter parameter : lamExpr.getParameters()) {
            if (parameter.isExplicit()) {
              paramsNumber += parameter.getNumberOfParameters();
            }
          }
          expr = lamExpr.getBody();
        }
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
        if (sequence.isEmpty()) return null;
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
          if (elem.fixity == Fixity.POSTFIX) return null; // TODO: Just ignore postfix operators for now.
          if (elem.isExplicit && elem.getComponent() instanceof Concrete.ReferenceExpression refExpr) {
            Referable ref = exprVisitor == null ? refExpr.getReferent() : tryResolve(refExpr.getReferent(), exprVisitor.getScope());
            if (ref != null && i == 0) first = ref;
            Precedence prec = ref instanceof GlobalReferable globalRef ? globalRef.getPrecedence() : Precedence.DEFAULT;
            if (prec.isInfix || elem.fixity == Fixity.INFIX) {
              Precedence.ComparisonResult cmp = best == null ? Precedence.ComparisonResult.LESS : prec.compare(bestPrec);
              if (cmp == Precedence.ComparisonResult.UNCOMPARABLE) return null;
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
        found = exprVisitor == null ? refExpr.getReferent() : tryResolve(refExpr.getReferent(), exprVisitor.getScope());
        break;
      } else {
        break;
      }
    }

    return found == null ? null : new AbstractBody(paramsNumber, found, arguments);
  }

  @Override
  public GlobalTypingInfo visitMeta(DefinableMetaDefinition def, Scope scope) {
    AbstractBody body = resolveAbstractBody(def.getParameters(), def.body, scope, false);
    if (body != null) myTypingInfo.addReferableBody(def.getData(), body);
    return null;
  }

  @Override
  public GlobalTypingInfo visitFunction(Concrete.BaseFunctionDefinition def, Scope scope) {
    if (def.getBody() instanceof Concrete.TermFunctionBody body) {
      AbstractBody abstractBody = resolveAbstractBody(def.getParameters(), body.getTerm(), scope, false);
      if (abstractBody != null) myTypingInfo.addReferableBody(def.getData(), abstractBody);
    }
    AbstractBody abstractBody = resolveAbstractBody(def.getParameters(), def.getResultType(), scope, true);
    if (abstractBody != null) myTypingInfo.addReferableType(def.getData(), abstractBody);
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
        AbstractBody abstractBody = resolveAbstractBody(field.getParameters(), field.getResultType(), scope, true);
        if (abstractBody != null) myTypingInfo.addReferableType(field.getData(), abstractBody);
      }
    }
    return null;
  }
}
