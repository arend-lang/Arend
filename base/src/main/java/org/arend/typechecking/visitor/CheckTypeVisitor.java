package org.arend.typechecking.visitor;

import org.arend.core.context.LinkList;
import org.arend.core.context.Utils;
import org.arend.core.context.binding.*;
import org.arend.core.context.binding.inference.*;
import org.arend.core.context.param.*;
import org.arend.core.definition.*;
import org.arend.core.elimtree.ElimBody;
import org.arend.core.expr.*;
import org.arend.core.expr.let.*;
import org.arend.core.expr.type.Type;
import org.arend.core.expr.type.TypeExpression;
import org.arend.core.expr.visitor.*;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.subst.*;
import org.arend.error.*;
import org.arend.ext.ArendExtension;
import org.arend.ext.FreeBindingsModifier;
import org.arend.ext.concrete.pattern.ConcreteNumberPattern;
import org.arend.ext.concrete.ConcreteParameter;
import org.arend.ext.concrete.pattern.ConcretePattern;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteLamExpression;
import org.arend.ext.core.body.CoreExpressionPattern;
import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.context.CoreInferenceVariable;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.context.CoreParameterBuilder;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.definition.CoreFunctionDefinition;
import org.arend.ext.core.expr.*;
import org.arend.ext.core.level.CoreSort;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.ops.CMP;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.core.ops.SubstitutionPair;
import org.arend.ext.error.*;
import org.arend.ext.instance.InstanceSearchParameters;
import org.arend.ext.instance.SubclassSearchParameters;
import org.arend.ext.prettifier.ExpressionPrettifier;
import org.arend.ext.prettifier.MergingExpressionPrettifier;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.*;
import org.arend.ext.util.StringUtils;
import org.arend.extImpl.*;
import org.arend.extImpl.userData.UserDataHolderImpl;
import org.arend.naming.reference.*;
import org.arend.naming.renamer.Renamer;
import org.arend.prelude.Prelude;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ConcreteExpressionVisitor;
import org.arend.term.concrete.ConcreteLevelExpressionVisitor;
import org.arend.term.prettyprint.LocalExpressionPrettifier;
import org.arend.typechecking.dfs.FieldDFS;
import org.arend.typechecking.LevelContext;
import org.arend.typechecking.TypecheckerState;
import org.arend.typechecking.TypecheckingContext;
import org.arend.typechecking.computation.ComputationRunner;
import org.arend.typechecking.doubleChecker.CoreException;
import org.arend.typechecking.doubleChecker.CoreExpressionChecker;
import org.arend.typechecking.error.ErrorReporterCounter;
import org.arend.typechecking.error.local.*;
import org.arend.ext.error.ArgInferenceError;
import org.arend.typechecking.error.local.inference.RecursiveInstanceInferenceError;
import org.arend.typechecking.implicitargs.ImplicitArgsInference;
import org.arend.typechecking.implicitargs.StdImplicitArgsInference;
import org.arend.typechecking.implicitargs.equations.*;
import org.arend.typechecking.instance.pool.GlobalInstancePool;
import org.arend.typechecking.instance.pool.RecursiveInstanceHoleExpression;
import org.arend.typechecking.dfs.DFS;
import org.arend.typechecking.dfs.MapDFS;
import org.arend.typechecking.patternmatching.*;
import org.arend.typechecking.result.DefCallResult;
import org.arend.typechecking.result.TResult;
import org.arend.typechecking.result.TypecheckingResult;
import org.arend.ext.util.Pair;
import org.arend.util.SingletonList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

import static org.arend.core.expr.ExpressionFactory.*;
import static org.arend.ext.error.ArgInferenceError.expression;

public class CheckTypeVisitor extends UserDataHolderImpl implements ConcreteExpressionVisitor<Expression, TypecheckingResult>, ConcreteLevelExpressionVisitor<LevelVariable, Level>, ExpressionTypechecker {
  private final Equations myEquations;
  private GlobalInstancePool myInstancePool;
  private final ImplicitArgsInference myArgsInference;
  protected Map<Referable, Binding> context;
  private LocalExpressionPrettifier myLocalPrettifier;
  private MyErrorReporter errorReporter;
  private final List<ClassCallExpression.ClassCallBinding> myClassCallBindings = new ArrayList<>();
  private final List<DeferredMeta> myDeferredMetasBeforeSolver = new ArrayList<>();
  private final List<DeferredMeta> myDeferredMetasAfterLevels = new ArrayList<>();
  private final ArendExtension myArendExtension;
  private TypecheckerState mySavedState;
  private LevelContext myLevelContext;
  private Definition myDefinition;
  private Set<TCDefReferable> myRecursiveDefinitions = Collections.emptySet();
  private boolean myAllowDeferredMetas = true;

  private record DeferredMeta(MetaDefinition meta, Map<Referable, Binding> context, LocalExpressionPrettifier localPrettifier, ContextDataImpl contextData, InferenceVariable inferenceVar, MyErrorReporter errorReporter) {}

  public static class MyErrorReporter implements ErrorReporter {
    private final CountingErrorReporter myErrorReporter;
    private Definition.TypeCheckingStatus myStatus = Definition.TypeCheckingStatus.NO_ERRORS;

    private MyErrorReporter(ErrorReporter errorReporter) {
      myErrorReporter = new CountingErrorReporter(GeneralError.Level.ERROR, errorReporter);
    }

    private void setStatus(GeneralError error) {
      myStatus = myStatus.max(error.level == GeneralError.Level.ERROR ? Definition.TypeCheckingStatus.HAS_ERRORS : Definition.TypeCheckingStatus.HAS_WARNINGS);
    }

    @Override
    public void report(GeneralError error) {
      setStatus(error);
      myErrorReporter.report(error);
    }
  }

  public void setStatus(Definition.TypeCheckingStatus status) {
    errorReporter.myStatus = errorReporter.myStatus.max(status);
  }

  protected CheckTypeVisitor(Map<Referable, Binding> localContext, LocalExpressionPrettifier localPrettifier, ErrorReporter errorReporter, GlobalInstancePool pool, ArendExtension arendExtension, UserDataHolderImpl holder) {
    this.errorReporter = new MyErrorReporter(errorReporter);
    myEquations = new TwoStageEquations(this);
    myInstancePool = pool;
    myArgsInference = new StdImplicitArgsInference(this);
    context = localContext;
    myLocalPrettifier = localPrettifier;
    myArendExtension = arendExtension;
    if (holder != null) {
      setUserData(holder);
    }
  }

  public CheckTypeVisitor(ErrorReporter errorReporter, GlobalInstancePool pool, ArendExtension arendExtension) {
    this(new LinkedHashMap<>(), new LocalExpressionPrettifier(), errorReporter, pool, arendExtension, null);
  }

  protected CheckTypeVisitor copy(Map<Referable, Binding> localContext, LocalExpressionPrettifier localPrettifier, ErrorReporter errorReporter, GlobalInstancePool pool, ArendExtension arendExtension, UserDataHolderImpl holder) {
    return new CheckTypeVisitor(localContext, localPrettifier, errorReporter, pool, arendExtension, holder);
  }

  public ArendExtension getExtension() {
    return myArendExtension;
  }

  public TypecheckingContext saveTypecheckingContext() {
    return new TypecheckingContext(new LinkedHashMap<>(context), new LocalExpressionPrettifier(myLocalPrettifier), myInstancePool, myArendExtension, copyUserData(), myLevelContext);
  }

  public Definition getDefinition() {
    return myDefinition;
  }

  public void setDefinition(Definition definition) {
    myDefinition = definition;
  }

  public void setRecursiveDefinitions(Set<TCDefReferable> definitions) {
    myRecursiveDefinitions = definitions;
  }

  public static CheckTypeVisitor loadTypecheckingContext(TypecheckingContext typecheckingContext, ErrorReporter errorReporter) {
    CheckTypeVisitor visitor = new CheckTypeVisitor(typecheckingContext.localContext, typecheckingContext.localPrettifier, errorReporter, null, typecheckingContext.arendExtension, typecheckingContext.userDataHolder);
    visitor.setInstancePool(typecheckingContext.instancePool.copy(visitor));
    visitor.setLevelContext(typecheckingContext.levelContext);
    return visitor;
  }

  public Referable addBinding(@Nullable Referable referable, Binding binding) {
    Referable ref = referable != null ? referable : new FakeLocalReferable(binding.getName() != null ? binding.getName() : "_");
    context.put(ref, binding);
    return ref;
  }

  public void addBindings(Map<Referable, Binding> bindings) {
    context.putAll(bindings);
  }

  private void removeBinding(Referable ref) {
    Binding binding = context.remove(ref);
    if (binding != null) {
      myLocalPrettifier.removeBinding(binding);
    }
  }

  public GlobalInstancePool getInstancePool() {
    return myInstancePool;
  }

  public void setInstancePool(GlobalInstancePool pool) {
    myInstancePool = pool;
  }

  public boolean isPBased() {
    return myLevelContext == null || myLevelContext.isPBased;
  }

  public boolean isHBased() {
    return myLevelContext == null || myLevelContext.isHBased;
  }

  public void setLevelContext(LevelContext levelContext) {
    myLevelContext = levelContext;
  }

  @NotNull
  public Map<Referable, Binding> getContext() {
    return context;
  }

  public void copyContextFrom(Map<? extends Referable, ? extends Binding> context) {
    this.context = new LinkedHashMap<>(context);
    myLocalPrettifier.clear();
  }

  public Set<Binding> getAllBindings() {
    Set<Binding> result = new HashSet<>();
    for (Binding binding : context.values()) {
      result.add(binding);
      if (binding instanceof EvaluatingBinding) {
        Expression expr = ((EvaluatingBinding) binding).getExpression();
        if (expr instanceof ReferenceExpression) {
          result.add(((ReferenceExpression) expr).getBinding());
        }
      }
    }
    return result;
  }

  private static class VeryFakeLocalReferable extends FakeLocalReferable {
    public VeryFakeLocalReferable(String name) {
      super(name);
    }
  }

  @Override
  public @NotNull List<CoreBinding> getFreeBindingsList() {
    List<CoreBinding> result = new ArrayList<>();
    for (Map.Entry<Referable, Binding> entry : context.entrySet()) {
      if (!(entry.getKey() instanceof VeryFakeLocalReferable)) {
        result.add(entry.getValue());
      }
    }
    return result;
  }

  @Override
  public @Nullable CoreBinding getFreeBinding(@NotNull ArendRef ref) {
    if (!(ref instanceof Referable)) {
      throw new IllegalArgumentException();
    }
    return context.get(ref);
  }

  @Override
  public @Nullable CoreBinding getThisBinding() {
    return context.isEmpty() ? null : context.values().iterator().next();
  }

  public LocalExpressionPrettifier getLocalExpressionPrettifier() {
    return myLocalPrettifier;
  }

  @Override
  public @Nullable ExpressionPrettifier getExpressionPrettifier() {
    ExpressionPrettifier result = new LocalExpressionPrettifier(myLocalPrettifier);
    ExpressionPrettifier prettifier = myArendExtension == null ? null : myArendExtension.getExpressionPrettifier();
    if (prettifier != null) {
      result = new MergingExpressionPrettifier(Arrays.asList(result, prettifier));
    }
    return result;
  }

  @NotNull
  @Override
  public ErrorReporter getErrorReporter() {
    return errorReporter;
  }

  public Equations getEquations() {
    return myEquations;
  }

  public int getNumberOfErrors() {
    return errorReporter.myErrorReporter.getErrorsNumber();
  }

  public Definition.TypeCheckingStatus getStatus() {
    return errorReporter.myStatus;
  }

  @Override
  public boolean compare(@NotNull UncheckedExpression expr1, @NotNull UncheckedExpression expr2, @NotNull CMP cmp, @Nullable ConcreteSourceNode marker, boolean allowEquations, boolean normalize, boolean useTypes) {
    CompareVisitor visitor = new CompareVisitor(myEquations, cmp, marker instanceof Concrete.SourceNode ? (Concrete.SourceNode) marker : null);
    if (!allowEquations) {
      visitor.doNotAllowEquations();
    }
    if (!normalize) {
      visitor.doNotNormalize();
    }
    return visitor.compare(UncheckedExpressionImpl.extract(expr1), UncheckedExpressionImpl.extract(expr2), null, useTypes);
  }

  public static TypecheckingResult coerceFromType(TypecheckingResult result) {
    Expression curType = result.type;
    if (!(curType instanceof FunCallExpression && ((FunCallExpression) curType).getDefinition().getKind() == CoreFunctionDefinition.Kind.TYPE)) {
      return null;
    }

    Expression curExpr = result.expression;
    while (curType instanceof FunCallExpression && ((FunCallExpression) curType).getDefinition().getKind() == CoreFunctionDefinition.Kind.TYPE) {
      curExpr = TypeDestructorExpression.match((FunCallExpression) curType, curExpr);
      if (curExpr == null) {
        return null;
      }
      curType = curExpr.getType().normalize(NormalizationMode.WHNF);
    }
    return new TypecheckingResult(curExpr, curType);
  }

  private static <T> Pair<TypecheckingResult,T> coerceToType(Expression expectedType, Function<Expression, Pair<Expression,T>> checker) {
    List<TypeConstructorExpression> stack = new ArrayList<>();
    Expression curType = expectedType;
    while (curType instanceof FunCallExpression && ((FunCallExpression) curType).getDefinition().getKind() == CoreFunctionDefinition.Kind.TYPE) {
      TypeConstructorExpression typeCoerce = (TypeConstructorExpression) TypeConstructorExpression.match((FunCallExpression) curType, null);
      if (typeCoerce == null) {
        break;
      }
      stack.add(typeCoerce);
      curType = typeCoerce.getArgumentType().normalize(NormalizationMode.WHNF);
    }
    if (!stack.isEmpty()) {
      Pair<Expression,T> pair = checker.apply(curType);
      if (pair == null) return null;
      Expression curExpr = pair.proj1;
      if (curExpr == null) return new Pair<>(null, pair.proj2);
      for (int i = stack.size() - 1; i >= 0; i--) {
        stack.get(i).setArgument(curExpr);
        curExpr = stack.get(i);
      }
      return new Pair<>(new TypecheckingResult(stack.get(0), expectedType), pair.proj2);
    }
    return null;
  }

  public TypecheckingResult checkResult(Expression expectedType, TypecheckingResult result, Concrete.Expression expr) {
    boolean isOmega = expectedType instanceof Type && ((Type) expectedType).isOmega();
    if (result == null || expectedType == null || isOmega && result.type instanceof UniverseExpression) {
      return result;
    }

    CompareVisitor cmpVisitor = new CompareVisitor(DummyEquations.getInstance(), CMP.LE, expr);
    if (!isOmega && cmpVisitor.nonNormalizingCompare(result.type, expectedType, Type.OMEGA)) {
      return result;
    }

    result.type = result.type.normalize(NormalizationMode.WHNF);
    expectedType = expectedType.normalize(NormalizationMode.WHNF);

    if (result.expression instanceof FunCallExpression idp && ((FunCallExpression) result.expression).getDefinition() == Prelude.IDP) {
      FunCallExpression equality = expectedType.toEquality();
      if (equality != null) {
        CompareVisitor visitor = new CompareVisitor(myEquations, CMP.LE, expr);
        if (!idp.getLevels().compare(equality.getLevels(), CMP.LE, myEquations, expr)) {
          Expression resultType = FunCallExpression.make(Prelude.PATH_INFIX, idp.getLevels(), Arrays.asList(idp.getDefCallArguments().get(0), idp.getDefCallArguments().get(1), idp.getDefCallArguments().get(1)));
          errorReporter.report(new TypeMismatchWithSubexprError(new CompareVisitor.Result(resultType, equality, resultType, equality, idp.getLevels(), equality.getLevels()), expr));
          return null;
        }
        if (!visitor.compare(idp.getDefCallArguments().get(0), equality.getDefCallArguments().get(0), Type.OMEGA, false)) {
          Expression resultType = FunCallExpression.make(Prelude.PATH_INFIX, idp.getLevels(), Arrays.asList(idp.getDefCallArguments().get(0), idp.getDefCallArguments().get(1), idp.getDefCallArguments().get(1)));
          errorReporter.report(new TypeMismatchWithSubexprError(new CompareVisitor.Result(resultType, equality, idp.getDefCallArguments().get(0), equality.getDefCallArguments().get(0), null, null), expr));
          return null;
        }
        visitor.setCMP(CMP.EQ);
        Expression type = equality.getDefCallArguments().get(0);
        Expression left = equality.getDefCallArguments().get(1).getUnderlyingExpression();
        Expression right = equality.getDefCallArguments().get(2).getUnderlyingExpression();
        Expression idpArg = idp.getDefCallArguments().get(1).getUnderlyingExpression();
        boolean isNotEqualError = idpArg instanceof InferenceReferenceExpression && ((InferenceReferenceExpression) idpArg).getVariable() != null;
        if (!(visitor.compare(idpArg, left, type, true) && visitor.compare(idpArg, right, type, true))) {
          errorReporter.report(isNotEqualError ? new NotEqualExpressionsError(getExpressionPrettifier(), left, right, expr) : new TypeMismatchError(equality, result.type, expr));
          return null;
        }
        if (left instanceof ArrayExpression) {
          Expression elementsType = ((ArrayExpression) left).getElementsType();
          if (elementsType instanceof InferenceReferenceExpression && ((InferenceReferenceExpression) elementsType).getVariable() != null) {
            type = type.normalize(NormalizationMode.WHNF);
            if (type instanceof ClassCallExpression) {
              myEquations.addEquation(elementsType, FieldCallExpression.make(Prelude.ARRAY_ELEMENTS_TYPE, right), type, CMP.EQ, expr, ((InferenceReferenceExpression) elementsType).getVariable(), right.getStuckInferenceVariable());
            }
          }
        } else if (right instanceof ArrayExpression) {
          Expression elementsType = ((ArrayExpression) right).getElementsType();
          if (elementsType instanceof InferenceReferenceExpression && ((InferenceReferenceExpression) elementsType).getVariable() != null) {
            type = type.normalize(NormalizationMode.WHNF);
            if (type instanceof ClassCallExpression) {
              myEquations.addEquation(elementsType, FieldCallExpression.make(Prelude.ARRAY_ELEMENTS_TYPE, left), type, CMP.EQ, expr, ((InferenceReferenceExpression) elementsType).getVariable(), left.getStuckInferenceVariable());
            }
          }
        }
        return result;
      }
    }

    boolean actualIsType = result.type instanceof FunCallExpression && ((FunCallExpression) result.type).getDefinition().getKind() == CoreFunctionDefinition.Kind.TYPE;
    boolean expectedIsType = expectedType instanceof FunCallExpression && ((FunCallExpression) expectedType).getDefinition().getKind() == CoreFunctionDefinition.Kind.TYPE;
    if (!(actualIsType && expectedIsType && ((FunCallExpression) result.type).getDefinition() == ((FunCallExpression) expectedType).getDefinition())) {
      if (actualIsType && expectedType.getStuckInferenceVariable() == null) {
        TypecheckingResult coerceResult = coerceFromType(result);
        if (coerceResult != null) {
          result.expression = coerceResult.expression;
          result.type = coerceResult.type.normalize(NormalizationMode.WHNF);
          TypecheckingResult result2 = (TypecheckingResult) myArgsInference.inferTail(result, expectedType, expr);
          if (result2 == null) return null;
          result.expression = result2.expression;
          result.type = result2.type;
        }
      }
      if (expectedIsType && result.type.getStuckInferenceVariable() == null) {
        Pair<TypecheckingResult, Boolean> coerceResult = coerceToType(expectedType, argType -> {
          if (!CompareVisitor.compare(myEquations, CMP.LE, result.type, argType, Type.OMEGA, expr)) {
            if (!result.type.reportIfError(errorReporter, expr)) {
              errorReporter.report(new TypeMismatchError(argType, result.type, expr));
            }
            return new Pair<>(null, false);
          }
          return new Pair<>(result.expression, true);
        });
        if (coerceResult != null) return coerceResult.proj1;
      }
    }

    if (result.type instanceof ClassCallExpression actualClassCall && expectedType instanceof ClassCallExpression expectedClassCall) {
      if (actualClassCall.getDefinition().isSubClassOf(expectedClassCall.getDefinition()) && actualClassCall.getDefinition() != Prelude.DEP_ARRAY) {
        boolean replace = false;
        for (ClassField field : expectedClassCall.getImplementedHere().keySet()) {
          if (!actualClassCall.isImplemented(field)) {
            replace = true;
            break;
          }
        }

        if (replace) {
          if (!actualClassCall.getImplementedHere().isEmpty()) {
            actualClassCall = new ClassCallExpression(actualClassCall.getDefinition(), actualClassCall.getLevels(), Collections.emptyMap(), actualClassCall.getSort(), actualClassCall.getUniverseKind());
          }
          result.expression = new NewExpression(result.expression, actualClassCall);
          result.type = result.expression.getType();
          return checkResultExpr(expectedClassCall, result, expr);
        }
      }
    }

    if (expectedType instanceof ClassCallExpression && ((ClassCallExpression) expectedType).getDefinition() == Prelude.DEP_ARRAY && result.type instanceof PiExpression piExpr) {
      Expression dom = piExpr.getParameters().getTypeExpr().normalize(NormalizationMode.WHNF);
      if (dom instanceof DataCallExpression && ((DataCallExpression) dom).getDefinition() == Prelude.FIN || dom.getStuckInferenceVariable() != null) {
        ClassCallExpression classCall = (ClassCallExpression) expectedType;
        Expression length = classCall.getClosedImplementation(Prelude.ARRAY_LENGTH);
        if (length == null && dom instanceof DataCallExpression) {
          length = ((DataCallExpression) dom).getDefCallArguments().get(0);
        }
        if (length != null) {
          Map<ClassField, Expression> impls = new LinkedHashMap<>();
          ClassCallExpression resultClassCall = new ClassCallExpression(Prelude.DEP_ARRAY, classCall.getLevels(), impls, Sort.PROP, UniverseKind.NO_UNIVERSES);
          Expression elementsType = piExpr.getParameters().getNext().hasNext() ? new LamExpression(piExpr.getResultSort(), DependentLink.Helper.take(piExpr.getParameters(), 1), new PiExpression(piExpr.getResultSort(), piExpr.getParameters().getNext(), piExpr.getCodomain())) : new LamExpression(piExpr.getResultSort(), piExpr.getParameters(), piExpr.getCodomain());
          impls.put(Prelude.ARRAY_LENGTH, length);
          impls.put(Prelude.ARRAY_ELEMENTS_TYPE, elementsType);
          impls.put(Prelude.ARRAY_AT, result.expression);
          return checkResultExpr(expectedType, new TypecheckingResult(new NewExpression(null, resultClassCall), resultClassCall), expr);
        }
      }
    } else if (expectedType instanceof DataCallExpression && ((DataCallExpression) expectedType).getDefinition() == Prelude.PATH && result.type instanceof PiExpression) {
      int n1 = 0;
      Expression actualType = result.type;
      while (actualType instanceof PiExpression && ((PiExpression) actualType).getParameters().isExplicit()) {
        n1 += DependentLink.Helper.size(((PiExpression) actualType).getParameters());
        actualType = ((PiExpression) actualType).getCodomain().normalize(NormalizationMode.WHNF);
      }

      int n2 = 0;
      Expression eType = expectedType;
      while (eType instanceof DataCallExpression && ((DataCallExpression) eType).getDefinition() == Prelude.PATH) {
        n2++;
        eType = AppExpression.make(((DataCallExpression) eType).getDefCallArguments().get(0), new ReferenceExpression(new TypedBinding("i", ExpressionFactory.Interval())), true).normalize(NormalizationMode.WHNF);
      }

      int n = Math.min(n1, n2);
      if (n > 0) {
        List<Referable> refs = new ArrayList<>(n - 1);
        for (int i = 1; i < n; i++) {
          refs.add(new GeneratedLocalReferable("i" + i));
        }
        Concrete.Expression newExpr = new Concrete.ReferenceExpression(expr.getData(), new CoreReferable(null, result));
        if (!refs.isEmpty()) {
          List<Concrete.Argument> args = new ArrayList<>(refs.size());
          for (Referable ref : refs) {
            args.add(new Concrete.Argument(new Concrete.ReferenceExpression(expr.getData(), ref), true));
          }
          newExpr = Concrete.AppExpression.make(expr.getData(), newExpr, args);
        }
        Concrete.Expression pathRef = new Concrete.ReferenceExpression(expr.getData(), Prelude.PATH_CON.getRef());
        newExpr = Concrete.AppExpression.make(expr.getData(), pathRef, newExpr, true);
        for (int i = refs.size() - 1; i >= 0; i--) {
          newExpr = Concrete.AppExpression.make(expr.getData(), pathRef, new Concrete.LamExpression(expr.getData(), Collections.singletonList(new Concrete.NameParameter(expr.getData(), true, refs.get(i))), newExpr), true);
        }
        return checkExpr(newExpr, expectedType);
      }
    } else if (expectedType instanceof PiExpression && result.type instanceof DataCallExpression && ((DataCallExpression) result.type).getDefinition() == Prelude.PATH) {
      return checkExpr(Concrete.AppExpression.make(expr.getData(), new Concrete.ReferenceExpression(expr.getData(), Prelude.AT.getRef()), new Concrete.ReferenceExpression(expr.getData(), new CoreReferable(null, result)), true), expectedType);
    }

    if (result.type instanceof DataCallExpression && ((DataCallExpression) result.type).getDefinition() == Prelude.FIN && expectedType instanceof DataCallExpression && ((DataCallExpression) expectedType).getDefinition() == Prelude.INT) {
      result.expression = Pos(result.expression);
      result.type = Int();
      return checkResultExpr(expectedType, result, expr);
    }

    TypecheckingResult coercedResult = CoerceData.coerce(result, expectedType, expr, this);
    if (coercedResult != null) {
      return (TypecheckingResult) myArgsInference.inferTail(coercedResult, expectedType, expr);
    }

    return isOmega ? result : checkResultExpr(expectedType, result, expr);
  }

