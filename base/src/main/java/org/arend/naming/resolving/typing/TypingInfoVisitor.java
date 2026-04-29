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
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;

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
      for (ConcreteGroup subgroup : group.dynamicGroups()) {
        dynamicRefs.add(subgroup.referable());
      }

      myTypingInfo.addDynamicScopeProvider(classDef.getData(), new DynamicScopeProviderImpl(classDef.getData(), superRefs, dynamicRefs));
    } else if (group.definition() == null
               && group.referable() instanceof TCDefReferable tcRef
               && tcRef.getTypechecked() instanceof org.arend.core.definition.ClassDefinition cd) {
      // Fallback for deserialized class groups (no Concrete.ClassDefinition): register
      // DynamicScopeProvider from the typechecked ClassDefinition so downstream fresh
      // classes that extend this one can resolve its inherited fields. Without this,
      // a class like `\class TopSpace \extends BaseSet { ... }` whose BaseSet is
      // deserialized will fail to resolve `E` (BaseSet's classifying field), leaving
      // TopSpace with HAS_ERRORS status and null classifying → downstream instance
      // search cycles on classes without a classifying field.
      List<GlobalReferable> superRefs = new ArrayList<>(cd.getSuperClasses().size());
      for (org.arend.core.definition.ClassDefinition sc : cd.getSuperClasses()) {
        superRefs.add(sc.getReferable());
      }
      List<GlobalReferable> dynamicRefs = new ArrayList<>();
      for (org.arend.core.definition.ClassField field : cd.getPersonalFields()) {
        if (field.getReferable() instanceof GlobalReferable gr) dynamicRefs.add(gr);
      }
      for (ConcreteGroup subgroup : group.dynamicGroups()) {
        dynamicRefs.add(subgroup.referable());
      }
      myTypingInfo.addDynamicScopeProvider(tcRef, new DynamicScopeProviderImpl(tcRef, superRefs, dynamicRefs));
      // Each field has type `\Pi (this : ThisClass) → ...target` — register the
      // field's referable → AbstractBody pointing at the target class so callers
      // of `getTypeDynamicScopeProvider(fieldRef)` find the right scope. Note we
      // pass priorParams=0: getCodomain() strips the implicit `this` Pi, and the
      // source-side `resolveAbstractBody` does NOT count `this` either (it only
      // counts explicit concrete parameters).
      for (org.arend.core.definition.ClassField field : cd.getPersonalFields()) {
        if (field.getType() != null && field.getType().getCodomain() != null) {
          registerCoreType(field.getReferable(), field.getType().getCodomain(),
              /*priorParams=*/ 0);
        }
      }
    } else if (group.definition() == null
               && group.referable() instanceof TCDefReferable tcRef
               && tcRef.getTypechecked() instanceof org.arend.core.definition.FunctionDefinition fnDef) {
      // Fallback for deserialized function/instance groups: add referableType so that
      // `typingInfo.getTypeDynamicScopeProvider(fnRef)` can find the class whose instance
      // this function produces. Without this, `Instance.fieldName` resolution through
      // a deserialized instance returns null and the resolver falls back to
      // FieldCallExpression, which then fails in CheckTypeVisitor#visitFieldCall.
      if (fnDef.getResultType() != null) {
        // The source-side `resolveAbstractBody` counts only EXPLICIT parameters, and does
        // NOT count the implicit `this` binding for class-internal functions (it doesn't
        // appear in the concrete AST at all). Match that convention — otherwise the
        // cached `typeBody.params` mismatches the caller's argument count during
        // `getTypeDynamicScopeProvider`, the lookup returns null, and the resolver
        // falls back to FieldCallExpression, which then reports "Cannot find 'X' in
        // class 'Y'" for non-field dynamic members.
        int params = 0;
        org.arend.core.context.param.DependentLink link = fnDef.getParameters();
        if (fnDef.hasEnclosingClass() && link.hasNext()) {
          link = link.getNext();  // skip the implicit `this`
        }
        while (link.hasNext()) {
          if (link.isExplicit()) params++;
          link = link.getNext();
        }
        registerCoreType(tcRef, fnDef.getResultType(), params);
      }
      // Recover precedence for deserialized coclause functions. A coclause function
      // generated for `\instance i : C \cowith | f => ...` or `\class C { \default f => ... }`
      // inherits its precedence from the implemented field `f`. Source-mode populates
      // this map in `visitFunction` / `visitClass` from `Concrete.CoClauseFunctionReference`;
      // for deserialized groups those concrete references are absent, but the core
      // `FunctionDefinition.getImplementedField()` carries the same information. Without
      // this, `GlobalTypingInfo.getRefPrecedence` falls back to the coclause function's
      // own (always-DEFAULT) precedence, so an infix field like `+` is treated as
      // non-infix when parsed through the instance — "1 + 2" becomes application
      // `1 + 2` rather than the expected binop.
      if (tcRef.getKind() == GlobalReferable.Kind.COCLAUSE_FUNCTION && fnDef.getImplementedField() != null) {
        TCDefReferable parentTcRef = tcRef.getLocatedReferableParent() instanceof TCDefReferable p ? p : tcRef;
        boolean isBodyRef = parentTcRef.getTypechecked() instanceof org.arend.core.definition.ClassDefinition;
        myTypingInfo.addReferablePrecedence(tcRef, parentTcRef, isBodyRef, fnDef.getImplementedField());
      }
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

  /** Register `AbstractBody` for {@code ref} whose type is (possibly applied) {@code coreType}. */
  private void registerCoreType(org.arend.naming.reference.TCDefReferable ref,
                                org.arend.core.expr.Expression coreType,
                                int priorParams) {
    // Count EXPLICIT Pi parameters in the type: `\Pi (x : T1) → \Pi (y : T2) → ... → TargetDefCall`.
    // Implicit parameters are not counted (matches source's `resolveAbstractBody` for isType=true).
    int piParams = 0;
    org.arend.core.expr.Expression cur = coreType;
    while (cur instanceof org.arend.core.expr.PiExpression piExpr) {
      org.arend.core.context.param.DependentLink link = piExpr.getParameters();
      while (link.hasNext()) {
        if (link.isExplicit()) piParams++;
        link = link.getNext();
      }
      cur = piExpr.getCodomain();
    }
    // Unwrap arg applications. Source counts only explicit arguments; mirror that.
    int arguments = 0;
    while (cur instanceof org.arend.core.expr.AppExpression appExpr) {
      if (appExpr.isExplicit()) arguments++;
      cur = appExpr.getFunction();
    }
    if (cur instanceof org.arend.core.expr.DefCallExpression defCall) {
      myTypingInfo.addReferableType(ref,
          new AbstractBody(priorParams + piParams, defCall.getDefinition().getReferable(), arguments));
    } else if (cur instanceof org.arend.core.expr.ClassCallExpression classCall) {
      // Count parameter-field implementations — these correspond to the positional
      // arguments the source-side `resolveAbstractBody` would count from a
      // `Concrete.AppExpression` chain. Source's ClassExt branch resets arguments
      // to 0 and then walks the base; parameter-field implementations (e.g. `S=Cod`
      // in `SubRing Cod`) mirror that count in core.
      int classArgs = 0;
      for (org.arend.core.definition.ClassField field : classCall.getImplementedHere().keySet()) {
        if (field.getReferable().isParameterField()) classArgs++;
      }
      myTypingInfo.addReferableType(ref,
          new AbstractBody(priorParams + piParams, classCall.getDefinition().getReferable(), classArgs));
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
    ExpressionResolveNameVisitor exprVisitor = scope == null ? null : new ExpressionResolveNameVisitor(scope, new ArrayList<>(), TypingInfo.EMPTY, DummyErrorReporter.INSTANCE, null, ResolverListener.EMPTY);
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
    label:
    while (true) {
      switch (expr) {
        case Concrete.AppExpression appExpr:
          expr = appExpr.getFunction();
          arguments += appExpr.getNumberOfExplicitArguments();
          break;
        case Concrete.ClassExtExpression classExtExpression:
          expr = classExtExpression.getBaseClassExpression();
          arguments = 0;
          break;
        case Concrete.BinOpSequenceExpression binOpExpr:
          var sequence = binOpExpr.getSequence();
          if (sequence.isEmpty()) return null;
          if (sequence.size() == 1) {
            expr = sequence.getFirst().getComponent();
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
              if (prec.isInfix && elem.fixity != Fixity.NONFIX || elem.fixity == Fixity.INFIX) {
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
            if (!(sequence.getFirst().getComponent() instanceof Concrete.ReferenceExpression)) {
              expr = sequence.getFirst().getComponent();
              continue;
            }
            found = first;
          } else {
            found = best;
            arguments += bestIndex == 0 || bestIndex == sequence.size() - 1 ? 1 : 2;
          }
          break label;
        case Concrete.ReferenceExpression refExpr:
          found = exprVisitor == null ? refExpr.getReferent() : tryResolve(refExpr.getReferent(), exprVisitor.getScope());
          break label;
        default:
          break label;
      }
    }

    return found == null ? null : new AbstractBody(paramsNumber, RedirectingReferable.getOriginalReferable(found), arguments);
  }

  @Override
  public GlobalTypingInfo visitMeta(Concrete.MetaDefinition def, Scope scope) {
    AbstractBody body = resolveAbstractBody(def.getParameters(), def.body, scope, false);
    if (body != null) myTypingInfo.addReferableBody(def.getData(), body);
    return null;
  }

  @Override
  public GlobalTypingInfo visitFunction(Concrete.BaseFunctionDefinition def, Scope scope) {
    if (def.getBody() instanceof Concrete.TermFunctionBody body) {
      AbstractBody abstractBody = resolveAbstractBody(def.getParameters(), body.getTerm(), scope, false);
      if (abstractBody != null) myTypingInfo.addReferableBody(def.getData(), abstractBody);
    } else if (def.getBody() instanceof Concrete.CoelimFunctionBody body) {
      for (Concrete.CoClauseElement element : body.getCoClauseElements()) {
        if (element instanceof Concrete.CoClauseFunctionReference reference) {
          myTypingInfo.addReferablePrecedence(reference.functionReference, def.getData(), false, reference.getImplementedField());
        }
      }
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
      } else if (element instanceof Concrete.CoClauseFunctionReference reference) {
        myTypingInfo.addReferablePrecedence(reference.functionReference, def.getData(), true, reference.getImplementedField());
      }
    }
    return null;
  }
}
