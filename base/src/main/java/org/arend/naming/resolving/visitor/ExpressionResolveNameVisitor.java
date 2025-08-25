package org.arend.naming.resolving.visitor;

import org.arend.core.context.Utils;
import org.arend.core.context.binding.LevelVariable;
import org.arend.core.context.param.EmptyDependentLink;
import org.arend.error.CountingErrorReporter;
import org.arend.ext.LiteralTypechecker;
import org.arend.ext.concrete.expr.ConcreteArgument;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.LocalError;
import org.arend.ext.module.LongName;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.reference.DataContainer;
import org.arend.ext.reference.ExpressionResolver;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.extImpl.ContextDataImpl;
import org.arend.naming.binOp.MetaBinOpParser;
import org.arend.naming.binOp.PatternBinOpEngine;
import org.arend.naming.error.DuplicateNameError;
import org.arend.ext.error.NameResolverError;
import org.arend.naming.error.ReferenceError;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.resolving.typing.*;
import org.arend.naming.resolving.typing.TypedReferable;
import org.arend.naming.scope.*;
import org.arend.naming.scope.local.ElimScope;
import org.arend.naming.scope.local.ListScope;
import org.arend.term.Fixity;
import org.arend.term.concrete.BaseConcreteExpressionVisitor;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ConcreteLevelExpressionVisitor;
import org.arend.typechecking.error.local.CannotFindConstructorError;
import org.arend.typechecking.error.local.ExpectedConstructorError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ExpressionResolveNameVisitor extends BaseConcreteExpressionVisitor<Void> implements ExpressionResolver, ConcreteLevelExpressionVisitor<LevelVariable, Concrete.LevelExpression> {
  private final Scope myParentScope;
  private final Scope myScope;
  private final List<TypedReferable> myContext;
  private final TypingInfo myTypingInfo;
  private final CountingErrorReporter myErrorReporter;
  private final LiteralTypechecker myLiteralTypechecker;
  private final ResolverListener myResolverListener;

  private ExpressionResolveNameVisitor(Scope parentScope, Scope scope, List<TypedReferable> context, TypingInfo typingInfo, ErrorReporter errorReporter, LiteralTypechecker literalTypechecker, ResolverListener resolverListener) {
    myParentScope = parentScope;
    myScope = scope;
    myContext = context;
    myTypingInfo = new LocalTypingInfo(typingInfo, context);
    myErrorReporter = new CountingErrorReporter(GeneralError.Level.ERROR, errorReporter);
    myLiteralTypechecker = literalTypechecker;
    myResolverListener = resolverListener;
  }

  public ExpressionResolveNameVisitor(Scope parentScope, List<TypedReferable> context, TypingInfo typingInfo, ErrorReporter errorReporter, LiteralTypechecker literalTypechecker, ResolverListener resolverListener, List<? extends Referable> pLevels, List<? extends Referable> hLevels) {
    this(parentScope, context == null && pLevels.isEmpty() && hLevels.isEmpty() ? parentScope : new ContextScope(parentScope, context == null ? Collections.emptyList() : context, pLevels, hLevels), context, typingInfo, errorReporter, literalTypechecker, resolverListener);
  }

  public ExpressionResolveNameVisitor(Scope parentScope, List<TypedReferable> context, TypingInfo typingInfo, ErrorReporter errorReporter, LiteralTypechecker literalTypechecker, ResolverListener resolverListener) {
    this(parentScope, context, typingInfo, errorReporter, literalTypechecker, resolverListener, Collections.emptyList(), Collections.emptyList());
  }

  @Override
  public @NotNull CountingErrorReporter getErrorReporter() {
    return myErrorReporter;
  }

  public List<TypedReferable> getContext() {
    return myContext;
  }

  public TypingInfo getTypingInfo() {
    return myTypingInfo;
  }

  @Override
  public @Nullable ArendRef resolveName(@NotNull String name) {
    Referable result = tryResolve(new NamedUnresolvedReference(null, name), myScope, null);
    return result != null && !(result instanceof UnresolvedReference) ? result : null;
  }

  @Override
  public @Nullable ArendRef resolveLongName(@NotNull LongName name) {
    Referable result = tryResolve(LongUnresolvedReference.make(null, name.toList()), myScope, null);
    return result != null && !(result instanceof UnresolvedReference) ? result : null;
  }

  @Override
  public @NotNull ConcreteExpression resolve(@NotNull ConcreteExpression expression) {
    if (!(expression instanceof Concrete.Expression)) {
      throw new IllegalArgumentException();
    }
    return ((Concrete.Expression) expression).accept(this, null);
  }

  @Override
  public @NotNull ConcreteExpression resolve(@Nullable Object data, @NotNull List<? extends ConcreteArgument> arguments) {
    if (arguments.isEmpty()) {
      throw new IllegalArgumentException();
    }
    if (arguments.size() == 1) {
      if (!(arguments.getFirst().getExpression() instanceof Concrete.Expression)) {
        throw new IllegalArgumentException();
      }
      return ((Concrete.Expression) arguments.getFirst().getExpression()).accept(this, null);
    }

    List<Concrete.BinOpSequenceElem<Concrete.Expression>> elems = new ArrayList<>(arguments.size());
    boolean first = true;
    for (ConcreteArgument argument : arguments) {
      if (!(argument instanceof Concrete.Argument)) {
        throw new IllegalArgumentException();
      }
      if (first) {
        elems.add(new Concrete.BinOpSequenceElem<>(((Concrete.Argument) argument).expression));
        first = false;
      } else {
        elems.add(new Concrete.BinOpSequenceElem<>(((Concrete.Argument) argument).expression, Fixity.UNKNOWN, argument.isExplicit()));
      }
    }

    return visitBinOpSequence(new Concrete.BinOpSequenceExpression(data, elems, null), null);
  }

  @Override
  public @NotNull ExpressionResolver hideRefs(@NotNull Set<? extends ArendRef> refs) {
    return new ExpressionResolveNameVisitor(myParentScope, new ElimScope(myScope, refs), myContext, myTypingInfo, myErrorReporter, myLiteralTypechecker, myResolverListener);
  }

  @Override
  public @NotNull ExpressionResolver useRefs(@NotNull List<? extends ArendRef> refs, boolean allowContext) {
    Scope scope;
    if (allowContext) {
      scope = new ListScope(myScope, refs);
    } else {
      List<TypedReferable> newRefs = new ArrayList<>(refs.size());
      for (ArendRef ref : refs) {
        if (ref instanceof Referable) {
          newRefs.add(new TypedReferable((Referable) ref, null));
        }
      }
      scope = new ContextScope(newRefs);
    }
    return new ExpressionResolveNameVisitor(myParentScope, scope, myContext, myTypingInfo, myErrorReporter, myLiteralTypechecker, myResolverListener);
  }

  @Override
  public void registerDeclaration(@NotNull ArendRef ref) {
    if (!(ref instanceof Referable)) {
      throw new IllegalArgumentException();
    }
    if (myResolverListener != null) {
      myResolverListener.bindingResolved((Referable) ref);
    }
  }

  @Override
  public @NotNull ArendRef getOriginalRef(@NotNull ArendRef ref) {
    while (ref instanceof RedirectingReferable) {
      ref = ((RedirectingReferable) ref).getOriginalReferable();
    }
    return ref;
  }

  @Override
  public boolean isUnresolved(@NotNull ArendRef ref) {
    return ref instanceof UnresolvedReference;
  }

  @Override
  public boolean isLongUnresolvedReference(@NotNull ArendRef ref) {
    return ref instanceof LongUnresolvedReference && ((LongUnresolvedReference) ref).getPath().size() > 1;
  }

  public Scope getScope() {
    return myScope;
  }

  public static Referable resolve(Referable referable, Scope scope, boolean withArg, List<Referable> resolvedRefs, @Nullable Scope.ScopeContext context, TypingInfo typingInfo, @Nullable ResolverListener listener) {
    referable = RedirectingReferable.getOriginalReferable(referable);
    if (referable instanceof UnresolvedReference) {
      if (withArg) {
        ((UnresolvedReference) referable).resolveExpression(scope, typingInfo, resolvedRefs, null);
      }
      referable = RedirectingReferable.getOriginalReferable(((UnresolvedReference) referable).resolve(scope, withArg ? null : resolvedRefs, context, listener));
    }
    return referable;
  }

  public static Referable resolve(Referable referable, Scope scope, @Nullable Scope.ScopeContext context, @Nullable ResolverListener listener) {
    return resolve(referable, scope, false, null, context, TypingInfo.EMPTY, listener);
  }

  public static Referable resolve(Referable referable, Scope scope) {
    return resolve(referable, scope, false, null, Scope.ScopeContext.STATIC, TypingInfo.EMPTY, null);
  }

  public static Referable tryResolve(Referable referable, Scope scope, List<Referable> resolvedRefs) {
    referable = RedirectingReferable.getOriginalReferable(referable);
    if (referable instanceof UnresolvedReference) {
      referable = RedirectingReferable.getOriginalReferable(((UnresolvedReference) referable).tryResolve(scope, resolvedRefs, null));
    }
    return referable;
  }

  private Concrete.Expression tryResolve(Concrete.ReferenceExpression refExpr, Scope scope, List<Referable> resolvedRefs) {
    Referable referable = RedirectingReferable.getOriginalReferable(refExpr.getReferent());
    refExpr.setReferent(referable);
    if (referable instanceof UnresolvedReference unresolved) {
      Concrete.Expression resolved = unresolved.tryResolveExpression(scope, myTypingInfo, resolvedRefs, myResolverListener);
      if (unresolved.isResolved()) {
        refExpr.setReferent(unresolved.resolve(scope, null, null));
      }
      return resolved == null ? refExpr : resolved;
    } else {
      return refExpr;
    }
  }

  private Concrete.Expression resolve(Concrete.ReferenceExpression refExpr, Scope scope, boolean removeRedirection, List<Referable> resolvedRefs, ResolverListener listener) {
    Referable referable = RedirectingReferable.getOriginalReferable(refExpr.getReferent());
    Concrete.Expression resolved;
    if (referable instanceof UnresolvedReference unresolved) {
      resolved = unresolved.resolveExpression(scope, myTypingInfo, resolvedRefs, listener);
      referable = unresolved.resolve(scope, null, listener);
      if (removeRedirection) {
        referable = RedirectingReferable.getOriginalReferable(referable);
      }
    } else {
      resolved = null;
    }
    refExpr.setReferent(referable);
    return resolved == null ? refExpr : resolved;
  }

  private void resolveLevels(Concrete.ReferenceExpression expr) {
    if (expr.getPLevels() != null) {
      expr.getPLevels().replaceAll(levelExpression -> levelExpression.accept(this, LevelVariable.PVAR));
    }
    if (expr.getHLevels() != null) {
      expr.getHLevels().replaceAll(levelExpression -> levelExpression.accept(this, LevelVariable.HVAR));
    }
  }

  void resolveLocal(Concrete.ReferenceExpression expr) {
    Referable origRef = expr.getReferent();
    if (origRef instanceof UnresolvedReference) {
      List<Referable> resolvedList = myResolverListener == null ? null : new ArrayList<>();
      Scope scope = myContext == null ? EmptyScope.INSTANCE : new ContextScope(myContext);
      resolve(expr, scope, true, resolvedList, myResolverListener);
      if (expr.getReferent() instanceof ErrorReference) {
        myErrorReporter.report(((ErrorReference) expr.getReferent()).getError());
      }
      if (myResolverListener != null) {
        myResolverListener.referenceResolved(null, origRef, expr, resolvedList);
      }
    }
  }

  public static MetaResolver getMetaResolver(Referable ref) {
    while (ref instanceof RedirectingReferable) {
      ref = ((RedirectingReferable) ref).getOriginalReferable();
    }
    return ref instanceof MetaReferable ? ((MetaReferable) ref).getResolver() : null;
  }

  public Concrete.Expression invokeMetaWithoutArguments(Concrete.Expression expr) {
    Concrete.Expression function;
    Concrete.Expression argument;
    if (expr instanceof Concrete.AppExpression appExpr && appExpr.getArguments().size() == 1 && !appExpr.getArguments().getFirst().isExplicit()) {
      function = appExpr.getFunction();
      argument = appExpr.getArguments().getFirst().expression;
    } else {
      function = expr;
      argument = null;
    }

    if (function instanceof Concrete.ReferenceExpression refExpr) {
      MetaResolver metaDef = getMetaResolver(refExpr.getReferent());
      if (metaDef != null) {
        myErrorReporter.resetErrorsNumber();
        return convertMetaResult(metaDef.resolvePrefix(this, new ContextDataImpl(refExpr, argument == null ? Collections.emptyList() : Collections.singletonList(new Concrete.Argument(argument, false)), null, null, null, null)), refExpr, Collections.emptyList(), null, null);
      }
    }

    return expr;
  }

  Concrete.Expression visitReference(Concrete.ReferenceExpression expr, boolean invokeMeta, boolean resolveLevels) {
    if (expr instanceof Concrete.FixityReferenceExpression) {
      Fixity fixity = ((Concrete.FixityReferenceExpression) expr).fixity;
      if (fixity == Fixity.INFIX || fixity == Fixity.POSTFIX) {
        myErrorReporter.report(new NameResolverError((fixity == Fixity.INFIX ? "Infix" : "Postfix") + " notation is not allowed here", expr));
      }
    }

    Referable origRef = expr.getReferent();
    while (origRef instanceof RedirectingReferable) {
      origRef = ((RedirectingReferable) origRef).getOriginalReferable();
    }

    Concrete.Expression resolved;
    if (origRef instanceof UnresolvedReference) {
      expr.setReferent(origRef);
      List<Referable> resolvedList = myResolverListener == null ? null : new ArrayList<>();
      resolved = resolve(expr, myScope, false, resolvedList, myResolverListener);
      if (expr.getReferent() instanceof ErrorReference) {
        myErrorReporter.report(((ErrorReference) expr.getReferent()).getError());
      }
      if (myResolverListener != null) {
        myResolverListener.referenceResolved(resolved, origRef, expr, resolvedList);
      }
    } else {
      resolved = null;
    }
    if (resolved == null) {
      resolved = expr;
    }

    if (resolveLevels) {
      resolveLevels(expr);
    }

    return invokeMeta ? invokeMetaWithoutArguments(resolved) : resolved;
  }

  @Override
  public Concrete.Expression visitReference(Concrete.ReferenceExpression expr, Void params) {
    return visitReference(expr, true, true);
  }

  @Override
  public Concrete.Expression visitFieldCall(Concrete.FieldCallExpression expr, Void params) {
    if (expr.fixity == Fixity.INFIX || expr.fixity == Fixity.POSTFIX) {
      myErrorReporter.report(new NameResolverError((expr.fixity == Fixity.INFIX ? "Infix" : "Postfix") + " notation is not allowed here", expr));
    }

    Concrete.Expression argument = expr.getArgument().accept(this, null);
    Referable ref = expr.getField();
    if (ref instanceof UnresolvedReference unresolved) {
      DynamicScopeProvider provider = argument instanceof Concrete.TypedExpression typedExpr
        ? myTypingInfo.getBodyDynamicScopeProvider(typedExpr.getType())
        : myTypingInfo.getTypeDynamicScopeProvider(argument);

      if (provider != null) {
        ref = unresolved.resolve(new DynamicScope(provider, myTypingInfo, DynamicScope.Extent.WITH_DYNAMIC), null, myResolverListener);
        if (ref instanceof ErrorReference errorRef) {
          myErrorReporter.report(errorRef.getError());
        }
      } else {
        ref = unresolved.tryResolve(myScope, null, Scope.ScopeContext.DYNAMIC, myResolverListener);
      }

      if (ref != null && myResolverListener != null) {
        myResolverListener.fieldCallResolved(expr, unresolved, ref);
      }
    }

    return ref != null ? Concrete.AppExpression.make(expr.getData(), new Concrete.ReferenceExpression(expr.getData(), ref), argument, false) : super.visitFieldCall(expr, params);
  }

  public Concrete.Expression convertMetaResult(ConcreteExpression expr, Concrete.ReferenceExpression refExpr, List<Concrete.Argument> args, Concrete.Coclauses coclauses, Concrete.FunctionClauses clauses) {
    if (!(expr == null || expr instanceof Concrete.Expression)) {
      throw new IllegalArgumentException();
    }
    if (expr == null) {
      if (myErrorReporter.getErrorsNumber() == 0) {
        myErrorReporter.report(new NameResolverError("Meta '" + refExpr.getReferent().getRefName() + "' failed", refExpr));
      }
      return new Concrete.ErrorHoleExpression(refExpr.getData(), null);
    }
    if (myResolverListener != null) {
      myResolverListener.metaResolved(refExpr, args, (Concrete.Expression) expr, coclauses, clauses);
    }
    return (Concrete.Expression) expr;
  }

  private Concrete.Expression visitMeta(Concrete.Expression function, List<Concrete.Argument> arguments, Concrete.Coclauses coclauses) {
    Concrete.ReferenceExpression refExpr;
    if (function instanceof Concrete.AppExpression && ((Concrete.AppExpression) function).getFunction() instanceof Concrete.ReferenceExpression) {
      refExpr = (Concrete.ReferenceExpression) ((Concrete.AppExpression) function).getFunction();
      List<Concrete.Argument> newArgs = new ArrayList<>(((Concrete.AppExpression) function).getArguments());
      newArgs.addAll(arguments);
      arguments = newArgs;
    } else if (function instanceof Concrete.ReferenceExpression) {
      refExpr = (Concrete.ReferenceExpression) function;
    } else {
      refExpr = null;
    }

    MetaResolver metaDef = refExpr == null ? null : getMetaResolver(refExpr.getReferent());
    if (metaDef == null) {
      return null;
    }
    myErrorReporter.resetErrorsNumber();
    return convertMetaResult(metaDef.resolvePrefix(this, new ContextDataImpl(refExpr, arguments, coclauses, null, null, null)), refExpr, arguments, coclauses, null);
  }

  @Override
  public Concrete.Expression visitApp(Concrete.AppExpression expr, Void params) {
    if (expr.getFunction() instanceof Concrete.ReferenceExpression) {
      Concrete.Expression function = visitReference((Concrete.ReferenceExpression) expr.getFunction(), false, true);
      Concrete.Expression metaResult = visitMeta(function, expr.getArguments(), null);
      return metaResult != null ? metaResult : visitArguments(function, expr.getArguments());
    } else {
      return visitArguments(expr.getFunction().accept(this, null), expr.getArguments());
    }
  }

  private Concrete.Expression visitArguments(Concrete.Expression function, List<Concrete.Argument> arguments) {
    for (Concrete.Argument argument : arguments) {
      function = Concrete.AppExpression.make(function.getData(), function, argument.expression.accept(this, null), argument.isExplicit());
    }
    return function;
  }

  @Override
  public Concrete.Expression visitBinOpSequence(Concrete.BinOpSequenceExpression expr, Void params) {
    return visitBinOpSequence(expr.getData(), expr, null);
  }

  private Concrete.Expression visitBinOpSequence(Object data, Concrete.BinOpSequenceExpression expr, Concrete.Coclauses coclauses) {
    if (expr.getSequence().isEmpty() && expr.getClauses() == null) {
      return visitClassExt(data, expr, coclauses);
    }
    if (expr.getSequence().size() == 1 && expr.getClauses() == null) {
      return visitClassExt(data, expr.getSequence().getFirst().getComponent().accept(this, null), coclauses);
    }

    boolean hasMeta = false;
    List<MetaBinOpParser.ResolvedReference> resolvedRefs = new ArrayList<>();
    for (Concrete.BinOpSequenceElem<Concrete.Expression> elem : expr.getSequence()) {
      if (elem.getComponent() instanceof Concrete.ReferenceExpression refExpr) {
        Referable ref = refExpr.getReferent();
        while (ref instanceof RedirectingReferable) {
          ref = ((RedirectingReferable) ref).getOriginalReferable();
        }
        if (ref instanceof UnresolvedReference) {
          List<Referable> resolvedList = myResolverListener == null ? null : new ArrayList<>();
          elem.setComponent(tryResolve(refExpr, myScope, resolvedList));
          resolvedRefs.add(new MetaBinOpParser.ResolvedReference(refExpr, (UnresolvedReference) ref, resolvedList));
        } else {
          resolvedRefs.add(new MetaBinOpParser.ResolvedReference(refExpr, null, null));
        }

        resolveLevels(refExpr);

        if (!hasMeta && getMetaResolver(refExpr.getReferent()) != null) {
          hasMeta = true;
        }
      } else {
        resolvedRefs.add(null);
      }
    }

    if (!hasMeta) {
      if (expr.getClauses() != null) {
        myErrorReporter.report(new NameResolverError("Clauses are not allowed here", expr.getClauses()));
      }
      for (int i = 0; i < resolvedRefs.size(); i++) {
        finalizeReference(expr.getSequence().get(i), resolvedRefs.get(i));
      }
      return visitClassExt(data, expr, coclauses);
    }

    return new MetaBinOpParser(this, expr, resolvedRefs, coclauses).parse(data);
  }

  public void finalizeReference(Concrete.BinOpSequenceElem<Concrete.Expression> elem, MetaBinOpParser.ResolvedReference resolvedReference) {
    if (resolvedReference == null) {
      elem.setComponent(elem.getComponent().accept(this, null));
      return;
    }
    if (resolvedReference.originalReference == null) {
      return;
    }

    if (resolvedReference.refExpr.getReferent() instanceof UnresolvedReference) {
      resolve(resolvedReference.refExpr, myScope, false, null, myResolverListener);
      if (resolvedReference.refExpr.getReferent() instanceof ErrorReference) {
        myErrorReporter.report(((ErrorReference) resolvedReference.refExpr.getReferent()).getError());
      }
    }
    if (resolvedReference.resolvedList != null && myResolverListener != null) {
      myResolverListener.referenceResolved(elem.getComponent(), resolvedReference.originalReference, resolvedReference.refExpr, resolvedReference.resolvedList);
    }
  }

  public void updateScope(Collection<? extends Concrete.Parameter> parameters) {
    for (Concrete.Parameter parameter : parameters) {
      for (Referable referable : parameter.getReferableList()) {
        if (referable != null && !referable.textRepresentation().equals("_")) {
          addLocalRef(referable, null);
        }
      }
    }
  }

  static boolean checkName(Referable ref, ErrorReporter errorReporter) {
    if (ref == null) {
      return true;
    }
    String name = ref.getRefName();
    for (int i = 0; i < name.length(); i++) {
      if (!Character.UnicodeBlock.BASIC_LATIN.equals(Character.UnicodeBlock.of(name.codePointAt(i)))) {
        errorReporter.report(new ReferenceError("Invalid name", ref));
        return false;
      }
    }
    return true;
  }

  private void addLocalRef(Referable ref, AbstractBody body) {
    if (checkName(ref, myErrorReporter)) {
      myContext.add(new TypedReferable(ref, body));
      if (myResolverListener != null) {
        myResolverListener.bindingResolved(ref);
      }
    }
  }

  @Override
  protected void visitParameter(Concrete.Parameter parameter, Void params) {
    if (parameter instanceof Concrete.TypeParameter) {
      ((Concrete.TypeParameter) parameter).type = ((Concrete.TypeParameter) parameter).type.accept(this, null);
    }

    AbstractBody abstractBody = TypingInfoVisitor.resolveAbstractBody(parameter.getType());
    List<? extends Referable> referableList = parameter.getReferableList();
    for (int i = 0; i < referableList.size(); i++) {
      Referable referable = referableList.get(i);
      if (referable != null && !referable.textRepresentation().equals("_")) {
        for (int j = 0; j < i; j++) {
          Referable referable1 = referableList.get(j);
          if (referable1 != null && referable.textRepresentation().equals(referable1.textRepresentation())) {
            myErrorReporter.report(new DuplicateNameError(GeneralError.Level.WARNING, referable, referable1));
          }
        }
        addLocalRef(referable, abstractBody);
      }
    }
  }

  @Override
  public Concrete.Expression visitLam(Concrete.LamExpression expr, Void params) {
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      if (expr instanceof Concrete.PatternLamExpression) {
        visitPatterns(((Concrete.PatternLamExpression) expr).getPatterns(), expr.getParameters(), new HashMap<>(), true);
      } else {
        visitParameters(expr.getParameters(), null);
      }
      expr.body = expr.body.accept(this, null);
      return expr;
    }
  }

  @Override
  public Concrete.Expression visitPi(Concrete.PiExpression expr, Void params) {
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      visitParameters(expr.getParameters(), null);
      expr.codomain = expr.codomain.accept(this, null);
      return expr;
    }
  }

  @Override
  public Concrete.Expression visitSigma(Concrete.SigmaExpression expr, Void params) {
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      visitParameters(expr.getParameters(), null);
      for (Concrete.TypeParameter parameter : expr.getParameters()) {
        if (!parameter.isExplicit()) {
          myErrorReporter.report(new NameResolverError("Parameters in sigma types must be explicit", parameter));
          parameter.setExplicit(true);
        }
      }
      return expr;
    }
  }

  @Override
  public Concrete.Expression visitCase(Concrete.CaseExpression expr, Void params) {
    Set<Referable> eliminatedRefs = new HashSet<>();
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      for (Concrete.CaseArgument caseArg : expr.getArguments()) {
        caseArg.expression = caseArg.expression.accept(this, null);
        if (caseArg.isElim && caseArg.expression instanceof Concrete.ReferenceExpression) {
          eliminatedRefs.add(((Concrete.ReferenceExpression) caseArg.expression).getReferent());
        }
        if (caseArg.type != null) {
          caseArg.type = caseArg.type.accept(this, null);
        }
        addReferable(caseArg.referable, caseArg.type, null);
      }
      if (expr.getResultType() != null) {
        expr.setResultType(expr.getResultType().accept(this, null));
      }
      if (expr.getResultTypeLevel() != null) {
        expr.setResultTypeLevel(expr.getResultTypeLevel().accept(this, null));
      }
    }

    List<TypedReferable> origContext = eliminatedRefs.isEmpty() ? null : new ArrayList<>(myContext);
    if (!eliminatedRefs.isEmpty()) {
      myContext.removeIf(ref -> eliminatedRefs.contains(ref.getReferable()));
    }
    visitClauses(expr.getClauses(), null);
    if (origContext != null) {
      myContext.clear();
      myContext.addAll(origContext);
    }

    return expr;
  }

  @Override
  public void visitClause(Concrete.Clause clause, Void params) {
    if (clause instanceof Concrete.FunctionClause functionClause) {
      try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
        visitPatterns(clause.getPatterns(), new HashMap<>());
        if (functionClause.expression != null) {
          functionClause.expression = functionClause.expression.accept(this, null);
        }
      }
    }
  }

  private void addReferable(Referable referable, Concrete.Expression type, Map<String, Referable> usedNames) {
    if (referable == null) {
      return;
    }

    String name = referable.textRepresentation();
    if (name.equals("_")) {
      return;
    }

    Referable prev = usedNames == null ? null : usedNames.put(name, referable);
    if (prev != null) {
      myErrorReporter.report(new DuplicateNameError(GeneralError.Level.WARNING, referable, prev));
    }

    addLocalRef(referable, type == null ? null : TypingInfoVisitor.resolveAbstractBody(type));
  }

  private Concrete.Pattern visitPattern(Concrete.Pattern pattern, Map<String, Referable> usedNames) {
    if (pattern.getAsReferable() != null && pattern.getAsReferable().type != null) {
      pattern.getAsReferable().type = pattern.getAsReferable().type.accept(this, null);
    }

    if (pattern instanceof Concrete.NamePattern namePattern) {
      if (namePattern.type != null) {
        namePattern.type = namePattern.type.accept(this, null);
      }

      Referable referable = namePattern.getReferable();
      if (referable == null || referable instanceof GlobalReferable) {
        return null;
      }

      if (namePattern.type == null || referable instanceof LongUnresolvedReference) {
        List<Referable> resolvedRefs = new ArrayList<>();

        Referable resolved;
        if (referable instanceof LongUnresolvedReference) {
          resolved = ((LongUnresolvedReference) referable).resolve(myParentScope, resolvedRefs, myResolverListener);
        } else if (referable instanceof UnresolvedReference) {
          resolved = ((UnresolvedReference) referable).tryResolve(myParentScope, resolvedRefs, myResolverListener);
        } else {
          resolved = myParentScope.resolveName(referable.getRefName());
          resolvedRefs.add(resolved);
        }

        Referable ref = resolved == null || resolved instanceof ErrorReference ? resolved : RedirectingReferable.getOriginalReferable(resolved);
        if (ref instanceof GlobalReferable && ((GlobalReferable) ref).getKind().isConstructor()) {
          if (pattern.getAsReferable() != null) {
            addReferable(pattern.getAsReferable().referable, pattern.getAsReferable().type, usedNames);
          }
          Concrete.ConstructorPattern result = new Concrete.ConstructorPattern(namePattern.getData(), namePattern.isExplicit(), namePattern.getData(), ref, List.of(), namePattern.getAsReferable());
          if (myResolverListener != null) {
            myResolverListener.patternResolved(referable, ref, result, resolvedRefs);
          }
          return result;
        }
        if (ref instanceof ErrorReference) {
          myErrorReporter.report(((ErrorReference) ref).getError());
          Concrete.NamePattern result = new Concrete.NamePattern(namePattern.getData(), namePattern.isExplicit(), ref, namePattern.type, namePattern.fixity);
          if (myResolverListener != null) {
            myResolverListener.patternResolved(referable, ref, result, resolvedRefs);
          }
          return result;
        }
      }

      LocalReferable local = referable.getRefName().equals("_") ? null : new DataLocalReferable(referable instanceof DataContainer ? ((DataContainer) referable).getData() : referable, referable.getRefName());
      addReferable(local, namePattern.type, usedNames);
      Concrete.NamePattern result = new Concrete.NamePattern(pattern.getData(), namePattern.isExplicit(), local, namePattern.type);
      if (myResolverListener != null && local != null) {
        myResolverListener.patternResolved(referable, local, result, Collections.singletonList(local));
      }
      return result;
    } else if (pattern instanceof Concrete.ConstructorPattern) {
      visitPatterns(((Concrete.ConstructorPattern) pattern).getPatterns(), null, usedNames, false);
    } else if (pattern instanceof Concrete.TuplePattern) {
      visitPatterns(((Concrete.TuplePattern) pattern).getPatterns(), null, usedNames, false);
    } else if (pattern instanceof Concrete.UnparsedConstructorPattern) {
      List<Concrete.BinOpSequenceElem<Concrete.Pattern>> correctedPatterns = resolveBinOpComponents((Concrete.UnparsedConstructorPattern) pattern);
      Concrete.Pattern parsedPattern = PatternBinOpEngine.parse(new Concrete.UnparsedConstructorPattern(pattern.getData(), pattern.isExplicit(), correctedPatterns, pattern.getAsReferable()), myErrorReporter);
      if (parsedPattern instanceof Concrete.ConstructorPattern) {
        Concrete.ConstructorPattern result = fixParsedConstructorPattern(pattern.getData(), pattern, usedNames, (Concrete.ConstructorPattern) parsedPattern);
        visitPatterns(result.getPatterns(), null, usedNames, false);
        if (pattern.getAsReferable() != null) {
          addReferable(pattern.getAsReferable().referable, pattern.getAsReferable().type, usedNames);
        }
        if (myResolverListener != null) {
          myResolverListener.patternParsed(result);
        }
        result.setExplicit(pattern.isExplicit());
        return result;
      }
      if (parsedPattern instanceof Concrete.UnparsedConstructorPattern) {
        myErrorReporter.report(new NameResolverError("Cannot parse pattern", pattern));
        return null;
      }
    } else if (!(pattern instanceof Concrete.NumberPattern)) {
      throw new IllegalStateException();
    }

    if (pattern.getAsReferable() != null) {
      addReferable(pattern.getAsReferable().referable, pattern.getAsReferable().type, usedNames);
    }

    return pattern;
  }

  private Concrete.@NotNull ConstructorPattern fixParsedConstructorPattern(@Nullable Object data, Concrete.Pattern pattern, Map<String, Referable> usedNames, Concrete.ConstructorPattern parsedPattern) {
    Referable constructor = parsedPattern.getConstructor();
    if (!(constructor instanceof GlobalReferable) || !((GlobalReferable) constructor).getKind().isConstructor()) {
      GeneralError error = constructor instanceof GlobalReferable ?
              new ExpectedConstructorError((GlobalReferable) constructor, null, null, parsedPattern, null, EmptyDependentLink.getInstance(), null, null) :
              new CannotFindConstructorError(pattern);
      getErrorReporter().report(error);
    }
    List<Concrete.Pattern> newPatterns = new ArrayList<>(parsedPattern.getPatterns());
    for (int i = 0; i < newPatterns.size(); i++) {
      Concrete.Pattern subPattern = newPatterns.get(i);
      if (subPattern instanceof Concrete.NamePattern namePattern && namePattern.getReferable() instanceof GlobalReferable) {
        newPatterns.set(i, new Concrete.ConstructorPattern(subPattern.getData(), subPattern.isExplicit(), subPattern.getData(), namePattern.getReferable(), List.of(), subPattern.getAsReferable()));
      } else if (subPattern instanceof Concrete.ConstructorPattern) {
        newPatterns.set(i, fixParsedConstructorPattern(subPattern.getData(), pattern, new HashMap<>(usedNames), (Concrete.ConstructorPattern) subPattern));
      }
    }
    return new Concrete.ConstructorPattern(data, true, parsedPattern.getConstructorData(), constructor, newPatterns, pattern.getAsReferable());
  }

  private List<Concrete.BinOpSequenceElem<Concrete.Pattern>> resolveBinOpComponents(Concrete.UnparsedConstructorPattern pattern) {
    List<Concrete.BinOpSequenceElem<Concrete.Pattern>> correctedPatterns = new ArrayList<>();
    boolean first = true;
    for (var component : pattern.getUnparsedPatterns()) {
      Concrete.Pattern subPattern = component.getComponent();
      Concrete.BinOpSequenceElem<Concrete.Pattern> corrected;
      if (subPattern instanceof Concrete.NamePattern namePattern) {
        Referable originalUnresolvedReferable = namePattern.getReferable();
        if (!(originalUnresolvedReferable instanceof UnresolvedReference) && originalUnresolvedReferable != null && !(originalUnresolvedReferable instanceof GlobalReferable)) {
          originalUnresolvedReferable = new NamedUnresolvedReference(originalUnresolvedReferable instanceof DataContainer ? ((DataContainer) originalUnresolvedReferable).getData() : originalUnresolvedReferable, originalUnresolvedReferable.getRefName());
        }
        List<Referable> resolvedRefs = new ArrayList<>();
        Referable resolved = tryResolve(originalUnresolvedReferable, myParentScope, resolvedRefs);
        if (resolved == null || resolved instanceof GlobalReferable && !((GlobalReferable) resolved).getKind().isConstructor()) {
          String name = originalUnresolvedReferable == null ? null : originalUnresolvedReferable.getRefName();
          Object data = originalUnresolvedReferable instanceof UnresolvedReference ? ((UnresolvedReference) originalUnresolvedReferable).getData() : null;
          Referable local = new DataLocalReferable(data, name);
          corrected = new Concrete.BinOpSequenceElem<>(new Concrete.NamePattern(subPattern.getData(), subPattern.isExplicit(), local, namePattern.type));
          if (myResolverListener != null && originalUnresolvedReferable != null) {
            myResolverListener.patternResolved(originalUnresolvedReferable, local, pattern, Collections.singletonList(local));
          }
        } else {
          if (resolved instanceof GlobalReferable) {
            Fixity fixity = first ? Fixity.NONFIX : ((GlobalReferable) resolved).getPrecedence().isInfix ? Fixity.INFIX : namePattern.fixity;
            corrected = new Concrete.BinOpSequenceElem<>(new Concrete.NamePattern(subPattern.getData(), subPattern.isExplicit(), resolved, namePattern.type), fixity, true);
          } else {
            resolved = originalUnresolvedReferable == null || originalUnresolvedReferable.getRefName().equals("_") ? null : new DataLocalReferable(originalUnresolvedReferable instanceof DataContainer ? ((DataContainer) originalUnresolvedReferable).getData() : originalUnresolvedReferable, originalUnresolvedReferable.getRefName());
            corrected = new Concrete.BinOpSequenceElem<>(new Concrete.NamePattern(subPattern.getData(), subPattern.isExplicit(), resolved, namePattern.type), first ? Fixity.NONFIX : namePattern.fixity, subPattern.isExplicit());
          }
        }
        if (resolved instanceof GlobalReferable && ((GlobalReferable) resolved).getKind().isConstructor() && myResolverListener != null) {
          myResolverListener.patternResolved(originalUnresolvedReferable, resolved, subPattern, resolvedRefs.size() == 1 ? Collections.singletonList(resolved) : resolvedRefs);
        }
      } else {
        corrected = component;
      }
      correctedPatterns.add(corrected);
      first = false;
    }
    return correctedPatterns;
  }

  private void visitPatterns(List<Concrete.Pattern> patterns, List<Concrete.Parameter> parameters, Map<String, Referable> usedNames, boolean resolvePatterns) {
    int j = 0;
    for (int i = 0; i < patterns.size(); i++) {
      Concrete.Pattern pattern = patterns.get(i);
      if (pattern == null) {
        visitParameter(parameters.get(j++), null);
        continue;
      }
      Concrete.Pattern parsedPattern = visitPattern(pattern, usedNames);
      if (parsedPattern != null) {
        patterns.set(i, parsedPattern);
      } else if (pattern instanceof Concrete.UnparsedConstructorPattern) {
        patterns.set(i, new Concrete.NamePattern(pattern.getData(), pattern.isExplicit(), null, null));
      }
      if (resolvePatterns) {
        resolvePattern(patterns.get(i));
      }
    }
  }

  public void visitPatterns(List<Concrete.Pattern> patterns, Map<String, Referable> usedNames) {
    visitPatterns(patterns, null, usedNames, true);
  }

  public void resolvePattern(Concrete.Pattern pattern) {
    if (pattern instanceof Concrete.TuplePattern) {
      for (Concrete.Pattern patternArg : ((Concrete.TuplePattern) pattern).getPatterns()) {
        resolvePattern(patternArg);
      }
      return;
    }
    if (!(pattern instanceof Concrete.ConstructorPattern)) {
      return;
    }

    Referable origReferable = ((Concrete.ConstructorPattern) pattern).getConstructor();
    if (origReferable instanceof UnresolvedReference) {
      List<Referable> resolvedList = myResolverListener == null ? null : new ArrayList<>();
      Referable referable = resolve(origReferable, myParentScope, false, resolvedList, Scope.ScopeContext.STATIC, myTypingInfo, myResolverListener);
      if (referable instanceof ErrorReference) {
        myErrorReporter.report(((ErrorReference) referable).getError());
      } else if (referable instanceof GlobalReferable && !((GlobalReferable) referable).getKind().isConstructor()) {
        myErrorReporter.report(new ExpectedConstructorError((GlobalReferable) referable, null, null, pattern, null, EmptyDependentLink.getInstance(), null, null));
      }

      ((Concrete.ConstructorPattern) pattern).setConstructor(referable);
      if (myResolverListener != null) {
        myResolverListener.patternResolved(origReferable, referable, pattern, resolvedList);
      }
    }
  }

  public Concrete.Expression visitClassExt(Object data, Concrete.Expression baseExpr, Concrete.Coclauses coclauses) {
    if (coclauses == null) {
      return baseExpr;
    }
    if (coclauses.getCoclauseList().isEmpty()) {
      return Concrete.ClassExtExpression.make(data, baseExpr, coclauses);
    }

    DynamicScopeProvider provider = myTypingInfo.getBodyOrTypeDynamicScopeProvider(baseExpr);
    if (provider != null) {
      visitClassFieldImpls(coclauses.getCoclauseList(), provider);
    } else {
      LocalError error = new NameResolverError("Expected a class or a class instance", baseExpr);
      myErrorReporter.report(error);
      return new Concrete.ErrorHoleExpression(data, error);
    }
    return Concrete.ClassExtExpression.make(data, baseExpr, coclauses);
  }

  @Override
  public Concrete.Expression visitClassExt(Concrete.ClassExtExpression expr, Void params) {
    Concrete.Expression baseExpr = expr.getBaseClassExpression();
    switch (baseExpr) {
      case Concrete.ReferenceExpression referenceExpression -> {
        baseExpr = visitReference(referenceExpression, false, true);
        Concrete.Expression metaResult = visitMeta(baseExpr, Collections.emptyList(), expr.getCoclauses());
        if (metaResult != null) {
          return metaResult;
        }
      }
      case Concrete.AppExpression appExpression -> {
        Concrete.Expression function = appExpression.getFunction();
        function = function instanceof Concrete.ReferenceExpression ? visitReference((Concrete.ReferenceExpression) function, false, true) : function.accept(this, null);
        Concrete.Expression metaResult = visitMeta(function, appExpression.getArguments(), expr.getCoclauses());
        if (metaResult != null) {
          return metaResult;
        }
        baseExpr = visitArguments(function, appExpression.getArguments());
      }
      case Concrete.BinOpSequenceExpression binOpSequenceExpression -> {
        return visitBinOpSequence(expr.getData(), binOpSequenceExpression, expr.getCoclauses());
      }
      default -> baseExpr = expr.getBaseClassExpression().accept(this, null);
    }

    expr.setBaseClassExpression(baseExpr);
    return visitClassExt(expr.getData(), baseExpr, expr.getCoclauses());
  }

  Referable visitClassFieldReference(Concrete.ClassElement element, Referable oldField, DynamicScopeProvider provider) {
    if (oldField instanceof UnresolvedReference) {
      List<Referable> resolvedRefs = myResolverListener == null ? null : new ArrayList<>();
      Referable field = resolve(oldField, new MergeScope(true, new DynamicScope(provider, myTypingInfo, DynamicScope.Extent.WITH_SUPER), myScope), false, resolvedRefs, Scope.ScopeContext.STATIC, myTypingInfo, myResolverListener);
      if (myResolverListener != null) {
        if (element instanceof Concrete.CoClauseElement) {
          myResolverListener.coPatternResolved((Concrete.CoClauseElement) element, oldField, field, resolvedRefs);
        } else if (element instanceof Concrete.OverriddenField) {
          myResolverListener.overriddenFieldResolved((Concrete.OverriddenField) element, oldField, field, resolvedRefs);
        }
      }
      if (field instanceof ErrorReference) {
        myErrorReporter.report(((ErrorReference) field).getError());
      }
      if (element instanceof Concrete.CoClauseElement) {
        ((Concrete.CoClauseElement) element).setImplementedField(field);
      } else if (element instanceof Concrete.OverriddenField) {
        ((Concrete.OverriddenField) element).setOverriddenField(field);
      }
      return field;
    }
    return oldField;
  }

  void visitClassFieldImpl(Concrete.ClassFieldImpl impl, DynamicScopeProvider provider) {
    visitClassFieldReference(impl, impl.getImplementedField(), provider);

    if (impl.implementation == null) {
      Referable ref = impl.getImplementedField();
      DynamicScopeProvider subProvider = myTypingInfo.getBodyDynamicScopeProvider(ref);
      if (subProvider == null) {
        subProvider = myTypingInfo.getTypeDynamicScopeProvider(ref);
      }
      if (subProvider != null) {
        if (subProvider.getReferable() instanceof TCDefReferable defRef) {
          impl.classRef = defRef;
        }
        visitClassFieldImpls(impl.getSubCoclauseList(), subProvider);
      }
    } else {
      impl.implementation = impl.implementation.accept(this, null);
    }
  }

  private void visitClassFieldImpls(List<Concrete.ClassFieldImpl> classFieldImpls, DynamicScopeProvider provider) {
    for (Concrete.ClassFieldImpl impl : classFieldImpls) {
      visitClassFieldImpl(impl, provider);
    }
  }

  @Override
  public Concrete.Expression visitLet(Concrete.LetExpression expr, Void params) {
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      List<Concrete.LetClause> letClauses = new ArrayList<>();
      for (Concrete.LetClause clause : expr.getClauses()) {
        Concrete.Expression newClauseTerm;
        Concrete.Expression clauseResultType = null;
        try (Utils.ContextSaver ignored1 = new Utils.ContextSaver(myContext)) {
          visitParameters(clause.getParameters(), null);
          if (clause.resultType != null) {
            clauseResultType = clause.resultType.accept(this, null);
          }
          newClauseTerm = clause.term.accept(this, null);
        }

        Concrete.Pattern pattern = clause.getPattern();
        if (pattern instanceof Concrete.NamePattern && ((Concrete.NamePattern) pattern).getRef() != null) {
          addLocalRef(((Concrete.NamePattern) pattern).getRef(), TypingInfoVisitor.resolveAbstractBody(clause.resultType != null ? clause.resultType : clause.term instanceof Concrete.NewExpression newExpr ? newExpr.expression : null));
        } else {
          ArrayList<Concrete.Pattern> newPattern = new ArrayList<>(List.of(pattern));
          visitPatterns(newPattern, null, new HashMap<>(), true);
          pattern = newPattern.getFirst();
        }
        letClauses.add(new Concrete.LetClause(clause.getParameters(), clauseResultType, newClauseTerm, pattern));
      }

      Concrete.Expression newBody = expr.expression.accept(this, null);
      return new Concrete.LetExpression(expr.getData(), expr.isHave(), expr.isStrict(), letClauses, newBody);
    }
  }

  @Override
  public Concrete.Expression visitNumericLiteral(Concrete.NumericLiteral expr, Void params) {
    if (myLiteralTypechecker == null) return expr;
    ConcreteExpression result = myLiteralTypechecker.resolveNumber(expr.getNumber(), this, new ContextDataImpl(expr, Collections.emptyList(), null, null, null, null));
    return result instanceof Concrete.Expression ? new Concrete.NumericLiteral(expr.getData(), expr.getNumber(), (Concrete.Expression) result) : expr;
  }

  @Override
  public Concrete.Expression visitStringLiteral(Concrete.StringLiteral expr, Void params) {
    if (myLiteralTypechecker == null) return expr;
    ConcreteExpression result = myLiteralTypechecker.resolveString(expr.getUnescapedString(), this, new ContextDataImpl(expr, Collections.emptyList(), null, null, null, null));
    return result instanceof Concrete.Expression ? new Concrete.StringLiteral(expr.getData(), expr.getUnescapedString(), (Concrete.Expression) result) : expr;
  }

  @Override
  public Concrete.Expression visitUniverse(Concrete.UniverseExpression expr, Void params) {
    Concrete.LevelExpression pLevel = expr.getPLevel();
    if (pLevel != null) {
      pLevel = pLevel.accept(this, LevelVariable.PVAR);
    }
    Concrete.LevelExpression hLevel = expr.getHLevel();
    if (hLevel != null) {
      hLevel = hLevel.accept(this, LevelVariable.HVAR);
    }
    return new Concrete.UniverseExpression(expr.getData(), pLevel, hLevel);
  }

  @Override
  public Concrete.LevelExpression visitInf(Concrete.InfLevelExpression expr, LevelVariable param) {
    return expr;
  }

  @Override
  public Concrete.LevelExpression visitLP(Concrete.PLevelExpression expr, LevelVariable param) {
    return expr;
  }

  @Override
  public Concrete.LevelExpression visitLH(Concrete.HLevelExpression expr, LevelVariable param) {
    return expr;
  }

  @Override
  public Concrete.LevelExpression visitNumber(Concrete.NumberLevelExpression expr, LevelVariable param) {
    return expr;
  }

  @Override
  public Concrete.LevelExpression visitVar(Concrete.VarLevelExpression expr, LevelVariable type) {
    Scope.ScopeContext scopeContext = type == LevelVariable.HVAR ? Scope.ScopeContext.HLEVEL : Scope.ScopeContext.PLEVEL;
    Referable ref = resolve(expr.getReferent(), myScope, scopeContext, myResolverListener);
    if (ref instanceof ErrorReference) {
      myErrorReporter.report(((ErrorReference) ref).getError());
    }
    Concrete.VarLevelExpression result = new Concrete.VarLevelExpression(expr.getData(), ref, type.getType());
    if (myResolverListener != null) {
      myResolverListener.levelResolved(expr.getReferent(), result, ref, new ArrayList<>(myScope.getElements(scopeContext)));
    }
    expr.setReferent(ref);
    return result;
  }

  @Override
  public Concrete.LevelExpression visitSuc(Concrete.SucLevelExpression expr, LevelVariable type) {
    return new Concrete.SucLevelExpression(expr.getData(), expr.getExpression().accept(this, type));
  }

  @Override
  public Concrete.LevelExpression visitMax(Concrete.MaxLevelExpression expr, LevelVariable type) {
    return new Concrete.MaxLevelExpression(expr.getData(), expr.getLeft().accept(this, type), expr.getRight().accept(this, type));
  }
}