  private TypecheckingResult checkResultExpr(Expression expectedType, TypecheckingResult result, Concrete.Expression expr) {
    CompareVisitor visitor = new CompareVisitor(myEquations, CMP.LE, expr);
    if (visitor.normalizedCompare(result.type, expectedType, Type.OMEGA, false)) {
      result.expression = OfTypeExpression.make(result.expression, result.type, expectedType);
      return result;
    }

    if (!result.type.reportIfError(errorReporter, expr)) {
      CompareVisitor.Result compareResult = visitor.getResult();
      errorReporter.report(compareResult == null ? new TypeMismatchError(expectedType, result.type, expr) : new TypeMismatchWithSubexprError(compareResult, expr));
    }
    return null;
  }

  public boolean checkCoerceResult(Expression expectedType, TypecheckingResult result, Concrete.SourceNode marker, boolean strict) {
    boolean isOmega = expectedType instanceof Type && ((Type) expectedType).isOmega();
    boolean ok = isOmega && result.type.isInstance(UniverseExpression.class);
    CompareVisitor.Result compareResult = null;
    if (!ok && expectedType != null && !isOmega) {
      CompareVisitor visitor = new CompareVisitor(strict ? new LevelEquationsWrapper(myEquations) : myEquations, CMP.LE, marker);
      FieldCallExpression actualType = result.type.cast(FieldCallExpression.class);
      if (actualType != null && expectedType instanceof FieldCallExpression && actualType.getDefinition() == ((FieldCallExpression) expectedType).getDefinition() && (actualType.getArgument().getUnderlyingExpression() instanceof InferenceReferenceExpression || ((FieldCallExpression) expectedType).getArgument().getUnderlyingExpression() instanceof InferenceReferenceExpression)) {
        ok = visitor.normalizedCompare(result.type, expectedType, Type.OMEGA, false);
      } else {
        ok = visitor.normalizedCompare(result.type.normalize(NormalizationMode.WHNF), expectedType, Type.OMEGA, false);
      }
      compareResult = visitor.getResult();
    }
    if (ok) {
      if (!strict && !isOmega) {
        result.expression = OfTypeExpression.make(result.expression, result.type, expectedType);
      }
      return true;
    }

    if (!strict && !result.type.reportIfError(errorReporter, marker)) {
      errorReporter.report(compareResult == null ? new TypeMismatchError(expectedType, result.type, marker) : new TypeMismatchWithSubexprError(compareResult, marker));
    }

    return false;
  }

  private TypecheckingResult tResultToResult(Expression expectedType, TResult result, Concrete.Expression expr) {
    if (result != null) {
      result = myArgsInference.inferTail(result, expectedType, expr);
    }
    return result == null ? null : checkResult(expectedType, result.toResult(this), expr);
  }

  @Nullable
  @Override
  public TypecheckingResult typecheck(@NotNull ConcreteExpression expression, @Nullable CoreExpression expectedType) {
    if (!(expression instanceof Concrete.Expression && (expectedType == null || expectedType instanceof Expression))) {
      throw new IllegalArgumentException();
    }
    Concrete.Expression expr = DesugarVisitor.desugar(((Concrete.Expression) expression).accept(new ReplaceVarConcreteVisitor(context.keySet()), null), errorReporter);
    Expression type = expectedType == null ? null : (Expression) expectedType;
    TypecheckingResult result = checkExpr(expr, type);
    if (result == null || result.expression.isError()) {
      if (result == null) {
        result = new TypecheckingResult(null, type == null || type == Type.OMEGA ? new ErrorExpression() : type);
      }
      result.expression = new ErrorWithConcreteExpression(expr);
    }
    return result;
  }

  @Override
  public @Nullable TypedExpression typecheckType(@NotNull ConcreteExpression expression) {
    return typecheck(expression, Type.OMEGA);
  }

  @Override
  public @Nullable Pair<AbstractedExpression,TypedExpression> typecheckAbstracted(@NotNull ConcreteExpression expression, @Nullable CoreExpression expectedType, int abstracted, @Nullable Function<TypedExpression,TypedExpression> transform) {
    TypecheckingResult result = typecheck(expression, expectedType);
    if (result == null) return null;

    List<Binding> bindings = new ArrayList<>(abstracted);
    if (!context.isEmpty() && abstracted > 0) {
      List<Binding> allBindings = new ArrayList<>(context.values());
      for (int i = allBindings.size() - 1; i >= 0 && bindings.size() < abstracted; i--) {
        if (!(allBindings.get(i) instanceof EvaluatingBinding)) {
          bindings.add(allBindings.get(i));
        }
      }
      Collections.reverse(bindings);
    }

    if (transform != null) {
      TypedExpression typed = transform.apply(result);
      if (typed == null) {
        return null;
      }
      if (!(typed instanceof TypecheckingResult)) {
        throw new IllegalArgumentException();
      }
      result = (TypecheckingResult) typed;
    }

    return new Pair<>(AbstractedExpressionImpl.make(bindings, result.expression), result);
  }

  @Override
  public @Nullable TypedExpression coerce(@NotNull TypedExpression expr, @NotNull CoreExpression expectedType, @NotNull ConcreteSourceNode marker) {
    if (!(expr instanceof TypecheckingResult result && expectedType instanceof Expression && marker instanceof Concrete.SourceNode)) {
      throw new IllegalArgumentException();
    }
    result.type = result.type.normalize(NormalizationMode.WHNF);
    return CoerceData.coerce(result, ((Expression) expectedType).normalize(NormalizationMode.WHNF), (Concrete.SourceNode) marker, this);
  }

  @Override
  public @Nullable TypedExpression coerceToType(@NotNull TypedExpression expr, @NotNull ConcreteSourceNode marker) {
    return coerce(expr, Type.OMEGA, marker);
  }

  @Nullable
  @Override
  public TypecheckingResult check(@NotNull UncheckedExpression expression, @NotNull ConcreteSourceNode sourceNode) {
    if (!(sourceNode instanceof Concrete.SourceNode)) {
      throw new IllegalArgumentException();
    }

    Expression expr = UncheckedExpressionImpl.extract(expression);
    try {
      return new TypecheckingResult(expr, expr.accept(new CoreExpressionChecker(null, myEquations, (Concrete.SourceNode) sourceNode), null));
    } catch (CoreException e) {
      errorReporter.report(e.error);
      return null;
    }
  }

  @Override
  public @Nullable TypecheckingResult replaceType(@NotNull TypedExpression typedExpression, @NotNull CoreExpression newType, @Nullable ConcreteSourceNode marker, boolean unfoldType) {
    if (!(newType instanceof Expression && marker instanceof Concrete.SourceNode)) {
      throw new IllegalArgumentException();
    }
    TypecheckingResult result = TypecheckingResult.fromChecked(typedExpression);
    Expression expr = result.expression;
    Expression type = result.type.normalize(NormalizationMode.WHNF);
    if (unfoldType) {
      while (type instanceof FunCallExpression && ((FunCallExpression) type).getDefinition().getKind() == CoreFunctionDefinition.Kind.TYPE) {
        FunctionDefinition func = ((FunCallExpression) type).getDefinition();
        Expression evalType = ((FunCallExpression) type).evaluate();
        if (evalType == null) break;
        expr = TypeDestructorExpression.make(func, expr);
        type = evalType.normalize(NormalizationMode.WHNF);
      }
    }
    return type.isError() ? new TypecheckingResult(expr, type) : CompareVisitor.compare(myEquations, CMP.LE, type, (Expression) newType, Type.OMEGA, (Concrete.SourceNode) marker) ? new TypecheckingResult(expr, (Expression) newType) : null;
  }

  @Override
  public @Nullable DependentLink typecheckParameters(@NotNull Collection<? extends ConcreteParameter> parameters) {
    return visitParameters(parameters, null, null);
  }

  @Override
  public @NotNull Concrete.Pattern desugarNumberPattern(@NotNull ConcreteNumberPattern pattern) {
    if (!(pattern instanceof Concrete.NumberPattern)) {
      throw new IllegalArgumentException();
    }
    return DesugarVisitor.desugarNumberPattern((Concrete.NumberPattern) pattern, errorReporter);
  }

  @Override
  public @Nullable List<CoreExpressionPattern> typecheckPatterns(@NotNull Collection<? extends ConcretePattern> patterns, @NotNull CoreParameter parameters, @NotNull ConcreteSourceNode marker) {
    if (!(parameters instanceof DependentLink)) {
      throw new IllegalArgumentException();
    }
    List<Concrete.Pattern> patterns1 = new ArrayList<>(patterns.size());
    for (ConcretePattern pattern : patterns) {
      if (!(pattern instanceof Concrete.Pattern)) {
        throw new IllegalArgumentException();
      }
      patterns1.add((Concrete.Pattern) pattern);
    }
    try (var ignored = new Utils.RefContextSaver(context, myLocalPrettifier)) {
      PatternTypechecking.Result result = new PatternTypechecking(PatternTypechecking.Mode.CASE, this, false, null, Collections.emptyList()).typecheckPatterns(patterns1, null, (DependentLink) parameters, new ExprSubstitution(), new ExprSubstitution(), marker);
      //noinspection unchecked
      return result == null ? null : (List<CoreExpressionPattern>) (List<?>) result.getPatterns();
    }
  }

  private static class ListParametersProvider implements ParametersProvider {
    private DependentLink parameter;
    private SingleDependentLink single = EmptyDependentLink.getInstance();
    private final ExprSubstitution substitution = new ExprSubstitution();

    ListParametersProvider(DependentLink parameter) {
      this.parameter = parameter;
    }

    @Override
    public @Nullable SingleDependentLink nextParameter() {
      if (!single.hasNext()) {
        if (!parameter.hasNext()) {
          return null;
        }

        List<String> names = new ArrayList<>();
        DependentLink link1 = parameter;
        parameter = parameter.getNextTyped(names);
        single = ExpressionFactory.singleParams(parameter.isExplicit(), names, parameter.getType().subst(new SubstVisitor(substitution, LevelSubstitution.EMPTY)));
        for (DependentLink link2 = single; link2.hasNext(); link1 = link1.getNext(), link2 = link2.getNext()) {
          substitution.add(link1, new ReferenceExpression(link2));
        }
        parameter = parameter.getNext();
      }

      SingleDependentLink result = single;
      single = single.getNext();
      return result;
    }

    @Override
    public @Nullable <T> Pair<TypecheckingResult, T> coerce(Function<ParametersProvider, Pair<Expression, T>> checker) {
      return null;
    }

    @Override
    public @Nullable Expression getType() {
      return null;
    }

    @Override
    public void subst(DependentLink param, Expression expr) {
      substitution.subst(new ExprSubstitution(param, expr));
    }
  }

  @Override
  public @Nullable TypedExpression typecheckLambda(@NotNull ConcreteLamExpression expr, @NotNull CoreParameter parameters) {
    if (!(parameters instanceof DependentLink && expr instanceof Concrete.LamExpression lamExpr)) {
      throw new IllegalArgumentException();
    }

    try (var ignored = new Utils.RefContextSaver(context, myLocalPrettifier)) {
      return visitLam(lamExpr.getParameters(), lamExpr, new ListParametersProvider((DependentLink) parameters));
    }
  }

  @Override
  public @NotNull CoreParameterBuilder newCoreParameterBuilder() {
    return new CoreParameterBuilderImpl(this);
  }

  private boolean checkSubstExpr(Expression expr, Collection<? extends CoreBinding> bindings, boolean alwaysCheckBindings) {
    Set<CoreBinding> bindingsSet = new HashSet<>(bindings);

    Set<Binding> freeBindings = FreeVariablesCollector.getFreeVariables(expr);
    int size = freeBindings.size();
    //noinspection SuspiciousMethodCalls
    freeBindings.removeAll(bindings);
    if (!alwaysCheckBindings && freeBindings.size() == size) {
      return true;
    }
    for (Binding binding : freeBindings) {
      if (binding.getTypeExpr().findFreeBindings(bindingsSet) != null) {
        throw new IllegalArgumentException("Invalid substitution");
      }
    }

    for (CoreBinding binding : bindings) {
      bindingsSet.remove(binding);
      if (binding.getTypeExpr().findFreeBindings(bindingsSet) != null) {
        throw new IllegalArgumentException("Invalid substitution");
      }
    }

    return freeBindings.size() == size;
  }

  @Override
  public @Nullable Expression substitute(@NotNull CoreExpression expression, @NotNull LevelSubstitution levelSubst, @NotNull List<SubstitutionPair> substPairs) {
    if (!(expression instanceof Expression)) throw new IllegalArgumentException();
    if (substPairs.isEmpty()) {
      return levelSubst.isEmpty() ? (Expression) expression : ((Expression) expression).subst(levelSubst);
    }

    List<CoreBinding> substVars = new ArrayList<>(substPairs.size());
    for (SubstitutionPair pair : substPairs) {
      substVars.add(pair.binding);
    }
    if (checkSubstExpr((Expression) expression, substVars, false)) {
      return levelSubst.isEmpty() ? (Expression) expression : ((Expression) expression).subst(levelSubst);
    }

    ExprSubstitution substitution = new ExprSubstitution();
    SubstVisitor substVisitor = new SubstVisitor(substitution, levelSubst);
    for (SubstitutionPair pair : substPairs) {
      if (!(pair.binding instanceof Binding)) {
        throw new IllegalArgumentException();
      }
      Expression type = ((Binding) pair.binding).getTypeExpr();
      TypecheckingResult result = typecheck(pair.expression, substVisitor.isEmpty() ? type : type.accept(substVisitor, null));
      if (result == null) return null;
      substitution.add((Binding) pair.binding, result.expression);
    }

    return ((Expression) expression).accept(substVisitor, null);
  }

  @Override
  public @Nullable AbstractedExpression substituteAbstractedExpression(@NotNull AbstractedExpression expression, @NotNull LevelSubstitution levelSubst, @NotNull List<? extends ConcreteExpression> arguments, ConcreteSourceNode marker) {
    if (arguments.isEmpty()) {
      return expression;
    }

    Set<Binding> skipped = new HashSet<>();
    int i = 0;
    ExprSubstitution substitution = new ExprSubstitution();
    SubstVisitor substVisitor = new SubstVisitor(substitution, levelSubst);
    while (i < arguments.size()) {
      List<? extends Binding> bindings;
      int drop = 0;
      if (expression instanceof AbstractedExpressionImpl) {
        bindings = ((AbstractedExpressionImpl) expression).getParameters();
      } else if (expression instanceof AbstractedDependentLinkType) {
        bindings = DependentLink.Helper.toList(((AbstractedDependentLinkType) expression).getParameters());
        drop = arguments.size() - i;
        if (drop > ((AbstractedDependentLinkType) expression).getSize()) {
          throw new IllegalArgumentException();
        }
      } else {
        throw new IllegalArgumentException();
      }

      int j = 0;
      for (; i < arguments.size(); i++, j++) {
        Binding binding = bindings.get(j);
        if (arguments.get(i) == null) {
          skipped.add(binding);
        } else {
          TypecheckingResult arg = typecheck(arguments.get(i), binding.getTypeExpr().accept(substVisitor, null));
          if (arg == null || arg.expression.reportIfError(errorReporter, arguments.get(i))) {
            return null;
          }
          substitution.add(binding, arg.expression);
        }
      }

      if (expression instanceof AbstractedDependentLinkType abs) {
        expression = AbstractedDependentLinkType.make(DependentLink.Helper.get(abs.getParameters(), drop), abs.getSize() - drop);
        break;
      }

      AbstractedExpressionImpl abs = (AbstractedExpressionImpl) expression;
      if (j < bindings.size()) {
        expression = AbstractedExpressionImpl.make(bindings.subList(j, bindings.size()), abs.getExpression());
        break;
      }
      expression = abs.getExpression();
    }

    if (expression.findFreeBinding(skipped) != null) {
      if (marker == null) {
        throw new IllegalArgumentException();
      }
      errorReporter.report(new TypecheckingError("Cannot perform substitution", marker));
      return null;
    }

    return AbstractedExpressionImpl.subst(expression, substVisitor);
  }

  @Override
  public @NotNull CoreExpression makeLambda(@NotNull List<? extends CoreParameter> parameters, @NotNull CoreExpression body, @NotNull ConcreteSourceNode marker) {
    if (parameters.isEmpty()) return body;
    if (!(body instanceof Expression && marker instanceof Concrete.SourceNode)) {
      throw new IllegalArgumentException();
    }
    for (CoreParameter parameter : parameters) {
      if (!(parameter instanceof DependentLink)) throw new IllegalArgumentException();
    }
    //noinspection unchecked
    checkSubstExpr((Expression) body, (Collection<? extends CoreBinding>) parameters, true);

    Sort sort = getSortOfType(((Expression) body).computeType(), (Concrete.SourceNode) marker);
    if (parameters.size() == 1 && parameters.get(0) instanceof SingleDependentLink param) {
      return new LamExpression(PiExpression.generateUpperBound(param.getType().getSortOfType(), sort, myEquations, (Concrete.SourceNode) marker), param, (Expression) body);
    }

    List<Pair<SingleDependentLink,Sort>> params = new ArrayList<>();
    ExprSubstitution substitution = new ExprSubstitution();
    for (CoreParameter parameter : parameters) {
      DependentLink param = (DependentLink) parameter;
      sort = PiExpression.generateUpperBound(param.getType().getSortOfType(), sort, myEquations, (Concrete.SourceNode) marker);
      params.add(new Pair<>(new TypedSingleDependentLink(param.isExplicit(), param.getName(), param.getType()), sort));
    }

    Expression result = ((Expression) body).subst(substitution);
    for (int i = params.size() - 1; i >= 0; i--) {
      result = new LamExpression(params.get(i).proj2, params.get(i).proj1, result);
    }
    return result;
  }

  @Override
  public @Nullable CoreParameter substituteParameters(@NotNull CoreParameter parameters, @NotNull LevelSubstitution levelSubst, @NotNull List<? extends ConcreteExpression> arguments) {
    boolean allNull = true;
    for (ConcreteExpression argument : arguments) {
      if (argument != null) {
        allNull = false;
        break;
      }
    }
    if (allNull) {
      return parameters;
    }

    if (!(parameters instanceof DependentLink)) {
      throw new IllegalArgumentException();
    }

    List<DependentLink> links = DependentLink.Helper.toList((DependentLink) parameters);
    if (arguments.size() > links.size()) {
      throw new IllegalArgumentException();
    }

    ExprSubstitution substitution = new ExprSubstitution();
    SubstVisitor substVisitor = new SubstVisitor(substitution, levelSubst);
    LinkList list = new LinkList();
    for (int i = 0; i < links.size();) {
      int typedIndex = i;
      while (links.get(typedIndex) instanceof UntypedDependentLink) {
        typedIndex++;
      }
      int last = typedIndex;
      for (; last >= i; last--) {
        if (last >= arguments.size() || arguments.get(last) == null) {
          break;
        }
      }

      Type type = links.get(typedIndex).getType().subst(substVisitor);
      for (; i <= typedIndex; i++) {
        if (i >= arguments.size() || arguments.get(i) == null) {
          DependentLink link = i < last ? new UntypedDependentLink(links.get(i).getName()) : new TypedDependentLink(links.get(typedIndex).isExplicit(), links.get(i).getName(), type, links.get(typedIndex).isHidden(), EmptyDependentLink.getInstance());
          substitution.add(links.get(i), new ReferenceExpression(link));
          list.append(link);
        } else {
          TypecheckingResult result = typecheck(arguments.get(i), type.getExpr());
          if (result == null) {
            return null;
          }
          substitution.add(links.get(i), result.expression);
        }
      }
    }
    return list.getFirst();
  }

  public TypecheckingResult checkExpr(Concrete.Expression expr, Expression expectedType) {
    return expr.accept(this, expectedType);
  }

  public TypecheckingResult finalCheckExpr(Concrete.Expression expr, Expression expectedType) {
    return finalize(checkExpr(expr, expectedType), expr, false);
  }

  public void invokeDeferredMetas(InPlaceLevelSubstVisitor substVisitor, StripVisitor stripVisitor, boolean afterLevels) {
    List<DeferredMeta> deferredMetas = afterLevels ? myDeferredMetasAfterLevels : myDeferredMetasBeforeSolver;
    // Indexed loop is required since deferredMetas can be modified during the loop
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < deferredMetas.size(); i++) {
      DeferredMeta deferredMeta = deferredMetas.get(i);
      Expression type = deferredMeta.contextData.getExpectedType();
      if (substVisitor != null && !substVisitor.isEmpty()) {
        type.accept(substVisitor, null);
      }
      if (stripVisitor != null) {
        type = type.accept(stripVisitor, null);
        deferredMeta.contextData.setExpectedType(type.accept(new StripVisitor(), null));

        TypedDependentLink lastTyped = null;
        for (Binding binding : deferredMeta.context.values()) {
          if (binding instanceof UntypedDependentLink) {
            TypedDependentLink typed = ((UntypedDependentLink) binding).getNextTyped(null);
            if (typed != lastTyped) {
              lastTyped = typed;
              typed.subst(substVisitor);
              typed.strip(stripVisitor);
            }
          } else {
            if (binding != lastTyped) {
              binding.subst(substVisitor);
              binding.strip(stripVisitor);
            }
            lastTyped = null;
          }
        }
      }

      CheckTypeVisitor checkTypeVisitor;
      MyErrorReporter originalErrorReporter = errorReporter;
      Map<Referable, Binding> originalContext = context;
      LocalExpressionPrettifier originalLocalPrettifier = myLocalPrettifier;
      if (afterLevels) {
        for (Binding binding : deferredMeta.context.values()) {
          Type bindingType = binding.getType();
          if (bindingType != null) bindingType.subst(substVisitor);
        }
        checkTypeVisitor = copy(deferredMeta.context, deferredMeta.localPrettifier, deferredMeta.errorReporter, null, myArendExtension, this);
        checkTypeVisitor.setInstancePool(myInstancePool.copy(checkTypeVisitor));
        checkTypeVisitor.setLevelContext(myLevelContext);
      } else {
        checkTypeVisitor = this;
        errorReporter = deferredMeta.errorReporter;
        context = deferredMeta.context;
        myLocalPrettifier = deferredMeta.localPrettifier;
      }

      int numberOfErrors = checkTypeVisitor.getNumberOfErrors();
      Concrete.ReferenceExpression refExpr = deferredMeta.contextData.getReferenceExpression();
      Concrete.Expression marker = deferredMeta.contextData.getMarker();
      TypecheckingResult result = checkTypeVisitor.invokeMeta(deferredMeta.meta, deferredMeta.contextData);
      fixCheckedExpression(result, refExpr == null ? null : refExpr.getReferent(), marker);
      if (result != null) {
        result = checkTypeVisitor.checkResult(type, result, marker);
        if (afterLevels) {
          result = checkTypeVisitor.finalize(result, marker, false);
        }
      }
      errorReporter = originalErrorReporter;
      context = originalContext;
      myLocalPrettifier = originalLocalPrettifier;
      if (result == null && checkTypeVisitor.getNumberOfErrors() == numberOfErrors) {
        deferredMeta.errorReporter.report(new TypecheckingError(refExpr == null ? "Cannot check deferred expression" : "Meta '" + refExpr.getReferent().getRefName() + "' failed", marker));
      }
      deferredMeta.inferenceVar.solve(this, result == null ? new ErrorExpression() : result.expression);
    }
    deferredMetas.clear();
    if (!afterLevels) {
      myEquations.solveEquations();
    }
  }

  public TypecheckingResult finalize(TypecheckingResult result, Concrete.SourceNode sourceNode, boolean propIfPossible) {
    if (result == null) {
      return null;
    }

    invokeDeferredMetas(null, null, false);
    LevelEquationsSolver levelSolver = myEquations.makeLevelEquationsSolver();
    if (propIfPossible) {
      Sort sort = result.type.getSortOfType();
      if (sort != null) {
        levelSolver.addPropEquationIfPossible(sort.getHLevel());
      }
    }
    LevelSubstitution levelSubstitution = levelSolver.solveLevels();
    myEquations.finalizeEquations(levelSubstitution, sourceNode);
    InPlaceLevelSubstVisitor substVisitor = new InPlaceLevelSubstVisitor(levelSubstitution);
    if (!substVisitor.isEmpty()) {
      if (result.expression != null) {
        result.expression.accept(substVisitor, null);
      }
      result.type.accept(substVisitor, null);
    }

    InferenceVariableSolveVisitor solveVisitor = new InferenceVariableSolveVisitor(this);
    if (result.expression != null) {
      result.expression.accept(solveVisitor, null);
    }
    result.type.accept(solveVisitor, null);

    ErrorReporterCounter counter = new ErrorReporterCounter(GeneralError.Level.ERROR, errorReporter);
    StripVisitor stripVisitor = new StripVisitor(counter, false);
    invokeDeferredMetas(substVisitor, stripVisitor, true);
    stripVisitor.setEvaluateBindings(true);
    if (result.expression != null) {
      result.expression = result.expression.accept(stripVisitor, null);
    }
    stripVisitor.setErrorReporter(counter.getErrorsNumber() == 0 ? errorReporter : DummyErrorReporter.INSTANCE);
    result.type = result.type.accept(stripVisitor, null);
    return result;
  }

  public Type checkType(Concrete.Expression expr, Expression expectedType) {
    if (expr == null) {
      assert false;
      errorReporter.report(new LocalError(GeneralError.Level.ERROR, "Incomplete expression"));
      return null;
    }

    TypecheckingResult result;
    Expression expectedType1 = expectedType;
    boolean isOmega = expectedType instanceof Type && ((Type) expectedType).isOmega();
    if (!isOmega) {
      expectedType = expectedType.normalize(NormalizationMode.WHNF);
      if (expectedType.getStuckInferenceVariable() != null) {
        expectedType1 = Type.OMEGA;
      }
    }

    result = expr.accept(this, expectedType1);
    if (result != null && expectedType1 != expectedType) {
      result.type = result.type.normalize(NormalizationMode.WHNF);
      result = checkResultExpr(expectedType, result, expr);
    }
    if (result == null) {
      return null;
    }
    if (result.expression instanceof Type) {
      return (Type) result.expression;
    }

    Expression type = result.type.normalize(NormalizationMode.WHNF);
    UniverseExpression universe = type.cast(UniverseExpression.class);
    if (universe == null) {
      Expression stuck = type.getStuckExpression();
      if (stuck == null || !stuck.isInstance(InferenceReferenceExpression.class) && !stuck.reportIfError(errorReporter, expr)) {
        if (stuck == null || !stuck.isError()) {
          errorReporter.report(new TypeMismatchError(DocFactory.text("\\Type"), type, expr));
        }
        return null;
      }

      universe = new UniverseExpression(Sort.generateInferVars(myEquations, false, expr));
      InferenceVariable infVar = stuck.getInferenceVariable();
      if (infVar != null) {
        myEquations.addEquation(type, universe, Type.OMEGA, CMP.LE, expr, infVar, null);
      }
    }

    return new TypeExpression(result.expression, universe.getSort());
  }

  public Type finalCheckType(Concrete.Expression expr, Expression expectedType, boolean propIfPossible) {
    Type result = checkType(expr, expectedType);
    if (result == null) return null;
    invokeDeferredMetas(null, null, false);
    LevelEquationsSolver levelSolver = myEquations.makeLevelEquationsSolver();
    if (propIfPossible) {
      Sort sort = result.getSortOfType();
      if (sort != null) {
        levelSolver.addPropEquationIfPossible(sort.getHLevel());
      }
    }
    LevelSubstitution levelSubstitution = levelSolver.solveLevels();
    myEquations.finalizeEquations(levelSubstitution, expr);
    InPlaceLevelSubstVisitor substVisitor = new InPlaceLevelSubstVisitor(levelSubstitution);
    if (!substVisitor.isEmpty()) {
      result.subst(substVisitor);
    }
    result.getExpr().accept(new InferenceVariableSolveVisitor(this), null);
    StripVisitor stripVisitor = new StripVisitor(errorReporter);
    invokeDeferredMetas(substVisitor, stripVisitor, true);
    return result.strip(stripVisitor);
  }

  public TypecheckingResult checkArgument(Concrete.Expression expr, Expression expectedType, TResult result, InferenceVariable infVar) {
    Concrete.ThisExpression thisExpr = null;
    Binding binding = null;
    if (expr instanceof Concrete.ThisExpression) {
      thisExpr = (Concrete.ThisExpression) expr;
    } else if (expr instanceof Concrete.TypedExpression typedExpr) {
      if (!myClassCallBindings.isEmpty() && typedExpr.expression instanceof Concrete.ThisExpression && ((Concrete.ThisExpression) typedExpr.expression).getReferent() == null && typedExpr.type instanceof Concrete.ReferenceExpression && ((Concrete.ReferenceExpression) typedExpr.type).getReferent() instanceof TCDefReferable) {
        Definition def = ((TCDefReferable) ((Concrete.ReferenceExpression) typedExpr.type).getReferent()).getTypechecked();
        if (def instanceof ClassDefinition) {
          for (int i = myClassCallBindings.size() - 1; i >= 0; i--) {
            if (myClassCallBindings.get(i).getTypeExpr().getDefinition() == def) {
              thisExpr = (Concrete.ThisExpression) typedExpr.expression;
              binding = myClassCallBindings.get(i);
              break;
            }
          }
        }
      }
    }

    if (thisExpr != null) {
      boolean ok = result == null;
      if (!ok) {
        // if infVar != null, it is a deferred argument, so we must find its index in the list of arguments
        if (infVar != null) {
          Definition definition = null;
          List<? extends Expression> args = Collections.emptyList();
          if (result instanceof DefCallResult) {
            definition = ((DefCallResult) result).getDefinition();
            args = ((DefCallResult) result).getArguments();
          } else if (result instanceof TypecheckingResult && ((TypecheckingResult) result).expression instanceof DefCallExpression) {
            definition = ((DefCallExpression) ((TypecheckingResult) result).expression).getDefinition();
            args = ((DefCallExpression) ((TypecheckingResult) result).expression).getDefCallArguments();
          }
          int index = -1;
          for (int i = 0; i < args.size(); i++) {
            Expression arg = args.get(i);
            if (arg instanceof InferenceReferenceExpression && ((InferenceReferenceExpression) arg).getVariable() == infVar) {
              index = i;
              break;
            }
          }
          if (index != -1) {
            ok = definition.isGoodParameter(index);
          }
        } else {
          ok = result instanceof DefCallResult && ((DefCallResult) result).getDefinition().isGoodParameter(((DefCallResult) result).getArguments().size());
        }
      }
      if (ok) {
        return checkThisExpression(thisExpr, binding, expectedType, expr, 0);
      }
    }

    return checkExpr(expr, expectedType);
  }

  private TypecheckingResult checkThisExpression(Concrete.ThisExpression thisExpr, Binding binding, Expression expectedType, Concrete.Expression expr, int skipLastClassCallBindings) {
    TResult tResult;
    if (thisExpr.getReferent() != null) {
      tResult = getLocalVar(thisExpr.getReferent(), thisExpr);
    } else {
      if (myClassCallBindings.size() <= skipLastClassCallBindings) {
        return checkExpr(expr, expectedType);
      } else {
        if (binding == null) {
          binding = myClassCallBindings.get(myClassCallBindings.size() - 1 - skipLastClassCallBindings);
        }
        tResult = new TypecheckingResult(new ReferenceExpression(binding), binding.getTypeExpr());
      }
    }
    return tResultToResult(expectedType, tResult, expr);
  }

  // Classes

  @Override
  public TypecheckingResult visitClassExt(Concrete.ClassExtExpression expr, Expression expectedType) {
    Concrete.Expression baseClassExpr = expr.getBaseClassExpression();
    if (baseClassExpr instanceof Concrete.AppExpression) {
      baseClassExpr = ((Concrete.AppExpression) baseClassExpr).getFunction();
    }
    if (baseClassExpr instanceof Concrete.ReferenceExpression && ((Concrete.ReferenceExpression) baseClassExpr).getReferent() instanceof MetaReferable) {
      return checkMeta((Concrete.ReferenceExpression) baseClassExpr, expr.getBaseClassExpression() instanceof Concrete.AppExpression ? ((Concrete.AppExpression) expr.getBaseClassExpression()).getArguments() : Collections.emptyList(), expr.getCoclauses(), expectedType);
    }

    baseClassExpr = desugarClassApp(expr.getBaseClassExpression(), false, Collections.emptySet());
    if (baseClassExpr instanceof Concrete.ClassExtExpression classExt) {
      classExt.getStatements().addAll(expr.getStatements());
      expr = classExt;
      baseClassExpr = classExt.getBaseClassExpression();
    }
    TypecheckingResult typeCheckedBaseClass;
    if (baseClassExpr instanceof Concrete.ReferenceExpression) {
      Referable ref = ((Concrete.ReferenceExpression) baseClassExpr).getReferent();
      boolean withoutUniverses = true;
      if (ref instanceof TCDefReferable && ((TCDefReferable) ref).getTypechecked() instanceof ClassDefinition classDef) {
        withoutUniverses = classDef.getUniverseKind() != UniverseKind.WITH_UNIVERSES;
        if (withoutUniverses && classDef.getUniverseKind() != UniverseKind.NO_UNIVERSES) {
          Set<ClassField> implemented = new HashSet<>();
          for (Concrete.ClassFieldImpl classFieldImpl : expr.getStatements()) {
            Referable fieldRef = classFieldImpl.getImplementedField();
            if (fieldRef instanceof TCDefReferable) {
              Definition fieldDef = ((TCDefReferable) fieldRef).getTypechecked();
              if (fieldDef instanceof ClassField) {
                implemented.add((ClassField) fieldDef);
              } else if (fieldDef instanceof ClassDefinition) {
                implemented.addAll(((ClassDefinition) fieldDef).getImplementedFields());
              }
            }
          }
          for (ClassField field : classDef.getNotImplementedFields()) {
            if (field.getUniverseKind() != UniverseKind.NO_UNIVERSES && !implemented.contains(field)) {
              withoutUniverses = false;
              break;
            }
          }
        }
      }
      typeCheckedBaseClass = tResultToResult(null, visitReference((Concrete.ReferenceExpression) baseClassExpr, withoutUniverses), baseClassExpr);
    } else {
      typeCheckedBaseClass = checkExpr(baseClassExpr, null);
    }
    if (typeCheckedBaseClass == null) {
      return null;
    }

    ClassCallExpression classCall = typeCheckedBaseClass.expression.normalize(NormalizationMode.WHNF).cast(ClassCallExpression.class);
    if (classCall == null) {
      errorReporter.report(new TypecheckingError("Expected a class", expr.getBaseClassExpression()));
      return null;
    }

    return expr.getStatements().isEmpty() ? typeCheckedBaseClass : typecheckClassExt(expr.getStatements(), expectedType, classCall, null, expr, false);
  }

  public TypecheckingResult typecheckClassExt(List<? extends Concrete.ClassFieldImpl> classFieldImpls, Expression expectedType, ClassCallExpression classCallExpr, Set<ClassField> pseudoImplemented, Concrete.Expression expr, boolean useDefaults) {
    return typecheckClassExt(classFieldImpls, expectedType, null, classCallExpr, pseudoImplemented, expr, useDefaults);
  }

  private void checkImplementationCycle(FieldDFS dfs, ClassField field, Expression implementation, boolean isFunc, ClassCallExpression classCall, Concrete.SourceNode sourceNode) {
    Set<ClassField> fields = null;
    Set<ClassField> allFields = classCall.getDefinition().getAllFields();
    if (isFunc) {
      Expression body = implementation;
      List<Binding> params = new ArrayList<>();
      params.add(classCall.getThisBinding());
      while (body instanceof LamExpression lam) {
        for (SingleDependentLink param = lam.getParameters(); param.hasNext(); param = param.getNext()) {
          params.add(param);
        }
        body = lam.getBody();
      }
      if (body instanceof FunCallExpression funCall && funCall.getDefCallArguments().size() == params.size()) {
        boolean ok = true;
        for (int i = 0; i < params.size(); i++) {
          if (!(funCall.getDefCallArguments().get(i) instanceof ReferenceExpression refExpr && refExpr.getBinding() == params.get(i))) {
            ok = false;
            break;
          }
        }
        if (ok) {
          fields = FieldsCollector.getFields(funCall.getDefinition(), classCall.getThisBinding(), allFields);
        }
      }
    }
    if (fields == null) {
      fields = FieldsCollector.getFields(implementation, classCall.getThisBinding(), allFields);
    }

    List<ClassField> cycle = dfs.checkDependencies(field, fields);
    if (cycle != null) {
      errorReporter.report(new FieldCycleError(cycle, sourceNode));
    }
    classCall.getImplementedHere().put(field, cycle == null ? implementation : new ErrorExpression());
  }

  private void copyImplementationsFrom(ClassCallExpression to, ClassCallExpression from, Concrete.SourceNode sourceNode) {
    if (from.getImplementedHere().isEmpty()) {
      return;
    }

    ReferenceExpression thisExpr = new ReferenceExpression(to.getThisBinding());
    for (Map.Entry<ClassField, Expression> entry : from.getImplementedHere().entrySet()) {
      if (to.getDefinition() != from.getDefinition()) {
        ClassDefinition toOriginalClass = to.getDefinition().getOverriddenOriginalClass(entry.getKey());
        if (toOriginalClass != null && toOriginalClass != from.getDefinition().getOverriddenOriginalClass(entry.getKey())) {
          Expression fieldType = to.getDefinition().getFieldType(entry.getKey(), to.getLevels(entry.getKey().getParentClass()), new ReferenceExpression(to.getThisBinding()));
          if (!CompareVisitor.compare(myEquations, CMP.LE, entry.getValue().getType(), fieldType, Type.OMEGA, sourceNode)) {
            break;
          }
        }
      }
      to.getImplementedHere().put(entry.getKey(), entry.getValue().subst(from.getThisBinding(), thisExpr));
    }
  }

  private TypecheckingResult typecheckClassExt(List<? extends Concrete.ClassFieldImpl> classFieldImpls, Expression expectedType, Expression renewExpr, ClassCallExpression classCallExpr, Set<ClassField> pseudoImplemented, Concrete.Expression expr, boolean useDefaults) {
    ClassDefinition baseClass = classCallExpr.getDefinition();
    Map<ClassField, Expression> fieldSet = new LinkedHashMap<>();
    ClassCallExpression resultClassCall = new ClassCallExpression(baseClass, classCallExpr.getLevels(), fieldSet, Sort.PROP, baseClass.getUniverseKind());
    copyImplementationsFrom(resultClassCall, classCallExpr, expr);
    resultClassCall.updateHasUniverses();

    Set<ClassField> defined = new HashSet<>();
    List<Pair<Definition,Concrete.ClassFieldImpl>> implementations = new ArrayList<>(classFieldImpls.size());
    for (Concrete.ClassFieldImpl classFieldImpl : classFieldImpls) {
      Definition definition = referableToDefinition(classFieldImpl.getImplementedField(), classFieldImpl);
      if (definition != null) {
        boolean ok = true;
        if (definition instanceof ClassField classField) {
          if (baseClass.containsField(classField)) {
            defined.add(classField);
          } else {
            errorReporter.report(new IncorrectImplementationError(classField, baseClass, classFieldImpl));
            ok = false;
          }
        } else if (definition instanceof ClassDefinition) {
          if (baseClass.isSubClassOf((ClassDefinition) definition)) {
            defined.addAll(((ClassDefinition) definition).getNotImplementedFields());
            defined.addAll(((ClassDefinition) definition).getImplementedFields());
          } else {
            errorReporter.report(new IncorrectImplementationError((ClassDefinition) definition, baseClass, classFieldImpl));
            ok = false;
          }
        }
        if (ok) {
          implementations.add(new Pair<>(definition, classFieldImpl));
        }
      }
    }

    FieldDFS dfs = new FieldDFS(resultClassCall.getDefinition());
    if (renewExpr != null) {
      for (ClassField field : baseClass.getNotImplementedFields()) {
        if (!defined.contains(field) && !resultClassCall.isImplementedHere(field)) {
          Set<ClassField> found = FindDefCallVisitor.findDefinitions(field.getType().getCodomain(), defined);
          if (!found.isEmpty()) {
            Concrete.SourceNode sourceNode = null;
            for (Pair<Definition, Concrete.ClassFieldImpl> implementation : implementations) {
              if (implementation.proj1 instanceof ClassField && found.contains(implementation.proj1)) {
                sourceNode = implementation.proj2;
                break;
              }
            }
            if (sourceNode == null) {
              sourceNode = expr;
            }
            errorReporter.report(new FieldDependencyError(field, found, sourceNode));
            return null;
          }
          fieldSet.put(field, FieldCallExpression.make(field, renewExpr));
        }
      }
    } else if (useDefaults && !baseClass.getDefaults().isEmpty()) {
      MapDFS<ClassField> defaultDFS = new MapDFS<>(baseClass.getDefaultDependencies());
      defaultDFS.visit(defined);
      defaultDFS.visit(resultClassCall.getImplementedHere().keySet());
      Set<ClassField> notDefault = defaultDFS.getVisited();

      Map<ClassField, AbsExpression> defaults = new HashMap<>();
      DFS<ClassField, Boolean> implDfs = new DFS<>() {
        @Override
        protected Boolean forDependencies(ClassField field) {
          if (defined.contains(field) || resultClassCall.isImplemented(field)) return true;
          AbsExpression impl = baseClass.getDefault(field);
          if (impl == null || notDefault.contains(field)) return false;
          Set<ClassField> dependencies = baseClass.getDefaultImplDependencies().get(field);
          if (dependencies != null) {
            for (ClassField dependency : dependencies) {
              Boolean ok = visit(dependency);
              if (ok == null || !ok) return false;
            }
          }
          defaults.put(field, impl);
          return true;
        }

        @Override
        protected Boolean getVisitedValue(ClassField field, boolean cycle) {
          return !cycle && (defined.contains(field) || resultClassCall.isImplemented(field) || defaults.containsKey(field));
        }
      };

      for (ClassField field : baseClass.getNotImplementedFields()) {
        implDfs.visit(field);
        AbsExpression defaultImpl = defaults.get(field);
        if (defaultImpl != null && !notDefault.contains(field)) {
          Pair<AbsExpression, Boolean> pair = baseClass.getDefaultPair(field);
          checkImplementationCycle(dfs, field, defaultImpl.apply(new ReferenceExpression(resultClassCall.getThisBinding()), resultClassCall.getLevelSubstitution()), pair != null && pair.proj2, resultClassCall, expr);
        }
      }
    }

    if (!implementations.isEmpty()) {
      if (resultClassCall.getDefinition() == Prelude.DEP_ARRAY && !resultClassCall.getImplementedHere().isEmpty()) {
        for (Pair<Definition, Concrete.ClassFieldImpl> pair : implementations) {
          if (pair.proj1 instanceof ClassField) {
            resultClassCall.getImplementedHere().remove(pair.proj1);
          } else if (pair.proj1 instanceof ClassDefinition) {
            resultClassCall.getImplementedHere().keySet().removeAll(((ClassDefinition) pair.proj1).getNotImplementedFields());
            resultClassCall.getImplementedHere().keySet().removeAll(((ClassDefinition) pair.proj1).getImplementedFields());
          }
        }
      }

      Referable thisRef = addBinding(null, resultClassCall.getThisBinding());
      myClassCallBindings.add(resultClassCall.getThisBinding());
      try {
        for (Pair<Definition, Concrete.ClassFieldImpl> pair : implementations) {
          if (pair.proj1 instanceof ClassField field) {
            TypecheckingResult implResult = typecheckImplementation(field, pair.proj2.implementation, resultClassCall, !(pair.proj2 instanceof Concrete.CoClauseFunctionReference));
            if (implResult != null) {
              Expression oldImpl = null;
              if (!field.isProperty()) {
                oldImpl = resultClassCall.getAbsImplementationHere(field);
                if (oldImpl == null) {
                  AbsExpression absImpl = resultClassCall.getDefinition().getImplementation(field);
                  oldImpl = absImpl == null ? null : absImpl.getExpression().subst(resultClassCall.getLevelSubstitution());
                }
              }
              if (oldImpl != null) {
                if (!classCallExpr.isImplemented(field) || !CompareVisitor.compare(myEquations, CMP.EQ, implResult.expression, oldImpl, implResult.type, pair.proj2.implementation)) {
                  errorReporter.report(new FieldsImplementationError(true, baseClass.getReferable(), Collections.singletonList(field.getReferable()), pair.proj2));
                }
              } else if (!resultClassCall.isImplemented(field)) {
                checkImplementationCycle(dfs, field, implResult.expression, false, resultClassCall, pair.proj2.implementation);
              }
            } else if (pseudoImplemented != null) {
              pseudoImplemented.add(field);
            } else if (!resultClassCall.isImplemented(field)) {
              fieldSet.put(field, new ErrorExpression());
            }
          } else if (pair.proj1 instanceof ClassDefinition classDef) {
            TypecheckingResult result = pair.proj2.implementation instanceof Concrete.ThisExpression ? checkThisExpression((Concrete.ThisExpression) pair.proj2.implementation, null, null, pair.proj2.implementation, 1) : checkExpr(pair.proj2.implementation, null);
            if (result != null) {
              Expression type = result.type.normalize(NormalizationMode.WHNF);
              ClassCallExpression classCall = type.cast(ClassCallExpression.class);
              if (classCall == null) {
                if (!type.reportIfError(errorReporter, pair.proj2.implementation) && !type.isInstance(ErrorExpression.class)) {
                  InferenceVariable var = type instanceof InferenceReferenceExpression ? ((InferenceReferenceExpression) type).getVariable() : null;
                  errorReporter.report(var != null && !(var instanceof MetaInferenceVariable) ? var.getErrorInfer() : new TypeMismatchError(DocFactory.text("a class"), type, pair.proj2.implementation));
                }
              } else {
                if (!classCall.getDefinition().isSubClassOf(classDef)) {
                  errorReporter.report(new TypeMismatchError(new ClassCallExpression(classDef, classDef.makeMinLevels()), type, pair.proj2.implementation));
                } else {
                  if (classCall.getDefinition().getUniverseKind() != UniverseKind.NO_UNIVERSES && resultClassCall.getDefinition().getUniverseKind() != UniverseKind.NO_UNIVERSES && !resultClassCall.getLevels(classDef).compare(classCall.getLevels(classDef), CMP.EQ, myEquations, pair.proj2.implementation)) {
                    errorReporter.report(new TypeMismatchError(new ClassCallExpression(classDef, resultClassCall.getLevels(classDef)), classCall, pair.proj2.implementation));
                    return null;
                  }
                  for (ClassField field : classDef.getNotImplementedFields()) {
                    Levels fieldLevels = classCall.getLevels(field.getParentClass());
                    Expression impl = FieldCallExpression.make(field, result.expression).normalize(NormalizationMode.WHNF);
                    boolean isImplemented = resultClassCall.isImplemented(field);
                    if (isImplemented && !field.isProperty()) {
                      if (!CompareVisitor.compare(myEquations, CMP.EQ, impl, resultClassCall.getImplementation(field, new ReferenceExpression(resultClassCall.getThisBinding())), classCall.getDefinition().getFieldType(field, fieldLevels, result.expression), pair.proj2.implementation)) {
                        errorReporter.report(new FieldsImplementationError(true, baseClass.getReferable(), Collections.singletonList(field.getReferable()), pair.proj2));
                      }
                    } else {
                      if (!isImplemented) {
                        checkImplementationCycle(dfs, field, impl, false, resultClassCall, pair.proj2.implementation);

                        PiExpression overridden = baseClass.getOverriddenType(field);
                        if (overridden != null && classCall.getDefinition().getOverriddenType(field) != overridden) {
                          Expression actualFieldType = impl.getType();
                          Expression expectedFieldType = overridden.getCodomain().subst(new ExprSubstitution(overridden.getParameters(), result.expression), classCallExpr.getLevelSubstitution());
                          if (!CompareVisitor.compare(myEquations, CMP.LE, actualFieldType, expectedFieldType, Type.OMEGA, pair.proj2.implementation)) {
                            errorReporter.report(new TypeMismatchError("The type of field '" + field.getName() + "' does not match", expectedFieldType, actualFieldType, pair.proj2.implementation));
                          }
                        }
                      }
                    }
                  }
                  resultClassCall.updateHasUniverses();
                }
              }
            }
          } else {
            errorReporter.report(new WrongReferable("Expected either a field or a class", pair.proj2.getImplementedField(), pair.proj2));
          }
        }
      } finally {
        myClassCallBindings.remove(myClassCallBindings.size() - 1);
      }
      removeBinding(thisRef);
    }

    resultClassCall.fixOrderOfImplementations();
    fixClassExtSort(resultClassCall, expr);
    resultClassCall.updateHasUniverses();
    return checkResult(expectedType, new TypecheckingResult(resultClassCall, new UniverseExpression(resultClassCall.getSortOfType())), expr);
  }

  static void setCaseLevel(Concrete.Expression expr, int level, boolean setSCase) {
    while (expr instanceof Concrete.LamExpression) {
      expr = ((Concrete.LamExpression) expr).getBody();
    }
    if (expr instanceof Concrete.CaseExpression caseExpr) {
      caseExpr.level = level;
      if (setSCase) {
        caseExpr.setSCase(true);
      }
      for (Concrete.FunctionClause clause : caseExpr.getClauses()) {
        setCaseLevel(clause.getExpression(), level, setSCase);
      }
    }
  }

  private TypecheckingResult typecheckImplementation(ClassField field, Concrete.Expression implBody, ClassCallExpression fieldSetClass, boolean addImplicitLambdas) {
    Expression type = fieldSetClass.getDefinition().getFieldType(field, fieldSetClass.getLevels(field.getParentClass()), new ReferenceExpression(fieldSetClass.getThisBinding()));

    // Expression type = FieldCallExpression.make(field, fieldSetClass.getLevels(), new ReferenceExpression(fieldSetClass.getThisBinding())).getType();
    if (implBody instanceof Concrete.HoleExpression && field.getReferable().isParameterField() && !field.getReferable().isExplicitField() && field.isTypeClass() && type instanceof ClassCallExpression && !((ClassCallExpression) type).getDefinition().isRecord()) {
      TypecheckingResult result;
      ClassDefinition classDef = ((ClassCallExpression) type).getDefinition();
      RecursiveInstanceHoleExpression holeExpr = implBody instanceof RecursiveInstanceHoleExpression ? (RecursiveInstanceHoleExpression) implBody : null;
      if (classDef.getClassifyingField() == null) {
        TypecheckingResult instance = myInstancePool.findInstance(null, type, new SubclassSearchParameters(classDef), implBody, holeExpr, myDefinition);
        if (instance == null) {
          ArgInferenceError error = new RecursiveInstanceInferenceError(getExpressionPrettifier(), classDef.getReferable(), implBody, holeExpr, new Expression[0]);
          errorReporter.report(error);
          result = new TypecheckingResult(new ErrorExpression(error), type);
        } else {
          result = instance;
          if (result.type == null) {
            result.type = type;
          }
        }
      } else {
        result = new TypecheckingResult(InferenceReferenceExpression.make(new TypeClassInferenceVariable(field.getName(), type, classDef, false, false, implBody, holeExpr, myDefinition, getAllBindings()), myEquations), type);
      }
      return result;
    }

    if (field.isProperty()) {
      setCaseLevel(implBody, -1, true);
    } else if (field.getResultTypeLevel() >= -1) {
      CheckTypeVisitor.setCaseLevel(implBody, field.getResultTypeLevel(), false);
    }

    if (addImplicitLambdas) {
      implBody = addImplicitLamParams(implBody, type);
    }

    TypecheckingResult result = fieldSetClass.getDefinition().isGoodField(field) ? checkArgument(implBody, type, null, null) : checkExpr(implBody, type);
    if (result == null) {
      return null;
    }
    result.type = type;
    return result;
  }

  static Concrete.Expression addImplicitLamParams(Concrete.Expression expr, Expression type) {
    if (!(expr instanceof Concrete.LamExpression)) {
      List<SingleDependentLink> params = new ArrayList<>();
      type.getPiParameters(params, true);
      if (!params.isEmpty()) {
        List<Concrete.Parameter> lamParams = new ArrayList<>(params.size());
        for (SingleDependentLink param : params) {
          lamParams.add(new Concrete.NameParameter(expr.getData(), false, new LocalReferable(VariableRenamerFactoryImpl.INSTANCE.getNameFromBinding(param, null))));
        }
        return new Concrete.LamExpression(expr.getData(), lamParams, expr);
      }
    }
    return expr;
  }

  @Override
  public TypecheckingResult visitNew(Concrete.NewExpression expr, Expression expectedType) {
    if (expr.getExpression() instanceof Concrete.ClassExtExpression classExt) {
      Concrete.Expression baseClassExpr = classExt.getBaseClassExpression();
      if (baseClassExpr instanceof Concrete.AppExpression) {
        baseClassExpr = ((Concrete.AppExpression) baseClassExpr).getFunction();
      }
      if (baseClassExpr instanceof Concrete.ReferenceExpression && ((Concrete.ReferenceExpression) baseClassExpr).getReferent() instanceof MetaReferable && ((MetaReferable) ((Concrete.ReferenceExpression) baseClassExpr).getReferent()).getDefinition() == null) {
        return makeNew(checkMeta((Concrete.ReferenceExpression) baseClassExpr, classExt.getBaseClassExpression() instanceof Concrete.AppExpression ? ((Concrete.AppExpression) classExt.getBaseClassExpression()).getArguments() : Collections.emptyList(), classExt.getCoclauses(), null), expr, expectedType, Collections.emptySet());
      }
    }

    TypecheckingResult exprResult = null;
    Set<ClassField> pseudoImplemented = Collections.emptySet();
    Concrete.Expression classExpr = desugarClassApp(expr.getExpression() instanceof Concrete.ClassExtExpression ? ((Concrete.ClassExtExpression) expr.getExpression()).getBaseClassExpression() : expr.getExpression(), !(expr.getExpression() instanceof Concrete.ClassExtExpression), Collections.emptySet());
    boolean isClassExtOrRef = classExpr instanceof Concrete.ClassExtExpression || classExpr instanceof Concrete.ReferenceExpression;
    if (expr.getExpression() instanceof Concrete.ClassExtExpression) {
      if (classExpr instanceof Concrete.ClassExtExpression) {
        ((Concrete.ClassExtExpression) classExpr).getStatements().addAll(((Concrete.ClassExtExpression) expr.getExpression()).getStatements());
      } else {
        classExpr = expr.getExpression();
      }
    }
    if (isClassExtOrRef) {
      Expression unfoldedType = expectedType == null ? null : TypeConstructorExpression.unfoldType(expectedType);
      Concrete.Expression baseExpr = classExpr instanceof Concrete.ClassExtExpression ? ((Concrete.ClassExtExpression) classExpr).getBaseClassExpression() : classExpr;
      Definition actualDef = unfoldedType instanceof ClassCallExpression && baseExpr instanceof Concrete.ReferenceExpression && ((Concrete.ReferenceExpression) baseExpr).getReferent() instanceof TCDefReferable ? ((TCDefReferable) ((Concrete.ReferenceExpression) baseExpr).getReferent()).getTypechecked() : null;
      if (baseExpr instanceof Concrete.HoleExpression || actualDef instanceof ClassDefinition) {
        ClassCallExpression actualClassCall = null;
        if (baseExpr instanceof Concrete.HoleExpression && !(unfoldedType instanceof ClassCallExpression)) {
          errorReporter.report(new TypecheckingError("Cannot infer an expression", baseExpr));
          return null;
        }

        ClassCallExpression expectedClassCall = (ClassCallExpression) unfoldedType;
        if (baseExpr instanceof Concrete.ReferenceExpression baseRefExpr) {
          ClassDefinition actualClass = (ClassDefinition) actualDef;
          boolean ok = actualClass.isSubClassOf(expectedClassCall.getDefinition());
          if (ok && (actualDef != expectedClassCall.getDefinition() || baseRefExpr.getPLevels() != null || baseRefExpr.getHLevels() != null)) {
            boolean fieldsOK = true;
            for (ClassField implField : expectedClassCall.getImplementedHere().keySet()) {
              if (actualClass.isImplemented(implField)) {
                fieldsOK = false;
                break;
              }
            }
            Levels levels = typecheckLevels(actualDef, baseRefExpr, actualDef.generateInferVars(myEquations, expr), false);
            actualClassCall = new ClassCallExpression(actualClass, levels, new LinkedHashMap<>(), expectedClassCall.getSort(), actualDef.getUniverseKind());
            // It's probably better to use CMP.LE here, but then we need to check that copied implementations fit into their types with new levels.
            if (!actualClass.castLevels(expectedClassCall.getDefinition(), levels).compare(expectedClassCall.getLevels(), CMP.EQ, myEquations, expr)) {
              errorReporter.report(new TypeMismatchWithSubexprError(new CompareVisitor.Result(actualClassCall, expectedClassCall, actualClassCall, expectedClassCall, actualClassCall.getLevels(), expectedClassCall.getLevels()), expr));
              fieldsOK = false;
            }
            if (fieldsOK) {
              copyImplementationsFrom(actualClassCall, expectedClassCall, expr);
            }
          }
          if (!ok) {
            errorReporter.report(new TypeMismatchError(unfoldedType, baseExpr, baseExpr));
            return null;
          }
        }

        if (actualClassCall != null) {
          expectedClassCall = actualClassCall;
          expectedClassCall.updateHasUniverses();
        }
        pseudoImplemented = new HashSet<>();
        exprResult = typecheckClassExt(classExpr instanceof Concrete.ClassExtExpression ? ((Concrete.ClassExtExpression) classExpr).getStatements() : Collections.emptyList(), null, expectedClassCall, pseudoImplemented, classExpr, true);
        if (exprResult == null) {
          return null;
        }
      }
    }

    Expression renewExpr = null;
    if (exprResult == null) {
      Concrete.Expression baseClassExpr;
      List<Concrete.ClassFieldImpl> classFieldImpls;
      if (classExpr instanceof Concrete.ClassExtExpression) {
        baseClassExpr = ((Concrete.ClassExtExpression) classExpr).getBaseClassExpression();
        classFieldImpls = ((Concrete.ClassExtExpression) classExpr).getStatements();
      } else {
        baseClassExpr = classExpr;
        classFieldImpls = Collections.emptyList();
      }

      TypecheckingResult typeCheckedBaseClass = baseClassExpr instanceof Concrete.ReferenceExpression && ((Concrete.ReferenceExpression) baseClassExpr).getReferent() == Prelude.DEP_ARRAY.getRef()
        ? tResultToResult(null, visitReference((Concrete.ReferenceExpression) baseClassExpr, true), baseClassExpr)
        : baseClassExpr instanceof Concrete.ReferenceExpression
          ? visitReference((Concrete.ReferenceExpression) baseClassExpr, null, false)
          : baseClassExpr instanceof Concrete.AppExpression
            ? visitApp((Concrete.AppExpression) baseClassExpr, null, false)
            : checkExpr(baseClassExpr, null);
      if (typeCheckedBaseClass == null) {
        return null;
      }

      ClassCallExpression classCall = TypeConstructorExpression.unfoldType(typeCheckedBaseClass.expression).cast(ClassCallExpression.class);
      if (classCall == null) {
        classCall = TypeConstructorExpression.unfoldType(typeCheckedBaseClass.type).cast(ClassCallExpression.class);
        if (classCall == null) {
          errorReporter.report(new TypecheckingError("Expected a class or a class instance", baseClassExpr));
          return null;
        }
        renewExpr = typeCheckedBaseClass.expression;
      }

      exprResult = typecheckClassExt(classFieldImpls, null, renewExpr, classCall, null, baseClassExpr, true);
    }

    return makeNew(exprResult, expr, expectedType, pseudoImplemented);
  }

  private TypecheckingResult makeNew(TypecheckingResult result, Concrete.NewExpression expr, Expression expectedType, Set<ClassField> pseudoImplemented) {
    if (result == null) return null;
    Expression normExpr = result.expression.normalize(NormalizationMode.WHNF);
    ClassCallExpression classCallExpr = normExpr.cast(ClassCallExpression.class);
    if (classCallExpr == null) {
      if (!normExpr.reportIfError(errorReporter, expr.getExpression())) {
        errorReporter.report(new TypecheckingError("Expected a class", expr.getExpression()));
      }
      return new TypecheckingResult(new ErrorExpression(), normExpr);
    }

    if (checkAllImplemented(classCallExpr, pseudoImplemented, expr, expr.getExpression())) {
      return checkResult(expectedType, new TypecheckingResult(new NewExpression(null, classCallExpr), classCallExpr), expr);
    } else {
      return null;
    }
  }

  public boolean checkAllImplemented(ClassCallExpression classCall, Set<ClassField> pseudoImplemented, Concrete.SourceNode newSourceNode, Concrete.SourceNode classSourceNode) {
    var expectedFields = classCall.getDefinition().getNumberOfNotImplementedFields();
    int notImplemented = expectedFields - classCall.getImplementedHere().size();
    if (notImplemented == 0) {
      return true;
    } else if (notImplemented < 0) {
      throw new IllegalArgumentException("Too many implemented fields (expected " + expectedFields + "): " + classCall);
    } else {
      int missingArgs = 0;
      List<FieldReferable> fields = new ArrayList<>(notImplemented);
      for (ClassField field : classCall.getDefinition().getNotImplementedFields()) {
        if (!classCall.isImplementedHere(field) && !pseudoImplemented.contains(field)) {
          if (field.getReferable().isRealParameterField()) {
            missingArgs++;
          } else {
            fields.add(field.getReferable());
          }
        }
      }
      if (missingArgs > 0) {
        errorReporter.report(new TypecheckingError("Missing " + missingArgs + " arguments", classSourceNode instanceof Concrete.ClassExtExpression ? ((Concrete.ClassExtExpression) classSourceNode).getBaseClassExpression() : classSourceNode));
      }
      if (!fields.isEmpty()) {
        errorReporter.report(new FieldsImplementationError(false, classCall.getDefinition().getReferable(), fields, newSourceNode));
      }
      return false;
    }
  }

  // Variables

  private Definition referableToDefinition(Referable referable, Concrete.SourceNode sourceNode) {
    if (referable == null || referable instanceof ErrorReference) {
      return null;
    }

    if (!(referable instanceof GlobalReferable)) {
      if (sourceNode != null) {
        errorReporter.report(new WrongReferable("Expected a definition", referable, sourceNode));
      }
      return null;
    }

    Definition definition = referable instanceof TCDefReferable ? ((TCDefReferable) referable).getTypechecked() : null;
    if (definition == null && sourceNode != null) {
      errorReporter.report(new TypecheckingError("Internal error: definition '" + referable.textRepresentation() + "' was not typechecked", sourceNode));
    }
    return definition;
  }

  public <T extends Definition> T referableToDefinition(Referable referable, Class<T> clazz, String errorMsg, Concrete.SourceNode sourceNode) {
    Definition definition = referableToDefinition(referable, sourceNode);
    if (definition == null) {
      return null;
    }
    if (clazz.isInstance(definition)) {
      return clazz.cast(definition);
    }

    if (sourceNode != null) {
      errorReporter.report(new WrongReferable(errorMsg, referable, sourceNode));
    }
    return null;
  }

  public ClassField referableToClassField(Referable referable, Concrete.SourceNode sourceNode) {
    return referableToDefinition(referable, ClassField.class, "Expected a class field", sourceNode);
  }

  private CallableDefinition getTypeCheckedDefinition(TCDefReferable definition, Concrete.Expression expr) {
    Definition typeCheckedDefinition = definition.getTypechecked();
    if (!(typeCheckedDefinition instanceof CallableDefinition)) {
      errorReporter.report(new IncorrectReferenceError(definition, expr));
      return null;
    }
    if (!typeCheckedDefinition.status().headerIsOK()) {
      errorReporter.report(new HasErrors(GeneralError.Level.ERROR, definition, expr));
      return null;
    } else {
      return (CallableDefinition) typeCheckedDefinition;
    }
  }

  private void generateLevel(LevelVariable param, LevelSubstitution defaultLevels, boolean useMinAsDefault, boolean isUniverseLike, Concrete.SourceNode sourceNode, List<Level> result) {
    if (defaultLevels != null) {
      Level level = (Level) defaultLevels.get(param);
      if (level != null) {
        result.add(level);
        return;
      }
    }

    if (useMinAsDefault) {
      result.add(new Level(param.getMinValue()));
      return;
    }

    InferenceLevelVariable var = new InferenceLevelVariable(param.getType(), isUniverseLike, sourceNode);
    myEquations.addVariable(var);
    result.add(new Level(var));
  }

  private void typecheckLevels(List<Concrete.LevelExpression> levels, List<? extends LevelVariable> params, LevelSubstitution defaultLevels, boolean useMinAsDefault, boolean isUniverseLike, Concrete.SourceNode sourceNode, List<Level> result) {
    int s = result.size();
    if (levels == null) {
      for (LevelVariable param : params) {
        generateLevel(param, defaultLevels, useMinAsDefault, isUniverseLike, sourceNode, result);
      }
    } else {
      if (levels.size() > params.size()) {
        Concrete.LevelExpression level = levels.get(params.size());
        errorReporter.report(new TypecheckingError("Too many level arguments" + (level == null ? ", expected " + params.size() : ""), level == null ? sourceNode : level));
      }
      for (int i = 0; i < params.size(); i++) {
        Concrete.LevelExpression level = i < levels.size() ? levels.get(i) : null;
        if (level == null) {
          generateLevel(params.get(i), defaultLevels, useMinAsDefault, isUniverseLike, sourceNode, result);
        } else {
          result.add(level.accept(this, params.get(i).getStd()));
        }
      }
    }

    for (int i = 0; i < params.size() - 1; i++) {
      myEquations.addEquation(result.get(s + i + 1), result.get(s + i), params.get(i + 1).compare(params.get(i), CMP.LE) ? CMP.LE : CMP.GE, sourceNode);
    }
  }

  public Levels typecheckLevels(Definition def, Concrete.ReferenceExpression expr, Levels defaultLevels, boolean useMinAsDefault) {
    List<Concrete.LevelExpression> pLevels = expr.getPLevels();
    List<Concrete.LevelExpression> hLevels = expr.getHLevels();
    boolean isUniverseLike = def == myDefinition || def.getUniverseKind() != UniverseKind.NO_UNIVERSES;
    if (pLevels == null && hLevels == null) {
      return defaultLevels != null ? defaultLevels : useMinAsDefault ? def.makeMinLevels() : def.generateInferVars(myEquations, isUniverseLike, expr);
    }

    List<? extends LevelVariable> params = def.getLevelParameters();
    List<? extends LevelVariable> pParams;
    List<? extends LevelVariable> hParams;

    if (params == null) {
      pParams = Collections.singletonList(LevelVariable.PVAR);
      hParams = Collections.singletonList(LevelVariable.HVAR);
    } else {
      int pNum = def.getNumberOfPLevelParameters();
      pParams = params.subList(0, pNum);
      hParams = params.subList(pNum, params.size());
    }

    List<Level> result = new ArrayList<>();
    LevelSubstitution defaultSubst = defaultLevels == null ? null : defaultLevels.makeSubstitution(def);
    typecheckLevels(pLevels, pParams, defaultSubst, useMinAsDefault, isUniverseLike, expr, result);
    typecheckLevels(hLevels, hParams, defaultSubst, useMinAsDefault, isUniverseLike, expr, result);
    return params == null ? new LevelPair(result.get(0), result.get(1)) : new ListLevels(result);
  }

  private TResult typeCheckDefCall(TCDefReferable resolvedDefinition, Concrete.ReferenceExpression expr, boolean withoutUniverses) {
    CallableDefinition definition = getTypeCheckedDefinition(resolvedDefinition, expr);
    if (definition == null) {
      return null;
    }

    Levels levels;
    boolean isMin = definition instanceof DataDefinition && !definition.getParameters().hasNext() && definition.getUniverseKind() == UniverseKind.NO_UNIVERSES;
    if (definition == myDefinition || myRecursiveDefinitions.contains(definition.getRef()) && expr.getPLevels() == null && expr.getHLevels() == null) {
      levels = definition.makeIdLevels();
      Levels levels1 = typecheckLevels(definition, expr, null, false);
      if (!levels.compare(levels1, CMP.EQ, myEquations, expr)) {
        errorReporter.report(new TypecheckingError("Recursive call must have the same levels as the definition", expr));
      }
    } else if (expr.getPLevels() == null && expr.getHLevels() == null) {
      levels = isMin ? definition.makeMinLevels() : definition.generateInferVars(getEquations(), !withoutUniverses && (definition == myDefinition || definition.getUniverseKind() != UniverseKind.NO_UNIVERSES), expr);
      if (definition == Prelude.PATH || definition == Prelude.PATH_INFIX) {
        LevelPair levelPair = (LevelPair) levels;
        InferenceLevelVariable pl = new InferenceLevelVariable(LevelVariable.LvlType.PLVL, definition.getUniverseKind() != UniverseKind.NO_UNIVERSES, expr);
        myEquations.addVariable(pl);
        myEquations.addEquation(new Level(levelPair.get(LevelVariable.PVAR).getVar()), new Level(pl), CMP.LE, expr);
        getEquations().bindVariables(pl, (InferenceLevelVariable) levelPair.get(LevelVariable.HVAR).getVar());
        return DefCallResult.makePathType(expr, definition == Prelude.PATH_INFIX, levels, new Sort(new Level(pl), new Level(levelPair.get(LevelVariable.HVAR).getVar(), -1, -1)));
      }
    } else {
      levels = typecheckLevels(definition, expr, null, isMin);
    }

    return DefCallResult.makeTResult(expr, definition, levels);
  }

  private boolean checkUnresolved(Referable ref, Concrete.SourceNode sourceNode) {
    if (ref instanceof UnresolvedReference || ref instanceof RedirectingReferable) {
      errorReporter.report(new TypecheckingError("Unresolved reference `" + ref.textRepresentation() + "`. This may be caused by a bug in a meta resolver.", sourceNode));
      return false;
    }
    return true;
  }

  private TResult getLocalVar(Referable ref, Concrete.SourceNode sourceNode) {
    if (ref instanceof ErrorReference || !checkUnresolved(ref, sourceNode)) {
      return null;
    }

    Binding def = context.get(ref);
    if (def == null) {
      errorReporter.report(new IncorrectReferenceError(ref, sourceNode));
      return null;
    }
    Expression type = def.getTypeExpr();
    if (type == null) {
      errorReporter.report(new ReferenceTypeError(ref));
      return null;
    } else {
      return new TypecheckingResult(def instanceof TypedEvaluatingBinding ? ((TypedEvaluatingBinding) def).getExpression() : new ReferenceExpression(def), type);
    }
  }

  public TResult visitReference(Concrete.ReferenceExpression expr) {
    return visitReference(expr, false);
  }

  private TResult visitReference(Concrete.ReferenceExpression expr, boolean withoutUniverses) {
    Referable ref = expr.getReferent();
    if (ref instanceof CoreReferable) {
      TypecheckingResult result = ((CoreReferable) ref).result;
      fixCheckedExpression(result, ref, expr);
      return new TypecheckingResult(result.expression, result.type);
    } else if (ref instanceof AbstractedReferable) {
      Expression core = (Expression) substituteAbstractedExpression(((AbstractedReferable) ref).expression, LevelSubstitution.EMPTY, ((AbstractedReferable) ref).arguments, expr);
      return core == null ? null : new TypecheckingResult(core, core.getType());
    }

    if (!(ref instanceof GlobalReferable) && (expr.getPLevels() != null || expr.getHLevels() != null)) {
      errorReporter.report(new IgnoredLevelsError(expr));
    }
    return ref instanceof TCDefReferable ? typeCheckDefCall((TCDefReferable) ref, expr, withoutUniverses) : getLocalVar(expr.getReferent(), expr);
  }

  @Override
  public TypecheckingResult visitReference(Concrete.ReferenceExpression expr, Expression expectedType) {
    return visitReference(expr, expectedType, true);
  }

  private TypecheckingResult visitReference(Concrete.ReferenceExpression expr, Expression expectedType, boolean inferTailImplicits) {
    if (expr.getReferent() instanceof MetaReferable) {
      return checkMeta(expr, Collections.emptyList(), null, expectedType);
    }

    if (expr.getReferent() instanceof TCDefReferable && ((TCDefReferable) expr.getReferent()).getTypechecked() instanceof ClassDefinition) {
      List<SingleDependentLink> parameters = expectedType == null ? null : new ArrayList<>();
      if (expectedType != null) {
        expectedType = expectedType.normalizePi(parameters);
      }
      Concrete.Expression dExpr = desugarClassApp(expr, Collections.emptyList(), expr, parameters, inferTailImplicits, Collections.emptySet());
      if (dExpr != expr) {
        return checkExpr(dExpr, expectedType);
      }
    }

    if (Prelude.ZERO != null && expr.getReferent() == Prelude.ZERO.getRef() && expectedType != null) {
      expectedType = expectedType.normalize(NormalizationMode.WHNF);
      if (expectedType instanceof DataCallExpression && ((DataCallExpression) expectedType).getDefinition() == Prelude.FIN) {
        return checkResult(expectedType, new TypecheckingResult(new SmallIntegerExpression(0), DataCallExpression.make(Prelude.FIN, Levels.EMPTY, new SingletonList<>(new SmallIntegerExpression(1)))), expr);
      }
    }

    TResult result = visitReference(expr);
    if (result == null || !checkPath(result, expr)) {
      return null;
    }

    return tResultToResult(expectedType, result, expr);
  }

  @Override
  public TypecheckingResult visitThis(Concrete.ThisExpression expr, Expression expectedType) {
    errorReporter.report(new TypecheckingError("\\this expressions are allowed only in appropriate arguments of definitions and class extensions", expr));
    return null;
  }

  @Override
  public TypecheckingResult visitHole(Concrete.HoleExpression expr, Expression expectedType) {
    boolean isOmega = expectedType instanceof Type && ((Type) expectedType).isOmega();
    if (expr.isErrorHole()) {
      return expectedType != null && !isOmega ? new TypecheckingResult(new ErrorExpression(expr.getError()), expectedType) : null;
    }

    if (isOmega) {
      Expression type = new UniverseExpression(Sort.generateInferVars(getEquations(), false, expr));
      return new TypecheckingResult(InferenceReferenceExpression.make(myArgsInference.newInferenceVariable(type, expr), getEquations()), type);
    } else if (expectedType != null) {
      Expression norm = expectedType.normalize(NormalizationMode.WHNF);
      if (norm instanceof ClassCallExpression classCall) {
        if (!classCall.getDefinition().isRecord()) {
          ClassField field = classCall.getDefinition().getClassifyingField();
          AbsExpression classExpr = field == null ? null : classCall.getAbsImplementation(field);
          if (field == null || classExpr != null) {
            TypecheckingResult result = myInstancePool.findInstance(classExpr == null ? null : classExpr.getExpression().subst(classCall.getLevelSubstitution()), expectedType, new SubclassSearchParameters(classCall.getDefinition()), expr, expr instanceof RecursiveInstanceHoleExpression ? (RecursiveInstanceHoleExpression) expr : null, myDefinition);
            if (result != null) return result;
          }
        }
      }
      return new TypecheckingResult(InferenceReferenceExpression.make(myArgsInference.newInferenceVariable(expectedType, expr), getEquations()), expectedType);
    } else {
      errorReporter.report(new ArgInferenceError(getExpressionPrettifier(), expression(), expr, new Expression[0]));
      return null;
    }
  }

  // Level expressions

  @Override
  public Level visitInf(Concrete.InfLevelExpression expr, LevelVariable base) {
    if (base == LevelVariable.PVAR) {
      errorReporter.report(new TypecheckingError("\\inf is not a correct p-level", expr));
      return new Level(base);
    }
    return Level.INFINITY;
  }

  @Override
  public Level visitLP(Concrete.PLevelExpression expr, LevelVariable base) {
    if (base != LevelVariable.PVAR) {
      errorReporter.report(new TypecheckingError("Expected " + base, expr));
    }
    return new Level(base);
  }

  @Override
  public Level visitLH(Concrete.HLevelExpression expr, LevelVariable base) {
    if (base != LevelVariable.HVAR) {
      errorReporter.report(new TypecheckingError("Expected " + base, expr));
    }
    return new Level(base);
  }

  @Override
  public Level visitNumber(Concrete.NumberLevelExpression expr, LevelVariable base) {
    int level = expr.getNumber();
    if (level < base.getMinValue()) {
      errorReporter.report(new TypecheckingError("Expected a " + (base == LevelVariable.HVAR ? "number >= -1" : "positive number"), expr));
      level = base.getMinValue();
    }
    return new Level(level);
  }

  @Override
  public Level visitVar(Concrete.VarLevelExpression expr, LevelVariable base) {
    if (expr.getReferent() instanceof ErrorReference) {
      return new Level(base);
    }
    ParamLevelVariable var = myLevelContext != null && expr.getReferent() instanceof LevelReferable ? myLevelContext.getVariable((LevelReferable) expr.getReferent()) : null;
    if (var == null) {
      if (checkUnresolved(expr.getReferent(), expr)) {
        errorReporter.report(new IncorrectReferenceError(expr.getReferent(), expr));
      }
      return new Level(base);
    }
    return new Level(var);
  }

  @Override
  public Level visitSuc(Concrete.SucLevelExpression expr, LevelVariable base) {
    return expr.getExpression().accept(this, base).add(1);
  }

  @Override
  public Level visitMax(Concrete.MaxLevelExpression expr, LevelVariable base) {
    return expr.getLeft().accept(this, base).max(expr.getRight().accept(this, base));
  }

  // Sorts

  public Sort getSortOfType(Expression expr, Concrete.SourceNode sourceNode) {
    Expression type = expr.getType();
    Sort sort = type == null ? null : type.toSort();
    if (sort == null) {
      assert type != null;
      if (type.isInstance(ErrorExpression.class)) {
        return Sort.STD;
      }
      Sort result = Sort.generateInferVars(getEquations(), false, sourceNode);
      if (!CompareVisitor.compare(getEquations(), CMP.LE, type, new UniverseExpression(result), Type.OMEGA, sourceNode)) {
        errorReporter.report(new TypeMismatchError(DocFactory.text("a type"), type, sourceNode));
      }
      return result;
    } else {
      return sort;
    }
  }

  private static Sort generateUniqueUpperBound(List<Sort> sorts) {
    LevelVariable pVar = null;
    LevelVariable hVar = null;
    for (Sort sort : sorts) {
      if (sort.getPLevel().getVar() != null) {
        if (pVar == null) {
          pVar = sort.getPLevel().getVar();
        } else {
          pVar = pVar.max(sort.getPLevel().getVar());
          if (pVar == null) return null;
        }
      }
      if (sort.getHLevel().getVar() != null) {
        if (hVar == null) {
          hVar = sort.getHLevel().getVar();
        } else {
          hVar = hVar.max(sort.getHLevel().getVar());
          if (hVar == null) return null;
        }
      }
    }

    if (sorts.isEmpty()) {
      return Sort.PROP;
    } else {
      Sort resultSort = sorts.get(0);
      for (int i = 1; i < sorts.size(); i++) {
        resultSort = resultSort.max(sorts.get(i));
      }
      return resultSort;
    }
  }

  private Sort generateUpperBound(List<Sort> sorts, Concrete.SourceNode sourceNode) {
    Sort resultSort = generateUniqueUpperBound(sorts);
    if (resultSort != null) {
      return resultSort;
    }

    Sort sortResult = Sort.generateInferVars(getEquations(), false, sourceNode);
    for (Sort sort : sorts) {
      getEquations().addEquation(sort.getPLevel(), sortResult.getPLevel(), CMP.LE, sourceNode);
      getEquations().addEquation(sort.getHLevel(), sortResult.getHLevel(), CMP.LE, sourceNode);
    }
    return sortResult;
  }

  public void fixClassExtSort(ClassCallExpression classCall, Concrete.SourceNode sourceNode) {
    ClassDefinition classDef = classCall.getDefinition();
    Levels idLevels = classDef.makeIdLevels();
    Expression thisExpr = new ReferenceExpression(ExpressionFactory.parameter("this", new ClassCallExpression(classDef, idLevels)));
    Integer hLevel = classDef.getUseLevel(classCall.getImplementedHere(), classCall.getThisBinding(), true);
    if (hLevel != null && hLevel == -1) {
      classCall.setSort(Sort.PROP);
    } else {
      Sort maxSort = hLevel == null ? Sort.PROP : new Sort(new Level(0), new Level(hLevel));
      for (ClassField field : classDef.getNotImplementedFields()) {
        if (classCall.isImplementedHere(field)) continue;
        Expression fieldType = classDef.getFieldType(field, classDef.castLevels(field.getParentClass(), idLevels), thisExpr).normalize(NormalizationMode.WHNF);
        if (!fieldType.isInstance(ErrorExpression.class)) {
          maxSort = maxSort.max(getSortOfType(fieldType, sourceNode));
          if (maxSort == null) {
            throw new IllegalStateException();
          }
        }
      }
      classCall.setSort(maxSort);
    }
  }

  // Parameters

  private TypedSingleDependentLink visitNameParameter(Concrete.NameParameter param, Concrete.SourceNode sourceNode) {
    Referable referable = param.getReferable();
    String name = referable == null ? null : referable.textRepresentation();
    Sort sort = Sort.generateInferVars(myEquations, false, sourceNode);
    InferenceVariable inferenceVariable = new LambdaInferenceVariable(name == null ? "_" : "type-of-" + name, new UniverseExpression(sort), param.getReferable(), false, sourceNode, getAllBindings());
    Expression argType = InferenceReferenceExpression.make(inferenceVariable, myEquations);

    TypedSingleDependentLink link = new TypedSingleDependentLink(param.isExplicit(), name, new TypeExpression(argType, sort));
    addBinding(referable, link);
    return link;
  }

  private SingleDependentLink visitTypeParameter(Concrete.TypeParameter param, List<Sort> sorts, Type expectedType) {
    Type argResult = checkType(param.getType(), Type.OMEGA);
    if (argResult == null) return null;
    if (expectedType != null) {
      Expression expected = expectedType.getExpr().normalize(NormalizationMode.WHNF).getUnderlyingExpression();
      if ((expected instanceof ClassCallExpression || expected instanceof PiExpression || expected instanceof SigmaExpression || expected instanceof UniverseExpression)
          && expected.isLessOrEquals(argResult.getExpr(), myEquations, param)) {
        argResult = expectedType;
      }
    }
    if (sorts != null) {
      sorts.add(argResult.getSortOfType());
    }

    if (param instanceof Concrete.TelescopeParameter) {
      List<? extends Referable> referableList = param.getReferableList();
      SingleDependentLink link = ExpressionFactory.singleParams(param.isExplicit(), param.getNames(), argResult);
      int i = 0;
      for (SingleDependentLink link1 = link; link1.hasNext(); link1 = link1.getNext(), i++) {
        addBinding(referableList.get(i) , link1);
      }
      return link;
    } else {
      return new TypedSingleDependentLink(param.isExplicit(), null, argResult);
    }
  }

  private boolean visitParameter(Concrete.Parameter arg, Expression expectedType, List<Sort> resultSorts, LinkList list) {
    Type result = checkType(arg.getType(), expectedType == null ? Type.OMEGA : expectedType);
    if (result == null) return false;

    if (arg instanceof Concrete.TelescopeParameter) {
      List<? extends Referable> referableList = arg.getReferableList();
      DependentLink link = ExpressionFactory.parameter(arg.isExplicit(), arg.getNames(), result);
      list.append(link);
      int i = 0;
      for (DependentLink link1 = link; link1.hasNext(); link1 = link1.getNext(), i++) {
        addBinding(referableList.get(i), link1);
      }
    } else {
      DependentLink link = ExpressionFactory.parameter(arg.isExplicit(), Collections.singletonList(null), result);
      list.append(link);
      addBinding(null, link);
    }

    if (resultSorts != null) {
      resultSorts.add(result.getSortOfType());
    }
    return true;
  }

  private DependentLink visitParameters(Collection<? extends ConcreteParameter> parameters, Expression expectedType, List<Sort> resultSorts) {
    LinkList list = new LinkList();

    try (var ignored = new Utils.RefContextSaver(context, myLocalPrettifier)) {
      for (ConcreteParameter parameter : parameters) {
        if (!(parameter instanceof Concrete.TypeParameter)) {
          throw new IllegalArgumentException();
        }
        if (!visitParameter((Concrete.TypeParameter) parameter, expectedType, resultSorts, list)) {
          return null;
        }
      }
    }

    return list.getFirst();
  }

  // Pi

  private TypecheckingResult bodyToLam(SingleDependentLink params, TypecheckingResult bodyResult, Concrete.SourceNode sourceNode) {
    if (bodyResult == null) {
      return null;
    }
    Sort sort = PiExpression.generateUpperBound(params.getType().getSortOfType(), getSortOfType(bodyResult.type, sourceNode), myEquations, sourceNode);
    return new TypecheckingResult(new LamExpression(sort, params, bodyResult.expression), new PiExpression(sort, params, bodyResult.type));
  }

  private interface ParametersProvider {
    @Nullable SingleDependentLink nextParameter();
    <T> @Nullable Pair<TypecheckingResult,T> coerce(Function<ParametersProvider, Pair<Expression,T>> checker);
    @Nullable Expression getType();
    void subst(DependentLink param, Expression expr);
  }

  private static final ParametersProvider NULL_PARAMETERS_PROVIDER = new ParametersProvider() {
    @Override
    public @Nullable SingleDependentLink nextParameter() {
      return null;
    }

    @Override
    public @Nullable <T> Pair<TypecheckingResult, T> coerce(Function<ParametersProvider, Pair<Expression, T>> checker) {
      return null;
    }

    @Override
    public @Nullable Expression getType() {
      return null;
    }

    @Override
    public void subst(DependentLink param, Expression expr) {

    }
  };

  private static class ExpressionParametersProvider implements ParametersProvider {
    private Sort sort = Sort.STD;
    private SingleDependentLink parameter = EmptyDependentLink.getInstance();
    private Expression expression;
    private final ExprSubstitution substitution = new ExprSubstitution();

    public ExpressionParametersProvider(Expression expression) {
      this.expression = expression;
    }

    @Override
    public @Nullable SingleDependentLink nextParameter() {
      if (!parameter.hasNext()) {
        expression = expression.subst(substitution).normalize(NormalizationMode.WHNF);
        substitution.clear();
        if (!(expression instanceof PiExpression piExpr)) {
          return null;
        }

        sort = piExpr.getResultSort();
        parameter = piExpr.getParameters();
        expression = piExpr.getCodomain();
      }

      SingleDependentLink result = parameter;
      parameter = parameter.getNext();
      return result;
    }

    @Override
    public <T> @Nullable Pair<TypecheckingResult,T> coerce(Function<ParametersProvider, Pair<Expression,T>> checker) {
      return expression instanceof FunCallExpression && ((FunCallExpression) expression).getDefinition().getKind() == CoreFunctionDefinition.Kind.TYPE
        ? coerceToType(expression, t -> checker.apply(new ExpressionParametersProvider(t))) : null;
    }

    @Override
    public @Nullable Expression getType() {
      return (parameter.hasNext() ? new PiExpression(sort, parameter, expression) : expression).subst(substitution);
    }

    @Override
    public void subst(DependentLink param, Expression expr) {
      substitution.add(param, expr);
    }
  }

  private TypecheckingResult visitLam(List<? extends Concrete.Parameter> parameters, Concrete.LamExpression expr, ParametersProvider provider) {
    if (parameters.isEmpty()) {
      return checkExpr(expr.getBody(), provider.getType());
    }

    Function<Pair<ParametersProvider,SingleDependentLink>, Pair<TypecheckingResult,Boolean>> checker = pair -> {
      ParametersProvider newProvider = pair.proj1;
      SingleDependentLink piParam = pair.proj2;
      Concrete.Parameter param = parameters.get(0);
      if (param.isProperty()) {
        errorReporter.report(new CertainTypecheckingError(CertainTypecheckingError.Kind.PROPERTY_IGNORED, param));
      }
      if (piParam != null && !piParam.isExplicit() && param.isExplicit()) {
        for (SingleDependentLink link = piParam; link.hasNext(); link = link.getNext()) {
          addBinding(null, link);
          if (link instanceof UntypedSingleDependentLink) {
            newProvider.nextParameter();
          }
        }
        return new Pair<>(bodyToLam(piParam, visitLam(parameters, expr, newProvider), expr), true);
      }

      if (param instanceof Concrete.NameParameter) {
        if (piParam == null) {
          TypedSingleDependentLink link = visitNameParameter((Concrete.NameParameter) param, expr);
          TypecheckingResult bodyResult = visitLam(parameters.subList(1, parameters.size()), expr, new ExpressionParametersProvider(new InferenceReferenceExpression(new ExpressionInferenceVariable(new UniverseExpression(Sort.generateInferVars(myEquations, false, expr)), expr, getAllBindings(), true, true))));
          if (bodyResult == null) return new Pair<>(null, true);
          Sort sort = PiExpression.generateUpperBound(link.getType().getSortOfType(), getSortOfType(bodyResult.type, expr), myEquations, expr);
          TypecheckingResult result = new TypecheckingResult(new LamExpression(sort, link, bodyResult.expression), new PiExpression(sort, link, bodyResult.type));
          Expression expectedType = newProvider.getType();
          return new Pair<>(expectedType == null ? result : checkResult(expectedType, result, expr), true);
        } else {
          Referable referable = ((Concrete.NameParameter) param).getReferable();
          if (piParam.isExplicit() && !param.isExplicit()) {
            errorReporter.report(new ImplicitLambdaError(referable, -1, param));
          }

          Type paramType = piParam.getType();
          DefCallExpression defCallParamType = paramType.getExpr().cast(DefCallExpression.class);
          if (defCallParamType != null && defCallParamType.getDefinition().getUniverseKind() == UniverseKind.NO_UNIVERSES) { // fixes test pLevelTest
            Definition definition = defCallParamType.getDefinition();
            Levels levels = definition instanceof DataDefinition || definition instanceof FunctionDefinition || definition instanceof ClassDefinition ? definition.generateInferVars(myEquations, false, param) : null;
            if (definition instanceof ClassDefinition) {
              ClassCallExpression classCall = (ClassCallExpression) defCallParamType;
              for (Map.Entry<ClassField, Expression> entry : classCall.getImplementedHere().entrySet()) {
                Expression type = entry.getValue().getType();
                if (type == null || !CompareVisitor.compare(myEquations, CMP.LE, type, classCall.getDefinition().getFieldType(entry.getKey(), levels, new ReferenceExpression(classCall.getThisBinding())), Type.OMEGA, param)) {
                  levels = null;
                  break;
                }
              }
            } else if (levels != null) {
              ExprSubstitution substitution = new ExprSubstitution();
              DependentLink link = definition.getParameters();
              LevelSubstitution levelSubst = levels.makeSubstitution(definition);
              for (Expression arg : defCallParamType.getDefCallArguments()) {
                Expression type = arg.getType();
                if (type == null || !CompareVisitor.compare(myEquations, CMP.LE, type, link.getTypeExpr().subst(substitution, levelSubst), Type.OMEGA, param)) {
                  levels = null;
                  break;
                }
                substitution.add(link, arg);
                link = link.getNext();
              }
            }

            if (levels != null) {
              if (definition instanceof DataDefinition) {
                paramType = DataCallExpression.make((DataDefinition) definition, levels, new ArrayList<>(defCallParamType.getDefCallArguments()));
              } else if (definition instanceof FunctionDefinition) {
                paramType = new TypeExpression(FunCallExpression.make((FunctionDefinition) definition, levels, new ArrayList<>(defCallParamType.getDefCallArguments())), paramType.getSortOfType());
              } else {
                ClassCallExpression classCall = (ClassCallExpression) defCallParamType;
                paramType = new ClassCallExpression((ClassDefinition) definition, levels, classCall.getImplementedHere(), classCall.getDefinition().computeSort(classCall.getImplementedHere(), classCall.getThisBinding()), classCall.getUniverseKind());
              }
            }
          }

          SingleDependentLink link = new TypedSingleDependentLink(piParam.isExplicit(), referable == null ? null : referable.textRepresentation(), paramType);
          addBinding(referable, link);
          newProvider.subst(piParam, new ReferenceExpression(link));
          return new Pair<>(bodyToLam(link, visitLam(parameters.subList(1, parameters.size()), expr, newProvider), expr), true);
        }
      } else if (param instanceof Concrete.TypeParameter) {
        SingleDependentLink link = visitTypeParameter((Concrete.TypeParameter) param, null, piParam == null || piParam.isExplicit() != param.isExplicit() ? null : piParam.getType());
        if (link == null) {
          return new Pair<>(null, true);
        }

        int namesCount = param.getNumberOfParameters();
        SingleDependentLink actualLink = link;
        if (piParam != null) {
          Expression argType = link.getTypeExpr();
          for (int i = 0; i < namesCount; i++, actualLink = actualLink.getNext()) {
            while (piParam instanceof UntypedDependentLink && i < namesCount - 1) {
              newProvider.subst(piParam, new ReferenceExpression(actualLink));
              piParam = newProvider.nextParameter();
              actualLink = actualLink.getNext();
              i++;
            }
            if (piParam == null) {
              break;
            }
            if (piParam.isExplicit() && !param.isExplicit() && i < namesCount) {
              errorReporter.report(new ImplicitLambdaError(param.getReferableList().get(i), namesCount > 1 ? i : -1, param));
            }
            if (!CompareVisitor.compare(myEquations, CMP.EQ, argType, piParam.getTypeExpr(), Type.OMEGA, param.getType())) {
              if (!argType.reportIfError(errorReporter, param.getType())) {
                errorReporter.report(new TypeMismatchError("Type mismatch in an argument of the lambda", piParam.getTypeExpr(), argType, param.getType()));
                return new Pair<>(null, true);
              }
            }

            newProvider.subst(piParam, new ReferenceExpression(actualLink));
            if (i < namesCount - 1) {
              piParam = newProvider.nextParameter();
            }
          }
        }

        TypecheckingResult bodyResult = visitLam(parameters.subList(1, parameters.size()), expr, actualLink.hasNext() ? NULL_PARAMETERS_PROVIDER : newProvider);
        if (bodyResult == null) return new Pair<>(null, true);
        Sort sort = PiExpression.generateUpperBound(link.getType().getSortOfType(), getSortOfType(bodyResult.type, expr), myEquations, expr);
        if (actualLink.hasNext()) {
          Expression expectedType = newProvider.getType();
          if (expectedType != null) {
            TypecheckingResult result = checkResult(expectedType, new TypecheckingResult(new LamExpression(sort, actualLink, bodyResult.expression), new PiExpression(sort, actualLink, bodyResult.type)), expr);
            if (result == null || link == actualLink) return new Pair<>(result, true);
            if (!(result.expression instanceof LamExpression)) {
              DependentLink prevLink = link;
              while (prevLink.getNext() != actualLink) {
                prevLink = prevLink.getNext();
              }
              prevLink.setNext(EmptyDependentLink.getInstance());
              prevLink = link;
              while (prevLink.getNext().hasNext()) {
                prevLink = prevLink.getNext();
              }
              if (prevLink instanceof UntypedDependentLink) {
                TypedSingleDependentLink lastLink = new TypedSingleDependentLink(prevLink.isExplicit(), prevLink.getName(), actualLink.getType(), prevLink.isHidden());
                if (prevLink == link) {
                  link = lastLink;
                } else {
                  DependentLink prevPrevLink = link;
                  while (prevPrevLink.getNext() != prevLink) {
                    prevPrevLink = prevPrevLink.getNext();
                  }
                  prevPrevLink.setNext(lastLink);
                }
              }
              return new Pair<>(new TypecheckingResult(new LamExpression(sort, link, result.expression), new PiExpression(sort, link, result.type)), true);
            }
          }
        }

        return new Pair<>(new TypecheckingResult(new LamExpression(sort, link, bodyResult.expression), new PiExpression(sort, link, bodyResult.type)), true);
      } else {
        throw new IllegalStateException();
      }
    };

    SingleDependentLink piParam = provider.nextParameter();
    if (piParam != null) {
      return checker.apply(new Pair<>(provider, piParam)).proj1;
    }

    var pair2 = provider.coerce(newProvider -> {
      SingleDependentLink newPiParam = newProvider.nextParameter();
      if (newPiParam == null) return null;
      var pair = checker.apply(new Pair<>(newProvider, newPiParam));
      return new Pair<>(pair.proj1 == null ? null : pair.proj1.expression, pair.proj2);
    });

    return pair2 != null ? pair2.proj1 : checker.apply(new Pair<>(provider, null)).proj1;
  }

  @Override
  public TypecheckingResult visitLam(Concrete.LamExpression expr, Expression expectedType) {
    try (var ignored = new Utils.RefContextSaver(context, myLocalPrettifier)) {
      if (expectedType == null) {
        return visitLam(expr.getParameters(), expr, NULL_PARAMETERS_PROVIDER);
      }

      expectedType = expectedType.normalize(NormalizationMode.WHNF);
      Expression type = expectedType;
      ClassCallExpression classCall = expectedType.cast(ClassCallExpression.class);
      if (classCall != null && classCall.getDefinition() == Prelude.DEP_ARRAY) {
        Expression length = classCall.getAbsImplementationHere(Prelude.ARRAY_LENGTH);
        if (length != null) {
          Expression elementsType = classCall.getImplementation(Prelude.ARRAY_ELEMENTS_TYPE, new NewExpression(null, classCall));
          if (elementsType != null) {
            TypedSingleDependentLink param = new TypedSingleDependentLink(true, "j", ExpressionFactory.Fin(length));
            type = new PiExpression(classCall.getSortOfType().max(Sort.SET0), param, AppExpression.make(elementsType, new ReferenceExpression(param), true));
          }
        }
      }

      TypecheckingResult result = visitLam(expr.getParameters(), expr, new ExpressionParametersProvider(type));
      if (result == null || type == expectedType) {
        return result;
      }

      Map<ClassField, Expression> impls = new LinkedHashMap<>(classCall.getImplementedHere());
      impls.put(Prelude.ARRAY_AT, result.expression);
      return new TypecheckingResult(new NewExpression(null, new ClassCallExpression(Prelude.DEP_ARRAY, classCall.getLevels(), impls, Sort.PROP, UniverseKind.NO_UNIVERSES)), expectedType);
    }
  }

  @Override
  public TypecheckingResult visitPi(Concrete.PiExpression expr, Expression expectedType) {
    List<SingleDependentLink> list = new ArrayList<>();
    List<Sort> sorts = new ArrayList<>(expr.getParameters().size());

    try (var ignored = new Utils.RefContextSaver(context, myLocalPrettifier)) {
      for (Concrete.TypeParameter arg : expr.getParameters()) {
        if (arg.isProperty()) {
          errorReporter.report(new CertainTypecheckingError(CertainTypecheckingError.Kind.PROPERTY_IGNORED, arg));
        }
        SingleDependentLink link = visitTypeParameter(arg, sorts, null);
        if (link == null) {
          return null;
        }
        list.add(link);
      }

      Type result = checkType(expr.getCodomain(), Type.OMEGA);
      if (result == null) return null;
      Sort codSort = result.getSortOfType();

      Expression piExpr = result.getExpr();
      for (int i = list.size() - 1; i >= 0; i--) {
        codSort = PiExpression.generateUpperBound(sorts.get(i), codSort, myEquations, expr);
        piExpr = new PiExpression(codSort, list.get(i), piExpr);
      }

      return checkResult(expectedType, new TypecheckingResult(piExpr, new UniverseExpression(codSort)), expr);
    }
  }

  // Sigma

  @Override
  public TypecheckingResult visitSigma(Concrete.SigmaExpression expr, Expression expectedType) {
    if (expr.getParameters().isEmpty()) {
      return checkResult(expectedType, new TypecheckingResult(new SigmaExpression(Sort.PROP, EmptyDependentLink.getInstance()), new UniverseExpression(Sort.PROP)), expr);
    }
    if (expr.getParameters().size() == 1 && expr.getParameters().get(0).getReferableList().size() == 1) {
      errorReporter.report(new TypecheckingError("\\Sigma type cannot have exactly one parameter", expr));
      return expr.getParameters().get(0).getType().accept(this, expectedType);
    }

    List<Sort> sorts = new ArrayList<>(expr.getParameters().size());
    DependentLink args = visitSigmaParameters(expr.getParameters(), expectedType, sorts);
    if (args == null || !args.hasNext()) return null;
    Sort sort = generateUpperBound(sorts, expr);
    return checkResult(expectedType, new TypecheckingResult(new SigmaExpression(sort, args), new UniverseExpression(sort)), expr);
  }

  private DependentLink visitSigmaParameters(Collection<? extends Concrete.TypeParameter> parameters, Expression expectedType, List<Sort> resultSorts) {
    LinkList list = new LinkList();

    try (var ignored = new Utils.RefContextSaver(context, myLocalPrettifier)) {
      for (Concrete.TypeParameter parameter : parameters) {
        if (!visitSigmaParameter(parameter, expectedType, resultSorts, list)) {
          return null;
        }
      }
    }

    return list.getFirst();
  }

  private boolean visitSigmaParameter(Concrete.TypeParameter arg, Expression expectedType, List<Sort> resultSorts, LinkList list) {
    Type result = checkType(arg.getType(), expectedType == null ? Type.OMEGA : expectedType);
    if (result == null) return false;

    Sort sort = result.getSortOfType();
    boolean isProperty = arg.isProperty();
    boolean isProp = isProperty && Sort.compare(sort, Sort.PROP, CMP.LE, myEquations, arg);
    if (!isProp && isProperty) {
      errorReporter.report(new LevelMismatchError(LevelMismatchError.TargetKind.SIGMA_FIELD, result.getSortOfType(), arg));
    }
    if (arg instanceof Concrete.TelescopeParameter) {
      List<? extends Referable> referableList = arg.getReferableList();
      DependentLink link = ExpressionFactory.parameter(true, isProp, arg.getNames(), result);
      list.append(link);
      int i = 0;
      for (DependentLink link1 = link; link1.hasNext(); link1 = link1.getNext(), i++) {
        addBinding(referableList.get(i), link1);
      }
    } else {
      DependentLink link = ExpressionFactory.parameter(true, isProp, Collections.singletonList(null), result);
      list.append(link);
      addBinding(null, link);
    }

    if (resultSorts != null) {
      resultSorts.add(sort);
    }
    return true;
  }

  @Override
  public TypecheckingResult visitTuple(Concrete.TupleExpression expr, Expression expectedType) {
    Expression expectedTypeNorm = expectedType == null ? null : expectedType.normalize(NormalizationMode.WHNF);
    if (expectedTypeNorm instanceof ClassCallExpression classCall && classCall.getDefinition() == Prelude.DEP_ARRAY && !classCall.isImplementedHere(Prelude.ARRAY_AT)) {
      Expression elementsType = classCall.getAbsImplementationHere(Prelude.ARRAY_ELEMENTS_TYPE);
      if (elementsType != null) {
        Expression length = classCall.getAbsImplementationHere(Prelude.ARRAY_LENGTH);
        if (length == null) {
          Map<ClassField, Expression> impls = new LinkedHashMap<>();
          impls.put(Prelude.ARRAY_LENGTH, new SmallIntegerExpression(expr.getFields().size()));
          impls.putAll(classCall.getImplementedHere());
          elementsType = elementsType.subst(classCall.getThisBinding(), new NewExpression(null, new ClassCallExpression(Prelude.DEP_ARRAY, classCall.getLevels(), impls, classCall.getSort(), classCall.getUniverseKind())));
        } else {
          Expression actualLength = new SmallIntegerExpression(expr.getFields().size());
          if (!CompareVisitor.compare(myEquations, CMP.EQ, length, actualLength, Nat(), expr)) {
            errorReporter.report(new TypeMismatchWithSubexprError(new CompareVisitor.Result(new ClassCallExpression(Prelude.DEP_ARRAY, LevelPair.PROP, Collections.singletonMap(Prelude.ARRAY_LENGTH, actualLength), Sort.SET0, UniverseKind.ONLY_COVARIANT), new ClassCallExpression(Prelude.DEP_ARRAY, LevelPair.PROP, Collections.singletonMap(Prelude.ARRAY_LENGTH, length), Sort.SET0, UniverseKind.ONLY_COVARIANT), actualLength, length), expr));
            return null;
          }
        }
        elementsType = elementsType.removeUnusedBinding(classCall.getThisBinding());
        if (elementsType != null) {
          List<Expression> elements = new ArrayList<>(expr.getFields().size());
          boolean ok = true;
          for (int i = 0; i < expr.getFields().size(); i++) {
            TypecheckingResult field = checkExpr(expr.getFields().get(i), AppExpression.make(elementsType, new SmallIntegerExpression(i), true));
            if (field == null) {
              ok = false;
            } else {
              elements.add(field.expression);
            }
          }
          return ok ? new TypecheckingResult(ArrayExpression.make(classCall.getLevels().toLevelPair(), elementsType, elements, null), classCall) : null;
        }
      }
    }

    Function<Expression, Pair<Expression,Boolean>> checker = type -> {
      if (!(type instanceof SigmaExpression)) {
        return null;
      }

      DependentLink sigmaParams = ((SigmaExpression) type).getParameters();
      int sigmaParamsSize = DependentLink.Helper.size(sigmaParams);

      if (expr.getFields().size() != sigmaParamsSize) {
        errorReporter.report(new TypecheckingError("Expected a tuple with " + sigmaParamsSize + " fields, but given " + expr.getFields().size(), expr));
        return new Pair<>(null, false);
      }

      List<Expression> fields = new ArrayList<>(expr.getFields().size());
      ExprSubstitution substitution = new ExprSubstitution();
      for (Concrete.Expression field : expr.getFields()) {
        Expression expType = sigmaParams.getTypeExpr().subst(substitution);
        TypecheckingResult result = checkExpr(field, expType);
        if (result == null) return new Pair<>(null, false);
        fields.add(result.expression);
        substitution.add(sigmaParams, result.expression);

        sigmaParams = sigmaParams.getNext();
      }
      return new Pair<>(new TupleExpression(fields, (SigmaExpression) type), true);
    };

    Pair<TypecheckingResult, Boolean> coerceResult = coerceToType(expectedTypeNorm, checker);
    if (coerceResult != null) {
      return coerceResult.proj1;
    }
    Pair<Expression,Boolean> pair = checker.apply(expectedTypeNorm);
    if (pair != null) {
      return pair.proj1 == null ? null : new TypecheckingResult(pair.proj1, expectedType);
    }

    List<Sort> sorts = new ArrayList<>(expr.getFields().size());
    List<Expression> fields = new ArrayList<>(expr.getFields().size());
    LinkList list = new LinkList();
    for (int i = 0; i < expr.getFields().size(); i++) {
      TypecheckingResult result = checkExpr(expr.getFields().get(i), null);
      if (result == null) return null;
      fields.add(result.expression);
      Sort sort = getSortOfType(result.type, expr);
      sorts.add(sort);
      list.append(ExpressionFactory.parameter(null, result.type instanceof Type ? (Type) result.type : new TypeExpression(result.type, sort)));
    }

    SigmaExpression type = new SigmaExpression(generateUpperBound(sorts, expr), list.getFirst());
    return checkResult(expectedTypeNorm, new TypecheckingResult(new TupleExpression(fields, type), type), expr);
  }

  @Override
  public TypecheckingResult visitProj(Concrete.ProjExpression expr, Expression expectedType) {
    TypecheckingResult exprResult = checkExpr(expr.expression, null);
    if (exprResult == null) return null;
    exprResult.type = exprResult.type.normalize(NormalizationMode.WHNF);

    if (exprResult.type instanceof FunCallExpression && ((FunCallExpression) exprResult.type).getDefinition().getKind() == CoreFunctionDefinition.Kind.TYPE) {
      TypecheckingResult coerceResult = coerceFromType(exprResult);
      if (coerceResult != null) {
        exprResult.expression = coerceResult.expression;
        exprResult.type = coerceResult.type.normalize(NormalizationMode.WHNF);
      }
    }

    if (expectedType != null && !(exprResult.type instanceof SigmaExpression) && exprResult.type.getStuckInferenceVariable() != null) {
      return defer(new MetaDefinition() {
        @Override
        public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
          return checkProj(exprResult, expr, expectedType);
        }
      }, new ContextDataImpl(expr, Collections.emptyList(), null, null, expectedType, null), expectedType, false);
    }

    return checkProj(exprResult, expr, expectedType);
  }

  private TypecheckingResult checkProj(TypecheckingResult exprResult, Concrete.ProjExpression projExpr, Expression expectedType) {
    Concrete.Expression expr = projExpr.expression;
    {
      ExprSubstitution subst = new ExprSubstitution();
      while (exprResult.type instanceof PiExpression && !((PiExpression) exprResult.type).getParameters().isExplicit()) {
        for (DependentLink param = ((PiExpression) exprResult.type).getParameters(); param.hasNext(); param = param.getNext()) {
          Expression arg = new InferenceReferenceExpression(new ExpressionInferenceVariable(param.getTypeExpr(), expr, getAllBindings(), true));
          exprResult.expression = AppExpression.make(exprResult.expression, arg, false);
          subst.add(param, arg);
        }
        exprResult.type = ((PiExpression) exprResult.type).getCodomain().subst(subst).normalize(NormalizationMode.WHNF);
      }
      exprResult.type = exprResult.type.subst(subst);
    }

    if (!(exprResult.type instanceof SigmaExpression)) {
      TypecheckingResult coercedResult = CoerceData.coerceToKey(exprResult, new CoerceData.SigmaKey(), expr, this);
      if (coercedResult != null) {
        exprResult = coercedResult;
      }
    }
    SigmaExpression sigmaExpr = exprResult.type.cast(SigmaExpression.class);
    if (sigmaExpr == null) {
      Expression stuck = exprResult.type.getStuckExpression();
      if (stuck == null || !stuck.reportIfError(errorReporter, expr)) {
        errorReporter.report(new TypeMismatchError(DocFactory.text("A sigma type"), exprResult.type, expr));
      }
      return null;
    }

    DependentLink sigmaParams = sigmaExpr.getParameters();
    if (projExpr.getField() < 0) {
      errorReporter.report(new TypecheckingError("Index " + (projExpr.getField() +1) + " is too small; the lower bound of projection index is 1", projExpr));
      return null;
    }
    DependentLink fieldLink = DependentLink.Helper.get(sigmaParams, projExpr.getField());
    if (!fieldLink.hasNext()) {
      errorReporter.report(new TypecheckingError("Index " + (projExpr.getField() + 1) + " is out of range; the number of parameters is " + DependentLink.Helper.size(sigmaParams), projExpr));
      return null;
    }

    ExprSubstitution substitution = new ExprSubstitution();
    for (int i = 0; sigmaParams != fieldLink; sigmaParams = sigmaParams.getNext(), i++) {
      substitution.add(sigmaParams, ProjExpression.make(exprResult.expression, i, sigmaParams.isProperty()));
    }

    exprResult.expression = ProjExpression.make(exprResult.expression, projExpr.getField(), fieldLink.isProperty());
    exprResult.type = fieldLink.getTypeExpr().subst(substitution);
    return checkResult(expectedType, exprResult, projExpr);
  }

  // Let

  private TypecheckingResult typecheckLetClause(List<? extends Concrete.Parameter> parameters, Concrete.LetClause letClause, boolean useSpecifiedType) {
    if (parameters.isEmpty()) {
      Concrete.Expression letResult = letClause.getResultType();
      if (letResult != null) {
        Type type = checkType(letResult, Type.OMEGA);
        if (type == null) {
          return null;
        }

        TypecheckingResult result = checkExpr(letClause.getTerm(), type.getExpr());
        if (result == null) {
          return new TypecheckingResult(new ErrorExpression(type.getExpr()), type.getExpr());
        }
        ErrorExpression errorExpr = result.expression.cast(ErrorExpression.class);
        if (errorExpr != null && !errorExpr.useExpression()) {
          errorExpr = errorExpr.replaceExpression(type.getExpr());
          if (!errorExpr.useExpression()) {
            result.expression = errorExpr;
          }
        }
        return useSpecifiedType ? new TypecheckingResult(result.expression, type.getExpr()) : result;
      } else {
        return checkExpr(letClause.getTerm(), null);
      }
    }

    Concrete.Parameter param = parameters.get(0);
    if (param.isProperty()) {
      errorReporter.report(new CertainTypecheckingError(CertainTypecheckingError.Kind.PROPERTY_IGNORED, param));
    }
    if (param instanceof Concrete.NameParameter) {
      return bodyToLam(visitNameParameter((Concrete.NameParameter) param, letClause), typecheckLetClause(parameters.subList(1, parameters.size()), letClause, false), letClause);
    } else if (param instanceof Concrete.TypeParameter) {
      SingleDependentLink link = visitTypeParameter((Concrete.TypeParameter) param, null, null);
      return link == null ? null : bodyToLam(link, typecheckLetClause(parameters.subList(1, parameters.size()), letClause, false), letClause);
    } else {
      throw new IllegalStateException();
    }
  }

  private void getLetClauseName(Concrete.Pattern pattern, StringBuilder builder) {
    if (pattern instanceof Concrete.NamePattern) {
      Referable ref = ((Concrete.NamePattern) pattern).getRef();
      if (ref != null) {
        builder.append(ref.textRepresentation());
        return;
      }
    }

    boolean first = true;
    for (Concrete.Pattern subPattern : pattern.getPatterns()) {
      if (first) {
        first = false;
      } else {
        builder.append('_');
      }
      getLetClauseName(subPattern, builder);
    }
  }

  private Pair<HaveClause,Expression> typecheckLetClause(Concrete.LetClause clause, boolean isHave) {
    try (var ignore = new Utils.RefContextSaver(context, myLocalPrettifier)) {
      TypecheckingResult result = typecheckLetClause(clause.getParameters(), clause, true);
      if (result == null) {
        return null;
      }

      String name;
      if (clause.getPattern() instanceof Concrete.NamePattern && ((Concrete.NamePattern) clause.getPattern()).getRef() == null) {
        name = null;
      } else {
        StringBuilder builder = new StringBuilder();
        getLetClauseName(clause.getPattern(), builder);
        name = Renamer.getValidName(builder.toString(), Renamer.UNNAMED);
      }
      if (!(clause.getPattern() instanceof Concrete.NamePattern)) {
        result = TypeConstructorExpression.unfoldResult(result);
      }
      if (result.expression.isInstance(ErrorExpression.class)) {
        result.expression = new OfTypeExpression(result.expression, result.type);
      }
      return new Pair<>(TypedLetClause.make(!isHave, name, null, result.expression, result.type), result.type);
    }
  }

  private Pair<LetClausePattern, LocalExpressionPrettifier.Accessor> typecheckLetClausePattern(Concrete.Pattern pattern, TypecheckingResult tcResult, Set<Binding> bindings) {
    if (pattern instanceof Concrete.NamePattern) {
      Referable referable = ((Concrete.NamePattern) pattern).getRef();
      Concrete.Expression patternType = ((Concrete.NamePattern) pattern).type;
      if (patternType != null) {
        Type typeResult = checkType(((Concrete.NamePattern) pattern).type, Type.OMEGA);
        if (typeResult != null && !tcResult.type.isLessOrEquals(typeResult.getExpr(), myEquations, patternType)) {
          errorReporter.report(new TypeMismatchError(typeResult.getExpr(), tcResult.type, patternType));
        }
      }

      String name = referable == null ? null : referable.textRepresentation();
      if (referable != null) {
        Binding binding = new TypedEvaluatingBinding(name, tcResult.expression, tcResult.type);
        bindings.add(binding);
        addBinding(referable, binding);
      }
      return new Pair<>(new NameLetClausePattern(name), referable == null ? null : new LocalExpressionPrettifier.Accessor(referable, null, null));
    }

    tcResult = TypeConstructorExpression.unfoldResult(tcResult);
    Expression type = tcResult.type.normalize(NormalizationMode.WHNF);
    SigmaExpression sigma = type.cast(SigmaExpression.class);
    ClassCallExpression classCall = type.cast(ClassCallExpression.class);
    List<ClassField> notImplementedFields = classCall == null ? null : classCall.getNotImplementedFields();
    int numberOfPatterns = pattern.getPatterns().size();
    if (sigma == null && classCall == null || sigma != null && DependentLink.Helper.size(sigma.getParameters()) != numberOfPatterns || notImplementedFields != null && notImplementedFields.size() != numberOfPatterns) {
      errorReporter.report(new TypeMismatchError("Cannot match an expression with the pattern", DocFactory.text(sigma == null && classCall == null ? "A sigma type or a record" : sigma != null ? "A sigma type with " + numberOfPatterns + " fields" : "A records with " + numberOfPatterns + " not implemented fields"), type, pattern));
      return null;
    }

    List<LetClausePattern> patterns = new ArrayList<>();
    DependentLink link = sigma == null ? null : sigma.getParameters();
    ExprSubstitution substitution = new ExprSubstitution();
    LocalExpressionPrettifier.Accessor accessor = new LocalExpressionPrettifier.Accessor(null, sigma != null ? new ArrayList<>() : null, sigma == null ? new HashMap<>() : null);
    for (int i = 0; i < numberOfPatterns; i++) {
      assert link != null || notImplementedFields != null;
      Concrete.Pattern subPattern = pattern.getPatterns().get(i);
      Expression newType;
      ClassField field;
      if (link != null) {
        field = null;
        newType = link.getTypeExpr().subst(substitution);
      } else {
        field = notImplementedFields.get(i);
        newType = classCall.getDefinition().getFieldType(field, classCall.getLevels(field.getParentClass()), tcResult.expression);
      }
      Pair<LetClausePattern, LocalExpressionPrettifier.Accessor> pair = typecheckLetClausePattern(subPattern, new TypecheckingResult(link != null ? ProjExpression.make(tcResult.expression, i, link.isProperty()) : FieldCallExpression.make(notImplementedFields.get(i), tcResult.expression), newType), bindings);
      if (pair == null) {
        return null;
      }
      patterns.add(pair.proj1);
      if (field == null) {
        accessor.addProjAccessor(pair.proj2);
      } else {
        accessor.addFieldAccessor(field, pair.proj2);
      }
      if (link != null) {
        substitution.add(link, ProjExpression.make(tcResult.expression, i, link.isProperty()));
        link = link.getNext();
      }
    }

    return new Pair<>(sigma == null ? new RecordLetClausePattern(notImplementedFields, patterns) : new TupleLetClausePattern(patterns), accessor);
  }

  @Override
  public TypecheckingResult visitLet(Concrete.LetExpression expr, Expression expectedType) {
    try (var ignored = new Utils.RefContextSaver(context, myLocalPrettifier)) {
      try (var ignored1 = new Utils.ContextSaver(myInstancePool == null ? Collections.emptyList() : myInstancePool.getLocalInstances())) {
        List<? extends Concrete.LetClause> abstractClauses = expr.getClauses();
        List<HaveClause> clauses = new ArrayList<>(abstractClauses.size());
        Set<Binding> definedBindings = new HashSet<>();
        for (Concrete.LetClause clause : abstractClauses) {
          Pair<HaveClause, Expression> pair = typecheckLetClause(clause, expr.isHave());
          if (pair == null) {
            return null;
          }
          if (clause.getPattern() instanceof Concrete.NamePattern) {
            Referable referable = ((Concrete.NamePattern) clause.getPattern()).getRef();
            pair.proj1.setPattern(new NameLetClausePattern(referable == null ? null : referable.textRepresentation()));
            if (referable != null) {
              addBinding(referable, pair.proj1);
            }
          } else {
            addBinding(null, pair.proj1);
            Pair<LetClausePattern, LocalExpressionPrettifier.Accessor> patternPair = typecheckLetClausePattern(clause.getPattern(), new TypecheckingResult(new ReferenceExpression(pair.proj1), pair.proj2), definedBindings);
            if (patternPair == null) {
              return null;
            }
            pair.proj1.setPattern(patternPair.proj1);
            myLocalPrettifier.addBinding(pair.proj1, patternPair.proj2);
            if (pair.proj1.getExpression() instanceof ReferenceExpression refExpr) {
              myLocalPrettifier.addBinding(refExpr.getBinding(), patternPair.proj2);
            }
          }
          clauses.add(pair.proj1);

          if (myInstancePool != null && pair.proj2 instanceof ClassCallExpression && !((ClassCallExpression) pair.proj2).getDefinition().isRecord()) {
            ClassDefinition classDef = ((ClassCallExpression) pair.proj2).getDefinition();
            Expression instance = new ReferenceExpression(pair.proj1);
            myInstancePool.addLocalInstance(classDef.getClassifyingField() == null ? null : FieldCallExpression.make(classDef.getClassifyingField(), instance), classDef, instance);
          }
        }

        TypecheckingResult result = checkExpr(expr.getExpression(), expectedType);
        if (result == null) {
          return null;
        }

        Expression resultType = expectedType;
        if (resultType == null) {
          if (expr.isHave()) {
            definedBindings.addAll(clauses);
            CoreBinding binding = result.type.findFreeBindings(definedBindings);
            if (binding != null) {
              errorReporter.report(new TypecheckingError("\\have bindings occur freely in the result type", expr));
              return null;
            }
            resultType = result.type;
          } else {
            Set<Binding> freeVars = FreeVariablesCollector.getFreeVariables(result.type);
            boolean found = false;
            for (HaveClause clause : clauses) {
              if (freeVars.contains(clause)) {
                found = true;
                break;
              }
            }
            if (found) {
              ExprSubstitution substitution = new ExprSubstitution();
              for (HaveClause clause : clauses) {
                substitution.add(clause, clause.getExpression().subst(substitution));
              }
              resultType = result.type.subst(substitution);
            } else {
              resultType = result.type;
            }
          }
        }
        return new TypecheckingResult(expr.isGeneratedFromLambda ? result.expression : new LetExpression(expr.isStrict(), clauses, result.expression), resultType);
      }
    }
  }

  // Other

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean checkPath(TResult result, Concrete.Expression expr) {
    if (result instanceof DefCallResult && ((DefCallResult) result).getDefinition() == Prelude.PATH_CON) {
      errorReporter.report(new TypecheckingError("Expected an argument for 'path'", expr));
      return false;
    }
    if (result instanceof TypecheckingResult) {
      PathExpression pathExpr = ((TypecheckingResult) result).expression.cast(PathExpression.class);
      if (pathExpr != null) {
        Expression arg = pathExpr.getArgument();
        if (arg instanceof LamExpression && ((LamExpression) arg).getBody() instanceof GoalErrorExpression goalExpr && !((LamExpression) arg).getParameters().getNext().hasNext() && goalExpr.goalError.hasConditions()) {
          DependentLink param = ((LamExpression) arg).getParameters();
          ExprSubstitution leftSubst = new ExprSubstitution(param, ExpressionFactory.Left());
          ExprSubstitution rightSubst = new ExprSubstitution(param, ExpressionFactory.Right());
          goalExpr.goalError.addCondition(new Condition(null, leftSubst, AppExpression.make(arg, ExpressionFactory.Left(), true)));
          goalExpr.goalError.addCondition(new Condition(null, rightSubst, AppExpression.make(arg, ExpressionFactory.Right(), true)));
        }
      }
    }
    return true;
  }

  @Nullable
  @Override
  public TypecheckingResult defer(@NotNull MetaDefinition meta, @NotNull ContextData contextData, @NotNull CoreExpression type, boolean afterLevels) {
    if (!myAllowDeferredMetas) {
      return TypecheckingResult.fromChecked(meta.invokeMeta(this, contextData));
    }
    if (!meta.checkContextData(contextData, errorReporter)) {
      return null;
    }
    ConcreteExpression marker = contextData.getMarker();
    if (!(marker instanceof Concrete.Expression && type instanceof Expression expectedType)) {
      throw new IllegalArgumentException();
    }

    ContextDataImpl contextDataImpl = new ContextDataImpl((Concrete.Expression) marker, contextData.getArguments(), contextData.getCoclauses(), contextData.getClauses(), expectedType, contextData.getUserData());
    InferenceVariable inferenceVar = new MetaInferenceVariable(marker instanceof Concrete.ReferenceExpression ? ((Concrete.ReferenceExpression) marker).getReferent().getRefName() : "deferred", expectedType, (Concrete.Expression) marker, getAllBindings());
    (afterLevels ? myDeferredMetasAfterLevels : myDeferredMetasBeforeSolver).add(new DeferredMeta(meta, new LinkedHashMap<>(context), new LocalExpressionPrettifier(myLocalPrettifier), contextDataImpl, inferenceVar, errorReporter));
    return new TypecheckingResult(new InferenceReferenceExpression(inferenceVar), expectedType);
  }

  @Override
  public void allowDeferredMetas(boolean allow) {
    myAllowDeferredMetas = allow;
  }

  @Override
  public boolean deferredMetasAllowed() {
    return myAllowDeferredMetas;
  }

  private void fixCheckedExpression(TypecheckingResult result, Referable referable, Concrete.SourceNode sourceNode) {
    if (result == null || result.type != null) {
      return;
    }

    result.type = result.expression.getType();
    if (result.type == null) {
      TypecheckingError error = new TypeComputationError(getExpressionPrettifier(), referable, result.expression, sourceNode);
      errorReporter.report(error);
      result.type = new ErrorExpression(error);
    }
  }

  private TypecheckingResult invokeMeta(MetaDefinition meta, ContextData contextData) {
    try {
      return TypecheckingResult.fromChecked(meta.invokeMeta(this, contextData));
    } catch (MetaException e) {
      if (e.error.cause == null) {
        e.error.cause = contextData.getMarker();
      }
      errorReporter.report(e.error);
      ErrorExpression expr = new ErrorExpression(e.error);
      return new TypecheckingResult(expr, expr);
    }
  }

  private TypecheckingResult checkMeta(Concrete.ReferenceExpression refExpr, List<Concrete.Argument> arguments, Concrete.Coclauses coclauses, Expression expectedType) {
    MetaReferable metaRef = (MetaReferable) refExpr.getReferent();
    MetaDefinition meta = metaRef.getDefinition();
    if (meta == null) {
      errorReporter.report(new TypecheckingError("Meta '" + refExpr.getReferent().getRefName() + "' is empty", refExpr));
      return null;
    }
    MetaTopDefinition def = metaRef.getTypechecked();
    LevelSubstitution levelSubst = def == null ? null : typecheckLevels(def, refExpr, null, false).makeSubstitution(def);
    if (def != null && def.getParameters().hasNext()) {
      ExprSubstitution substitution = new ExprSubstitution();
      arguments = new ArrayList<>(arguments);
      int i = 0;
      List<? extends Boolean> typedParameters = def.getTypedParameters();
      DependentLink param = def.getParameters();
      for (int j = 0; j < typedParameters.size(); j++) {
        Boolean isTyped = typedParameters.get(j);
        boolean isExplicit = !isTyped || param.isExplicit();
        if (isExplicit && i >= arguments.size()) {
          int c = 1;
          for (j++; j < typedParameters.size(); j++) {
            if (typedParameters.get(j)) c++;
          }
          errorReporter.report(new TypecheckingError("Not enough arguments. Expected " + c + " more.", refExpr));
          return null;
        } else if (isExplicit && !arguments.get(i).isExplicit()) {
          errorReporter.report(new ArgumentExplicitnessError(true, refExpr));
          i++;
        } else if (!isExplicit && (i >= arguments.size() || arguments.get(i).isExplicit())) {
          Expression paramType = param.getTypeExpr().subst(substitution, levelSubst);
          Expression expr = InferenceReferenceExpression.make(myArgsInference.newInferenceVariable(paramType, refExpr), getEquations());
          substitution.add(param, expr);
          arguments.add(i++, new Concrete.Argument(new Concrete.ReferenceExpression(refExpr.getData(), new CoreReferable(null, new TypecheckingResult(expr, paramType))), true));
        } else {
          if (isTyped) {
            TypecheckingResult argResult = checkExpr(arguments.get(i).getExpression(), param.getTypeExpr().subst(substitution, levelSubst));
            if (argResult == null) return null;
            substitution.add(param, argResult.expression);
            arguments.set(i, new Concrete.Argument(new Concrete.ReferenceExpression(refExpr.getData(), new CoreReferable(null, argResult)), true));
          }
          i++;
        }
        if (isTyped) {
          param = param.getNext();
        }
      }
    }
    ContextData contextData = new ContextDataImpl(refExpr, arguments, coclauses, null, expectedType, null);
    if (!meta.checkContextData(contextData, errorReporter)) {
      return null;
    }

    int numberOfErrors = getNumberOfErrors();
    TypecheckingResult result = invokeMeta(meta, contextData);
    fixCheckedExpression(result, refExpr.getReferent(), refExpr);
    if (result != null) {
      return result.getType() == expectedType ? result : checkResult(expectedType, result, refExpr);
    }
    if (getNumberOfErrors() == numberOfErrors) {
      errorReporter.report(new TypecheckingError("Meta '" + refExpr.getReferent().getRefName() + "' failed", refExpr));
    }
    return null;
  }

  Concrete.Expression desugarClassApp(Concrete.Expression expr, boolean inferTailImplicits, Set<ClassField> implemented) {
    if (expr instanceof Concrete.AppExpression && ((Concrete.AppExpression) expr).getFunction() instanceof Concrete.ReferenceExpression) {
      return desugarClassApp((Concrete.ReferenceExpression) ((Concrete.AppExpression) expr).getFunction(), ((Concrete.AppExpression) expr).getArguments(), expr, null, inferTailImplicits, implemented);
    } else if (inferTailImplicits && expr instanceof Concrete.ReferenceExpression) {
      return desugarClassApp((Concrete.ReferenceExpression) expr, Collections.emptyList(), expr, null, true, implemented);
    } else {
      return expr;
    }
  }

  private Concrete.Expression desugarClassApp(Concrete.ReferenceExpression fun, List<Concrete.Argument> arguments, Concrete.Expression expr, List<SingleDependentLink> expectedParams, boolean inferTailImplicits, Set<ClassField> implemented) {
    Referable ref = fun.getReferent();
    if (!(ref instanceof TCDefReferable)) {
      return expr;
    }
    Definition def = ((TCDefReferable) ref).getTypechecked();
    if (!(def instanceof ClassDefinition classDef)) {
      return expr;
    }

    // Convert class call with arguments to class extension.
    List<Concrete.ClassFieldImpl> classFieldImpls = new ArrayList<>();
    List<ClassField> notImplementedFields = new ArrayList<>(classDef.getNotImplementedFields());

    int j = 0;
    for (int i = 0; i < arguments.size(); i++, j++) {
      if (j >= notImplementedFields.size()) {
        errorReporter.report(new TypecheckingError("Too many arguments. Class '" + ref.textRepresentation() + "' " + (notImplementedFields.isEmpty() ? "does not have fields" : "has only " + StringUtils.number(notImplementedFields.size(), " field")), arguments.get(i).expression));
        break;
      }

      ClassField field = notImplementedFields.get(j);
      boolean fieldExplicit = field.getReferable().isExplicitField();
      if (fieldExplicit && !arguments.get(i).isExplicit()) {
        errorReporter.report(new ArgumentExplicitnessError(true, arguments.get(i).expression));
        while (i < arguments.size() && !arguments.get(i).isExplicit()) {
          i++;
        }
        if (i == arguments.size()) {
          break;
        }
      }

      Concrete.Expression argument = arguments.get(i).expression;
      if (fieldExplicit == arguments.get(i).isExplicit()) {
        classFieldImpls.add(new Concrete.ClassFieldImpl(argument.getData(), field.getReferable(), argument, null));
      } else {
        classFieldImpls.add(new Concrete.ClassFieldImpl(argument.getData(), field.getReferable(), new Concrete.HoleExpression(argument.getData()), null));
        i--;
      }
    }

    Object data = arguments.isEmpty() ? fun.getData() : arguments.get(arguments.size() - 1).getExpression().getData();
    if (inferTailImplicits) {
      notImplementedFields.removeAll(implemented);
      int maxIndex;
      if (expectedParams != null) {
        int numberOfImplicitParams = 0;
        for (; numberOfImplicitParams < expectedParams.size(); numberOfImplicitParams++) {
          if (expectedParams.get(numberOfImplicitParams).isExplicit()) {
            break;
          }
        }
        int maxImplicitField = j;
        for (; maxImplicitField < notImplementedFields.size(); maxImplicitField++) {
          if (notImplementedFields.get(maxImplicitField).getReferable().isExplicitField() || !notImplementedFields.get(maxImplicitField).getReferable().isParameterField()) {
            break;
          }
        }
        maxIndex = maxImplicitField - numberOfImplicitParams;
      } else {
        maxIndex = notImplementedFields.size();
      }
      for (; j < maxIndex; j++) {
        ClassField field = notImplementedFields.get(j);
        if (field.getReferable().isExplicitField() || !field.getReferable().isParameterField()) {
          break;
        }
        if (!(field.getResultType() instanceof ClassCallExpression) || ((ClassCallExpression) field.getResultType()).getDefinition().isRecord()) {
          break;
        }

        classFieldImpls.add(new Concrete.ClassFieldImpl(data, field.getReferable(), new Concrete.HoleExpression(data), null));
      }
    }

    if (expectedParams != null && !expectedParams.isEmpty()) {
      List<Concrete.Parameter> lamParams = new ArrayList<>(expectedParams.size());
      for (SingleDependentLink param : expectedParams) {
        ClassField field = notImplementedFields.get(j);
        if (param.isExplicit() != field.getReferable().isExplicitField()) {
          lamParams = null;
          break;
        }
        Referable argRef = new LocalReferable(field.getName());
        lamParams.add(new Concrete.NameParameter(fun.getData(), param.isExplicit(), argRef));
        classFieldImpls.add(new Concrete.ClassFieldImpl(data, field.getReferable(), new Concrete.ReferenceExpression(data, argRef), null));
        j++;
      }
      if (lamParams != null) {
        return new Concrete.LamExpression(expr.getData(), lamParams, Concrete.ClassExtExpression.make(expr.getData(), fun, new Concrete.Coclauses(expr.getData(), classFieldImpls)));
      }
    }

    return classFieldImpls.isEmpty() ? fun : Concrete.ClassExtExpression.make(expr.getData(), fun, new Concrete.Coclauses(expr.getData(), classFieldImpls));
  }

  @Override
  public TypecheckingResult visitApp(Concrete.AppExpression expr, Expression expectedType) {
    return visitApp(expr, expectedType, true);
  }

  private TypecheckingResult visitApp(Concrete.AppExpression expr, Expression expectedType, boolean inferTailImplicits) {
    if (expr.getFunction() instanceof Concrete.ReferenceExpression refExpr && refExpr.getReferent() instanceof TCDefReferable && ((TCDefReferable) refExpr.getReferent()).getTypechecked() instanceof ClassDefinition) {
      List<SingleDependentLink> params = expectedType == null ? null : new ArrayList<>();
      if (expectedType != null) {
        expectedType = expectedType.normalizePi(params);
      }
      Concrete.Expression dExpr = desugarClassApp(refExpr, expr.getArguments(), expr, params, inferTailImplicits, Collections.emptySet());
      if (dExpr != expr) {
        return checkExpr(dExpr, expectedType);
      }
    }

    if (expr.getFunction() instanceof Concrete.ReferenceExpression && ((Concrete.ReferenceExpression) expr.getFunction()).getReferent() instanceof MetaReferable) {
      return checkMeta((Concrete.ReferenceExpression) expr.getFunction(), expr.getArguments(), null, expectedType);
    }

    Referable referable = expr.getFunction() instanceof Concrete.ReferenceExpression ? ((Concrete.ReferenceExpression) expr.getFunction()).getReferent() : null;
    Definition definition = referable instanceof TCDefReferable ? ((TCDefReferable) referable).getTypechecked() : null;
    if ((definition == Prelude.MOD || definition == Prelude.DIV_MOD) && expr.getArguments().size() == 2 && expr.getArguments().get(0).isExplicit() && expr.getArguments().get(1).isExplicit()) {
      TypecheckingResult arg1 = checkExpr(expr.getArguments().get(0).getExpression(), ExpressionFactory.Nat());
      TypecheckingResult arg2 = checkExpr(expr.getArguments().get(1).getExpression(), ExpressionFactory.Nat());
      if (arg1 == null || arg2 == null) {
        return null;
      }

      Type type;
      IntegerExpression intExpr = arg2.expression.cast(IntegerExpression.class);
      ConCallExpression conCall = arg2.expression.cast(ConCallExpression.class);
      if (intExpr != null && !intExpr.isZero() || conCall != null && conCall.getDefinition() == Prelude.SUC) {
        type = ExpressionFactory.Fin(arg2.expression);
      } else {
        type = ExpressionFactory.Nat();
      }

      boolean isMod = definition == Prelude.MOD;
      if (!isMod) {
        type = ExpressionFactory.divModType(type);
      }
      return checkResult(expectedType, new TypecheckingResult(FunCallExpression.make(isMod ? Prelude.MOD : Prelude.DIV_MOD, Levels.EMPTY, Arrays.asList(arg1.expression, arg2.expression)), type.getExpr()), expr);
    }

    if (expectedType != null && (definition == Prelude.ARRAY_AT && expr.getNumberOfExplicitArguments() == 0 || definition == Prelude.ARRAY_INDEX && expr.getNumberOfExplicitArguments() == 1 || definition == Prelude.ARRAY_CONS && expr.getNumberOfExplicitArguments() == 2)) {
      PiExpression piExpr = TypeConstructorExpression.unfoldType(expectedType).cast(PiExpression.class);
      if (piExpr != null && piExpr.getParameters().isExplicit()) {
        Referable lamParam = new LocalReferable("a");
        return visitLam(new Concrete.LamExpression(expr.getData(), Collections.singletonList(new Concrete.NameParameter(expr.getData(), true, lamParam)), Concrete.AppExpression.make(expr.getData(), expr, new Concrete.ReferenceExpression(expr.getData(), lamParam), true)), expectedType);
      }
    }

    TResult result = myArgsInference.infer(expr, expectedType);
    if (result == null || !checkPath(result, expr)) {
      return null;
    }

    if (definition instanceof DataDefinition || definition == Prelude.PATH_INFIX) {
      List<? extends Expression> args = null;
      if (result instanceof DefCallResult) {
        args = ((DefCallResult) result).getArguments();
      } else if (result instanceof TypecheckingResult) {
        Expression resultExpr = ((TypecheckingResult) result).expression;
        while (resultExpr instanceof AppExpression) {
          resultExpr = resultExpr.getFunction();
        }
        if (resultExpr instanceof DefCallExpression) {
          args = ((DefCallExpression) resultExpr).getDefCallArguments();
        }
      }
      if (args != null) {
        DataDefinition dataDef = definition instanceof DataDefinition ? (DataDefinition) definition : Prelude.PATH;
        for (int i = 0; i < args.size(); i++) {
          if (args.get(i) instanceof InferenceReferenceExpression && ((InferenceReferenceExpression) args.get(i)).getVariable() != null && dataDef.isCovariant(i)) {
            myEquations.solveLowerBounds(((InferenceReferenceExpression) args.get(i)).getVariable());
          }
        }
      }
    }

    if (result instanceof TypecheckingResult tcResult && tcResult.expression instanceof DefCallExpression && !(tcResult.expression instanceof FieldCallExpression)) {
      return checkResult(expectedType, tcResult, expr);
    }
    return tResultToResult(expectedType, result, expr);
  }

  @Override
  public TypecheckingResult visitUniverse(Concrete.UniverseExpression expr, Expression expectedType) {
    if (!isHBased() && expr.getHLevel() == null) {
      errorReporter.report(new TypecheckingError(GeneralError.Level.WARNING, "Universe can be replaced with \\Prop", expr));
      return new TypecheckingResult(new UniverseExpression(Sort.PROP), new UniverseExpression(Sort.SET0));
    }

    Level pLevel = expr.getPLevel() != null ? expr.getPLevel().accept(this, LevelVariable.PVAR) : null;
    Level hLevel = expr.getHLevel() != null ? expr.getHLevel().accept(this, LevelVariable.HVAR) : null;

    if (pLevel == null) {
      InferenceLevelVariable pl = new InferenceLevelVariable(LevelVariable.LvlType.PLVL, true, expr);
      getEquations().addVariable(pl);
      pLevel = new Level(pl);
    }

    if (hLevel == null) {
      InferenceLevelVariable hl = new InferenceLevelVariable(LevelVariable.LvlType.HLVL, true, expr);
      getEquations().addVariable(hl);
      hLevel = new Level(hl);
    }

    UniverseExpression universe = new UniverseExpression(new Sort(pLevel, hLevel));
    return checkResult(expectedType, new TypecheckingResult(universe, new UniverseExpression(universe.getSort().succ())), expr);
  }

  @Override
  public TypecheckingResult visitTyped(Concrete.TypedExpression expr, Expression expectedType) {
    Type type = checkType(expr.type, Type.OMEGA);
    if (type == null) {
      return checkExpr(expr.expression, expectedType);
    } else {
      Expression typeExpr = type.getExpr();
      TypecheckingResult result = checkExpr(expr.expression, typeExpr);
      if (result != null && typeExpr instanceof DataCallExpression && (((DataCallExpression) typeExpr).getDefinition() == Prelude.NAT || ((DataCallExpression) typeExpr).getDefinition() == Prelude.FIN)) {
        result.type = typeExpr;
      }
      return checkResult(expectedType, result, expr);
    }
  }

  @Override
  public @Nullable TypecheckingResult checkNumber(@NotNull BigInteger number, @Nullable CoreExpression expectedType, @NotNull ConcreteExpression marker) {
    if (!((expectedType == null || expectedType instanceof Expression) && marker instanceof Concrete.Expression)) {
      throw new IllegalArgumentException();
    }

    boolean isNegative = number.signum() < 0;
    IntegerExpression resultExpr;
    try {
      int value = number.intValueExact();
      resultExpr = new SmallIntegerExpression(isNegative ? -value : value);
    } catch (ArithmeticException e) {
      resultExpr = new BigIntegerExpression(isNegative ? number.negate() : number);
    }

    TypecheckingResult result;
    if (isNegative) {
      result = new TypecheckingResult(ExpressionFactory.Neg(resultExpr), ExpressionFactory.Int());
    } else {
      Expression ty;
      if (expectedType != null) {
        expectedType = expectedType.normalize(NormalizationMode.WHNF);
      }
      if (expectedType instanceof DataCallExpression && ((DataCallExpression) expectedType).getDefinition() == Prelude.FIN) {
        ty = ExpressionFactory.Fin(resultExpr.suc());
      } else {
        ty = ExpressionFactory.Nat();
      }
      result = new TypecheckingResult(resultExpr, ty);
    }
    return checkResult((Expression) expectedType, result, (Concrete.Expression) marker);
  }

  @Override
  public TypecheckingResult visitNumericLiteral(Concrete.NumericLiteral expr, Expression expectedType) {
    BigInteger number = expr.getNumber();
    if (myArendExtension != null) {
      var checker = myArendExtension.getLiteralTypechecker();
      if (checker != null) {
        int numberOfErrors = getNumberOfErrors();
        TypecheckingResult result = TypecheckingResult.fromChecked(checker.typecheckNumber(number, this, new ContextDataImpl(expr, Collections.emptyList(), null, null, expectedType, null)));
        if (result == null && getNumberOfErrors() == numberOfErrors) {
          errorReporter.report(new TypecheckingError("Cannot check number", expr));
        }
        return result;
      }
    }

    return checkNumber(number, expectedType, expr);
  }

  @Override
  public TypecheckingResult visitStringLiteral(Concrete.StringLiteral expr, Expression expectedType) {
    var string = expr.getUnescapedString();
    if (myArendExtension != null) {
      var checker = myArendExtension.getLiteralTypechecker();
      if (checker != null) {
        int numberOfErrors = getNumberOfErrors();
        TypecheckingResult result = TypecheckingResult.fromChecked(checker.typecheckString(string, this, new ContextDataImpl(expr, Collections.emptyList(), null, null, expectedType, null)));
        if (result == null && getNumberOfErrors() == numberOfErrors) {
          errorReporter.report(new TypecheckingError("Cannot check string", expr));
        }
        return result;
      }
    }

    return checkResult(expectedType, new TypecheckingResult(new StringExpression(string), ExpressionFactory.String()), expr);
  }

  @Override
  public <T> T withErrorReporter(@NotNull ErrorReporter errorReporter, @NotNull Function<ExpressionTypechecker, T> action) {
    MyErrorReporter originalErrorReport = this.errorReporter;
    this.errorReporter = new MyErrorReporter(errorReporter);
    try {
      return action.apply(this);
    } finally {
      this.errorReporter = originalErrorReport;
    }
  }

  @Override
  public <T> T withFreeBindings(@NotNull FreeBindingsModifier modifier, @NotNull Function<ExpressionTypechecker, T> action) {
    if (modifier.commands.isEmpty()) {
      return action.apply(this);
    }

    try (var ignored = new Utils.CompleteMapContextSaver<>(context)) {
      for (FreeBindingsModifier.Command command : modifier.commands) {
        switch (command.kind) {
          case ADD -> {
            for (Object binding : (Collection<?>) command.bindings) {
              if (binding instanceof Binding) {
                addBinding(null, (Binding) binding);
              } else if (binding instanceof Pair<?,?> pair) {
                if (!((pair.proj1 == null || pair.proj1 instanceof Referable) && pair.proj2 instanceof Binding)) {
                  throw new IllegalArgumentException();
                }
                addBinding((Referable) pair.proj1, (Binding) pair.proj2);
              } else {
                throw new IllegalArgumentException();
              }
            }
          }
          case ADD_PARAM -> {
            LinkList list = new LinkList();
            for (Object param : (Collection<?>) command.bindings) {
              if (!(param instanceof Concrete.Parameter)) {
                throw new IllegalArgumentException();
              }
              if (!visitParameter((Concrete.Parameter) param, null, null, list)) {
                return null;
              }
            }
          }
          case CLEAR, RETAIN, REMOVE -> {
            Set<?> bindings = (Set<?>) command.bindings;
            Set<Map.Entry<Referable, Binding>> removed = new HashSet<>();
            for (Map.Entry<Referable, Binding> entry : context.entrySet()) {
              if (!(entry.getKey() instanceof VeryFakeLocalReferable) && (bindings == null || command.kind == FreeBindingsModifier.Command.Kind.REMOVE && bindings.contains(entry.getValue()) || command.kind == FreeBindingsModifier.Command.Kind.RETAIN && !bindings.contains(entry.getValue()))) {
                removed.add(entry);
              }
            }
            for (var entry : removed) {
              removeBinding(entry.getKey());
              context.put(new VeryFakeLocalReferable(entry.getValue().getName()), entry.getValue());
            }
          }
          case REPLACE, REPLACE_REMOVE -> {
            Map<?, ?> replacement = (Map<?, ?>) command.bindings;
            Set<Map.Entry<Referable, Binding>> removed = command.kind == FreeBindingsModifier.Command.Kind.REPLACE_REMOVE ? new HashSet<>() : null;
            for (Map.Entry<Referable, Binding> entry : context.entrySet()) {
              Object newBinding = replacement.get(entry.getValue());
              if (newBinding != null) {
                if (!(newBinding instanceof Binding)) {
                  throw new IllegalArgumentException();
                }
                entry.setValue((Binding) newBinding);
              } else if (removed != null) {
                removed.add(entry);
              }
            }
            if (removed != null) {
              for (var entry : removed) {
                removeBinding(entry.getKey());
                context.put(new VeryFakeLocalReferable(entry.getValue().getName()), entry.getValue());
              }
            }
          }
        }
      }

      return action.apply(this);
    }
  }

  public void variableSolved(InferenceVariable variable) {
    if (mySavedState != null) {
      mySavedState.solvedVariables.add(variable);
    }
  }

  private void saveState() {
    ListErrorReporter listErrorReporter = new ListErrorReporter();
    TypecheckerState state = new TypecheckerState(errorReporter, myDeferredMetasBeforeSolver.size(), myDeferredMetasAfterLevels.size(), copyUserData(), mySavedState, listErrorReporter, myAllowDeferredMetas);
    errorReporter = new MyErrorReporter(listErrorReporter);
    myEquations.saveState(state);
    mySavedState = state;
  }

  private void restoreState() {
    mySavedState.listErrorReporter.reportTo(mySavedState.errorReporter);
    errorReporter = mySavedState.errorReporter;
    if (mySavedState.previousState != null) {
      mySavedState.previousState.solvedVariables.addAll(mySavedState.solvedVariables);
    }
    mySavedState = mySavedState.previousState;
  }

  @Override
  public <T> T withCurrentState(@NotNull Function<ExpressionTypechecker, T> action) {
    saveState();
    try {
      return action.apply(this);
    } finally {
      restoreState();
    }
  }

  @Override
  public void updateSavedState() {
    if (mySavedState == null) {
      throw new IllegalStateException();
    }

    mySavedState.listErrorReporter.reportTo(mySavedState.errorReporter);
    mySavedState.listErrorReporter.getErrorList().clear();
    if (mySavedState.previousState != null) {
      mySavedState.previousState.solvedVariables.addAll(mySavedState.solvedVariables);
    }
    TypecheckerState state = new TypecheckerState(mySavedState.errorReporter, myDeferredMetasBeforeSolver.size(), myDeferredMetasAfterLevels.size(), copyUserData(), mySavedState.previousState, mySavedState.listErrorReporter, mySavedState.allowDeferredMetas);
    myEquations.saveState(state);
    mySavedState = state;
  }

  private void loadState(TypecheckerState state) {
    state.listErrorReporter.getErrorList().clear();
    if (state.numberOfDeferredMetasBeforeSolver < myDeferredMetasBeforeSolver.size()) {
      myDeferredMetasBeforeSolver.subList(state.numberOfDeferredMetasBeforeSolver, myDeferredMetasBeforeSolver.size()).clear();
    }
    if (state.numberOfDeferredMetasAfterLevels < myDeferredMetasAfterLevels.size()) {
      myDeferredMetasAfterLevels.subList(state.numberOfDeferredMetasAfterLevels, myDeferredMetasAfterLevels.size()).clear();
    }
    setUserData(state.userDataHolder);
    myAllowDeferredMetas = state.allowDeferredMetas;

    for (InferenceVariable var : state.solvedVariables) {
      var.unsolve();
    }
    state.solvedVariables.clear();

    myEquations.loadState(state);
  }

  @Override
  public void loadSavedState() {
    if (mySavedState == null) {
      throw new IllegalStateException();
    }
    loadState(mySavedState);
  }

  @Override
  public boolean solveInferenceVariable(@NotNull CoreInferenceVariable variable, @NotNull CoreExpression expression) {
    if (!(variable instanceof InferenceVariable && expression instanceof Expression) || variable instanceof MetaInferenceVariable) {
      throw new IllegalArgumentException();
    }
    return myEquations.solve((InferenceVariable) variable, (Expression) expression);
  }

  @Override
  public @NotNull CoreInferenceReferenceExpression generateNewInferenceVariable(@NotNull String name, @NotNull CoreExpression type, @NotNull ConcreteSourceNode marker, boolean isSolvableFromEquations) {
    if (!(type instanceof Expression && marker instanceof Concrete.SourceNode)) {
      throw new IllegalArgumentException();
    }
    return new InferenceReferenceExpression(new UserInferenceVariable(name, (Expression) type, (Concrete.SourceNode) marker, getAllBindings(), isSolvableFromEquations));
  }

  @Override
  public @NotNull CoreSort generateSort(@NotNull ConcreteSourceNode marker) {
    if (!(marker instanceof Concrete.SourceNode)) {
      throw new IllegalArgumentException();
    }
    return Sort.generateInferVars(myEquations, true, (Concrete.SourceNode) marker);
  }

  @Override
  public @Nullable ConcreteExpression findInstance(@NotNull InstanceSearchParameters parameters, @Nullable UncheckedExpression classifyingExpression, @NotNull ConcreteSourceNode sourceNode) {
    if (!(sourceNode instanceof Concrete.SourceNode)) {
      throw new IllegalArgumentException();
    }
    return myInstancePool.findInstance(UncheckedExpressionImpl.extract(classifyingExpression), parameters, (Concrete.SourceNode) sourceNode, null, myDefinition);
  }

  @Override
  public @Nullable TypedExpression findInstance(@NotNull InstanceSearchParameters parameters, @Nullable UncheckedExpression classifyingExpression, @Nullable CoreExpression expectedType, @NotNull ConcreteSourceNode sourceNode) {
    if (!((expectedType == null || expectedType instanceof Expression) && sourceNode instanceof Concrete.SourceNode)) {
      throw new IllegalArgumentException();
    }
    return myInstancePool.findInstance(UncheckedExpressionImpl.extract(classifyingExpression), expectedType == null ? null : (Expression) expectedType, parameters, (Concrete.SourceNode) sourceNode, null, myDefinition);
  }

  @Override
  public void checkCancelled() {
    ComputationRunner.checkCanceled();
  }

  @Override
  public TypecheckingResult visitGoal(Concrete.GoalExpression expr, Expression expectedType) {
    List<GeneralError> errors = expr.errors;
    GoalSolver.CheckGoalResult goalResult = null;
    GoalSolver solver = expr.useGoalSolver ? expr.goalSolver : myArendExtension != null ? myArendExtension.getGoalSolver() : null;
    if (expr.expression != null || solver != null) {
      errors = new ArrayList<>(expr.errors);
      goalResult = withErrorReporter(new ListErrorReporter(errors), tc -> {
        if (solver == null) {
          return new GoalSolver.CheckGoalResult(expr.originalExpression, checkExpr(expr.expression, expectedType));
        } else {
          return solver.checkGoal(tc, expr, expectedType);
        }
      });
    }

    if (goalResult != null && (!(goalResult.concreteExpression == null || goalResult.concreteExpression instanceof Concrete.Expression) || !(goalResult.typedExpression == null || goalResult.typedExpression.getExpression() instanceof Expression))) {
      throw new IllegalArgumentException();
    }

    GoalError error = new GoalError(getExpressionPrettifier(), saveTypecheckingContext(), getBindingTypes(), expectedType, goalResult == null ? null : (Concrete.Expression) goalResult.concreteExpression, errors, solver, expr);
    errorReporter.report(error);
    Expression result = new GoalErrorExpression(goalResult == null || goalResult.typedExpression == null ? null : (Expression) goalResult.typedExpression.getExpression(), error);
    return new TypecheckingResult(result, expectedType != null && !(expectedType instanceof Type && ((Type) expectedType).isOmega()) ? expectedType : result);
  }

  protected Map<Binding, Expression> getBindingTypes() {
    Map<Binding, Expression> result = new HashMap<>();
    for (Binding binding : context.values()) {
      if (binding instanceof TypedHaveClause || binding instanceof TypedLetClause) {
        Expression type = binding instanceof TypedHaveClause ? ((TypedHaveClause) binding).type : ((TypedLetClause) binding).type;
        if (type != null) {
          result.put(binding, type);
        }
      }
    }
    return result;
  }

  @Override
  public TypecheckingResult visitBinOpSequence(Concrete.BinOpSequenceExpression expr, Expression expectedType) {
    throw new IllegalStateException();
  }

  @Override
  public TypecheckingResult visitApplyHole(Concrete.ApplyHoleExpression expr, Expression params) {
    errorReporter.report(new TypecheckingError("`__` not allowed here", expr));
    return null;
  }

  public static Expression getLevelExpression(Type type, int level) {
    if (level < -1) {
      return type.getExpr();
    }

    SingleDependentLink params = ExpressionFactory.singleParams(true, Arrays.asList("x" + (level + 2), "y" + (level + 2)), type);
    Sort sort = type.getSortOfType();
    return new PiExpression(sort, params, getLevelExpression(new TypeExpression(FunCallExpression.make(Prelude.PATH_INFIX, new LevelPair(sort.getPLevel(), sort.getHLevel()), Arrays.asList(type.getExpr(), new ReferenceExpression(params), new ReferenceExpression(params.getNext()))), sort), level - 1));
  }

  public Integer getExpressionLevel(DependentLink link, Expression type, Expression expr, Equations equations, Concrete.SourceNode sourceNode) {
    return getExpressionLevel(link, type, expr, equations, sourceNode, errorReporter);
  }

  public static Integer getExpressionLevel(DependentLink link, Expression type, Expression expr, Equations equations, Concrete.SourceNode sourceNode, ErrorReporter errorReporter) {
    boolean ok = expr != null;

    int level = -2;
    if (ok) {
      List<DependentLink> parameters = new ArrayList<>();
      for (; link.hasNext(); link = link.getNext()) {
        parameters.add(link);
      }

      Expression resultType = type == null ? null : type.getPiParameters(parameters, false);
      for (int i = 0; i < parameters.size(); i++) {
        link = parameters.get(i);
        if (link instanceof TypedDependentLink) {
          if (!CompareVisitor.compare(equations, CMP.EQ, link.getTypeExpr(), expr, Type.OMEGA, sourceNode)) {
            ok = false;
            break;
          }
        }

        List<Expression> pathArgs = new ArrayList<>();
        pathArgs.add(expr);
        pathArgs.add(new ReferenceExpression(link));
        i++;
        if (i >= parameters.size()) {
          ok = false;
          break;
        }
        link = parameters.get(i);
        if (!CompareVisitor.compare(equations, CMP.EQ, link.getTypeExpr(), expr, Type.OMEGA, sourceNode)) {
          ok = false;
          break;
        }

        pathArgs.add(new ReferenceExpression(link));
        expr = FunCallExpression.make(Prelude.PATH_INFIX, LevelPair.STD, pathArgs);
        level++;
      }

      if (ok && resultType != null && !CompareVisitor.compare(equations, CMP.EQ, resultType, expr, Type.OMEGA, sourceNode)) {
        ok = false;
      }
    }

    if (!ok || level < -1) {
      type = type == null ? null : type.normalize(NormalizationMode.WHNF);
      if (!(type instanceof ErrorExpression && ((ErrorExpression) type).isGoal())) {
        errorReporter.report(new TypecheckingError("\\level has wrong format", sourceNode));
        return null;
      }
      return -1;
    } else {
      return level;
    }
  }

  private static void replaceWithReferables(Set<Object> vars, Map<Referable, Binding> map) {
    for (Map.Entry<Referable, Binding> entry : map.entrySet()) {
      if (vars.remove(entry.getValue())) {
        vars.add(entry.getKey());
      }
    }
  }

  private Expression checkedSubst(Expression expr, ExprSubstitution substitution, Set<Binding> allowedBindings, Concrete.SourceNode sourceNode) {
    if (substitution.isEmpty()) {
      return expr;
    }

    expr = expr.accept(new SubstVisitor(new ExprSubstitution(), LevelSubstitution.EMPTY) {
      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public Expression visitFieldCall(FieldCallExpression expr, Void params) {
        if (!expr.getDefinition().isProperty() && expr.getArgument() instanceof ReferenceExpression) {
          Binding binding = ((ReferenceExpression) expr.getArgument()).getBinding();
          if (binding instanceof ClassCallExpression.ClassCallBinding) {
            Expression impl = ((ClassCallExpression.ClassCallBinding) binding).getTypeExpr().getImplementation(expr.getDefinition(), expr.getArgument());
            if (impl != null) {
              return impl.accept(this, null);
            }
          }
        }
        return super.visitFieldCall(expr, params);
      }
    }, null);

    Set<Object> foundVars = new LinkedHashSet<>();
    Expression result = CompareVisitor.checkedSubst(expr, substitution, allowedBindings, foundVars);
    if (result == null) {
      replaceWithReferables(foundVars, context);
      errorReporter.report(new ElimSubstError(null, foundVars, sourceNode));
      return expr;
    } else {
      return result;
    }
  }

  Integer minInteger(Integer int1, Integer int2) {
    return int1 == null ? int2 : int2 == null ? int1 : Integer.valueOf(Math.min(int1, int2));
  }

  @Override
  public TypecheckingResult visitCase(Concrete.CaseExpression expr, Expression expectedType) {
    if (expectedType == null && expr.getResultType() == null) {
      errorReporter.report(new CertainTypecheckingError(CertainTypecheckingError.Kind.CASE_RESULT_TYPE, expr));
      return null;
    }

    List<? extends Concrete.CaseArgument> caseArgs = expr.getArguments();
    LinkList list = new LinkList();
    List<Expression> expressions = new ArrayList<>(caseArgs.size());

    ExprSubstitution substitution = new ExprSubstitution();
    Type resultType = null;
    Expression resultExpr;
    Integer level = expr.level >= -1 ? expr.level : null;
    Expression resultTypeLevel = null;
    Map<Referable, Binding> origElimBindings = new HashMap<>();
    ExprSubstitution elimSubst = new ExprSubstitution();
    Set<Binding> allowedBindings = new HashSet<>();
    try (var ignored = new Utils.RefContextSaver(context, myLocalPrettifier)) {
      for (int i = 0; i < caseArgs.size(); i++) {
        Concrete.CaseArgument caseArg = caseArgs.get(i);
        Type argType = null;
        if (caseArg.type != null) {
          argType = checkType(caseArg.type, Type.OMEGA);
        }

        Expression argTypeExpr = argType == null ? null : argType.getExpr().subst(substitution);
        TypecheckingResult exprResult = checkExpr(caseArg.expression, argTypeExpr);
        if (exprResult == null) return null;
        if (caseArg.isElim && !(exprResult.expression instanceof ReferenceExpression)) {
          errorReporter.report(new TypecheckingError("Expected a variable", caseArg.expression));
          return null;
        }
        if (argType == null && Prelude.ARRAY_CONS != null) {
          boolean hasConstructors = false;
          for (Concrete.FunctionClause clause : expr.getClauses()) {
            if (clause.getPatterns().size() > i && clause.getPatterns().get(i) instanceof Concrete.ConstructorPattern conPattern && (conPattern.getConstructor() == Prelude.EMPTY_ARRAY.getReferable() || conPattern.getConstructor() == Prelude.ARRAY_CONS.getReferable())) {
              hasConstructors = true;
              break;
            }
          }
          if (hasConstructors && !caseArg.isElim) {
            Expression normType = exprResult.type.normalize(NormalizationMode.WHNF);
            if (normType instanceof ClassCallExpression classCall && classCall.getDefinition() == Prelude.DEP_ARRAY && classCall.isImplementedHere(Prelude.ARRAY_LENGTH)) {
              boolean ok = true;
              Sort lamSort = null;
              Expression type = null;
              Expression elementsType = classCall.getAbsImplementationHere(Prelude.ARRAY_ELEMENTS_TYPE);
              if (elementsType != null) {
                elementsType = elementsType.normalize(NormalizationMode.WHNF);
                if (elementsType instanceof LamExpression) {
                  type = elementsType.removeConstLam();
                  if (type == null) {
                    ok = false;
                  } else {
                    lamSort = ((LamExpression) elementsType).getResultSort();
                  }
                }
              }
              if (ok) {
                Map<ClassField, Expression> newImpls = new LinkedHashMap<>(classCall.getImplementedHere());
                newImpls.remove(Prelude.ARRAY_LENGTH);
                ClassCallExpression newClassCall = new ClassCallExpression(Prelude.DEP_ARRAY, classCall.getLevels(), newImpls, classCall.getSort(), classCall.getUniverseKind());
                if (type != null) newImpls.put(Prelude.ARRAY_ELEMENTS_TYPE, new LamExpression(lamSort, new TypedSingleDependentLink(true, null, ExpressionFactory.Fin(FieldCallExpression.make(Prelude.ARRAY_LENGTH, new ReferenceExpression(newClassCall.getThisBinding())))), type));
                newClassCall.setSort(Prelude.DEP_ARRAY.computeSort(newImpls, newClassCall.getThisBinding()));
                exprResult.type = newClassCall;
              }
            }
          }
        }
        if (argType == null || caseArg.isElim) {
          exprResult.type = checkedSubst(exprResult.type, elimSubst, allowedBindings, caseArg.expression);
        }
        Referable asRef = caseArg.isElim ? ((Concrete.ReferenceExpression) caseArg.expression).getReferent() : caseArg.referable;
        DependentLink link = ExpressionFactory.parameter(asRef == null ? null : asRef.textRepresentation(), argType != null ? argType : exprResult.type instanceof Type ? (Type) exprResult.type : new TypeExpression(exprResult.type, getSortOfType(exprResult.type, expr)));
        list.append(link);
        if (caseArg.isElim) {
          if (argTypeExpr != null) {
            errorReporter.report(new TypecheckingError("Explicit type annotation is not allowed with \\elim", caseArg.expression));
            return null;
          }
          Binding origBinding = ((ReferenceExpression) exprResult.expression).getBinding();
          origElimBindings.put(asRef, origBinding);
          elimSubst.add(origBinding, new ReferenceExpression(link));

          Set<Object> notEliminated = new HashSet<>();
          for (Binding allowedBinding : allowedBindings) {
            if (allowedBinding.getTypeExpr().findBinding(origBinding)) {
              notEliminated.add(allowedBinding);
            }
          }
          if (!notEliminated.isEmpty()) {
            replaceWithReferables(notEliminated, origElimBindings);
            replaceWithReferables(notEliminated, context);
            errorReporter.report(new ElimSubstError(asRef, notEliminated, caseArg.expression));
            return null;
          }
          allowedBindings.add(origBinding);
        }
        addBinding(asRef, link);
        Expression substExpr = exprResult.expression.subst(substitution);
        expressions.add(substExpr);
        substitution.add(link, substExpr);
      }

      if (expr.getResultType() != null) {
        resultType = checkType(expr.getResultType(), Type.OMEGA);
      }
      if (resultType == null && expectedType == null) {
        return null;
      }
      resultExpr = resultType != null ? resultType.getExpr() : !(expectedType instanceof Type && ((Type) expectedType).isOmega()) ? checkedSubst(expectedType, elimSubst, allowedBindings, expr.getResultType() != null ? expr.getResultType() : expr) : new UniverseExpression(Sort.generateInferVars(myEquations, false, expr));

      if (expr.getResultTypeLevel() != null) {
        TypecheckingResult levelResult = checkExpr(expr.getResultTypeLevel(), null);
        if (levelResult != null) {
          resultTypeLevel = levelResult.expression;
          level = minInteger(level, getExpressionLevel(EmptyDependentLink.getInstance(), levelResult.type, resultExpr, myEquations, expr.getResultTypeLevel()));
        }
      }
    }

    List<Referable> addedRefs = new ArrayList<>();
    for (Map.Entry<Referable, Binding> entry : origElimBindings.entrySet()) {
      removeBinding(entry.getKey());
      VeryFakeLocalReferable ref = new VeryFakeLocalReferable(entry.getValue().getName());
      context.put(ref, entry.getValue());
      addedRefs.add(ref);
    }

    // Check if the level of the result type is specified explicitly
    if (expr.getResultTypeLevel() == null && expr.getResultType() instanceof Concrete.TypedExpression) {
      Concrete.Expression typeType = ((Concrete.TypedExpression) expr.getResultType()).type;
      if (typeType instanceof Concrete.UniverseExpression universeType && universeType.getHLevel() instanceof Concrete.NumberLevelExpression) {
        level = minInteger(level, Math.max(((Concrete.NumberLevelExpression) universeType.getHLevel()).getNumber(), -1));
      }
    }

    // Try to infer level from \\use annotations of the definition in the result type.
    if (expr.getResultTypeLevel() == null) {
      DefCallExpression defCall = resultExpr.cast(DefCallExpression.class);
      Integer level2 = defCall == null ? null : defCall.getUseLevel();
      if (level2 == null) {
        defCall = resultExpr.getPiParameters(null, false).cast(DefCallExpression.class);
        if (defCall != null) {
          level2 = defCall.getUseLevel();
        }
      }
      level = minInteger(level, level2);
    }

    Level actualLevel;
    {
      Sort sort = resultType == null ? null : resultType.getSortOfType();
      actualLevel = sort != null ? sort.getHLevel() : Level.INFINITY;
    }

    List<ExtElimClause> clauses;
    try {
      PatternTypechecking patternTypechecking = new PatternTypechecking(PatternTypechecking.Mode.CASE, this, false, expressions, Collections.emptyList());
      clauses = patternTypechecking.typecheckClauses(expr.getClauses(), list.getFirst(), resultExpr);
    } finally {
      for (Referable ref : addedRefs) {
        removeBinding(ref);
      }
      context.putAll(origElimBindings);
    }
    if (clauses == null) {
      return null;
    }
    ElimBody elimBody = new ElimTypechecking(errorReporter, myEquations, resultExpr, PatternTypechecking.Mode.CASE, level, actualLevel, expr.isSCase(), expr.getClauses(), 0, expr).typecheckElim(clauses, list.getFirst());
    if (elimBody == null) {
      return null;
    }

    if (!(expr.isSCase() && actualLevel.isProp())) {
      new ConditionsChecking(myEquations, errorReporter, expr).check(clauses, expr.getClauses(), elimBody);
    }
    TypecheckingResult result = new TypecheckingResult(new CaseExpression(expr.isSCase(), list.getFirst(), resultExpr, resultTypeLevel, elimBody, expressions), resultType != null ? resultExpr.subst(substitution) : expectedType instanceof Type && ((Type) expectedType).isOmega() ? resultExpr : expectedType);
    return resultType == null ? result : checkResult(expectedType, result, expr);
  }

  @Override
  public TypecheckingResult visitEval(Concrete.EvalExpression expr, Expression expectedType) {
    TypecheckingResult result = checkExpr(expr.getExpression(), expr.isPEval() ? null : expectedType);
    if (result == null) {
      return null;
    }

    FunCallExpression funCall = result.expression instanceof FunCallExpression ? (FunCallExpression) result.expression : null;
    CaseExpression caseExpr = funCall != null ? null : result.expression instanceof CaseExpression ? (CaseExpression) result.expression : null;
    if ((caseExpr == null || !caseExpr.isSCase()) && (funCall == null || funCall.getDefinition().getKind() != CoreFunctionDefinition.Kind.SFUNC)) {
      errorReporter.report(new TypecheckingError(
        funCall != null ? "Expected a function (defined as \\sfunc) applied to arguments" :
        caseExpr != null ? "Expected an \\scase expression" :
          "Expected a function or an \\scase expression", expr.getExpression()));
      return null;
    }
    if (funCall != null && !(funCall.getDefinition().getActualBody() instanceof ElimBody || funCall.getDefinition().getActualBody() instanceof Expression)) {
      errorReporter.report(new FunctionWithoutBodyError(funCall.getDefinition(), expr.getExpression()));
      return null;
    }

    PEvalExpression pEvalResult = new PEvalExpression(result.expression);
    Expression normExpr = pEvalResult.eval();
    if (normExpr == null) {
      errorReporter.report(new TypecheckingError("Expression does not evaluate", expr.getExpression()));
      return null;
    }

    if (!expr.isPEval()) {
      return new TypecheckingResult(normExpr, result.type);
    }

    Expression typeType = result.type.getType();
    if (typeType == null) {
      errorReporter.report(new TypecheckingError("Cannot infer the universe of the type of the expression", expr.getExpression()));
      return null;
    }

    Sort sort;
    typeType = typeType.normalize(NormalizationMode.WHNF);
    UniverseExpression universe = typeType.cast(UniverseExpression.class);
    if (universe != null) {
      sort = universe.getSort();
    } else {
      sort = Sort.generateInferVars(myEquations, false, expr.getExpression());
      myEquations.addEquation(typeType, new UniverseExpression(sort), Type.OMEGA, CMP.LE, expr.getExpression(), typeType.getStuckInferenceVariable(), null);
    }

    List<Expression> args = new ArrayList<>(3);
    args.add(result.type);
    args.add(result.expression);
    args.add(normExpr);
    return checkResult(expectedType, new TypecheckingResult(pEvalResult, FunCallExpression.make(Prelude.PATH_INFIX, new LevelPair(sort.getPLevel(), sort.getHLevel()), args)), expr);
  }

  @Override
  public TypecheckingResult visitBox(Concrete.BoxExpression expr, Expression expectedType) {
    TypecheckingResult result = checkExpr(expr.getExpression(), expectedType);
    if (result == null) return null;
    Expression typeType = result.type.getType();
    Expression expectedTypeType = new UniverseExpression(Sort.PROP);
    if (!new CompareVisitor(myEquations, CMP.LE, expr).compare(typeType, expectedTypeType, null, false)) {
      Sort sort = typeType.toSort();
      errorReporter.report(sort != null ? new TypecheckingError("The type of the expression should live in \\Prop, but lives in " + sort, expr) : new TypeMismatchError("The type of the expression does not live in \\Prop", expectedTypeType, typeType, expr));
      return null;
    }
    return new TypecheckingResult(BoxExpression.make(result.expression, result.type), result.type);
  }
}
