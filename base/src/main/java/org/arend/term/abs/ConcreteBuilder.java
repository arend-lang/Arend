package org.arend.term.abs;

import org.arend.core.context.binding.LevelVariable;
import org.arend.error.CountingErrorReporter;
import org.arend.error.DummyErrorReporter;
import org.arend.error.ParsingError;
import org.arend.ext.concrete.definition.FunctionKind;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.typing.TypingInfoVisitor;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.Fixity;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.DefinableMetaDefinition;
import org.arend.typechecking.error.local.LocalErrorReporter;
import org.arend.util.SingletonList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;

@SuppressWarnings("WeakerAccess")
public class ConcreteBuilder implements AbstractDefinitionVisitor<Concrete.ResolvableDefinition>, AbstractExpressionVisitor<Void, Concrete.Expression>, AbstractLevelExpressionVisitor<LevelVariable, Concrete.LevelExpression> {
  private final LocalErrorReporter myErrorReporter;
  private final LocatedReferable myParent;
  private final TCDefReferable myDefinition;
  private final Map<Abstract.AbstractLocatedReferable, LocatedReferableImpl> myResolved;
  private GeneralError.Level myErrorLevel;

  private ConcreteBuilder(ErrorReporter errorReporter, TCDefReferable definition, LocatedReferable parent, Map<Abstract.AbstractLocatedReferable, LocatedReferableImpl> resolved) {
    myDefinition = definition;
    myParent = parent;
    myErrorReporter = new LocalErrorReporter(myDefinition, errorReporter) {
      @Override
      public void report(GeneralError error) {
        if (myErrorLevel == null || error.level.ordinal() > myErrorLevel.ordinal()) {
          myErrorLevel = error.level;
        }
        super.report(error);
      }
    };
    myResolved = resolved;
  }

  protected ConcreteBuilder(ErrorReporter errorReporter, TCDefReferable definition) {
    this(errorReporter, definition, null, null);
  }

  private static LocatedReferableImpl convertReferable(Abstract.AbstractLocatedReferable referable, LocatedReferable parent, Map<Abstract.AbstractLocatedReferable, LocatedReferableImpl> parentResolved) {
    if (referable == null) return null;
    if (parentResolved != null) {
      LocatedReferableImpl resolved = parentResolved.get(referable);
      if (resolved != null) {
        return resolved;
      }
    }

    return new LocatedReferableImpl(referable, referable.getAccessModifier(), referable.getPrecedence(), referable.getRefName(), referable.getAliasPrecedence(), referable.getAliasName(), parent, referable.getKind());
  }

  private static InternalReferableImpl convertInternalReferable(Abstract.AbstractLocatedReferable referable, LocatedReferable parent, boolean isVisible) {
    return new InternalReferableImpl(referable, referable.getAccessModifier(), referable.getPrecedence(), referable.getRefName(), referable.getAliasPrecedence(), referable.getAliasName(), isVisible, parent, referable.getKind());
  }

  private static LocatedReferableImpl convertField(Abstract.ClassField classField, LocatedReferable parent) {
    Abstract.AbstractLocatedReferable referable = classField.getReferable();
    return referable == null ? null : new FieldReferableImpl(referable, referable.getAccessModifier(), referable.getPrecedence(), referable.getRefName(), referable.getAliasPrecedence(), referable.getAliasName(), classField.isExplicitField(), classField.isParameterField(), false, (TCDefReferable) parent);
  }

  private static LocatedReferable convertFileReferable(Abstract.AbstractLocatedReferable referable, ModuleLocation module, LocatedReferable parent) {
    return parent == null && module != null ? new DataModuleReferable(referable, module) : convertReferable(referable, parent, null);
  }

  private static MetaReferable convertMetaReferable(Abstract.AbstractLocatedReferable referable, LocatedReferable parent) {
    return new MetaReferable(referable, referable.getAccessModifier(), referable.getPrecedence(), referable.getRefName(), referable.getAliasPrecedence(), referable.getAliasName(), null, null, parent);
  }

  public static @NotNull Concrete.ResolvableDefinition convert(Abstract.Definition definition, LocatedReferable parent, ErrorReporter errorReporter, Map<Abstract.AbstractLocatedReferable, LocatedReferableImpl> resolved, Map<Abstract.AbstractLocatedReferable, LocatedReferableImpl> parentResolved) {
    TCDefReferable referable = definition instanceof Abstract.MetaDefinition ? convertMetaReferable(definition.getReferable(), parent) : convertReferable(definition.getReferable(), parent, parentResolved);
    ConcreteBuilder builder = new ConcreteBuilder(errorReporter, referable, parent, resolved);
    Concrete.ResolvableDefinition result = definition.accept(builder);
    if (builder.myErrorLevel != null) {
      result.setStatus(builder.myErrorLevel);
    }
    return result;
  }

  public static @Nullable Concrete.Expression convertWithoutErrors(Abstract.Expression expression) {
    CountingErrorReporter errorReporter = new CountingErrorReporter(DummyErrorReporter.INSTANCE);
    Concrete.Expression result = expression.accept(new ConcreteBuilder(errorReporter, null), null);
    return errorReporter.getErrorsNumber() == 0 ? result : null;
  }

  public static @NotNull Concrete.Expression convertExpression(Abstract.Expression expression, ErrorReporter errorReporter) {
    return expression.accept(new ConcreteBuilder(errorReporter, null), null);
  }

  public static @NotNull Concrete.Expression convertExpression(Abstract.Expression expression) {
    return convertExpression(expression, DummyErrorReporter.INSTANCE);
  }

  public static @NotNull Concrete.Pattern convertPattern(Abstract.Pattern clause, ErrorReporter errorReporter, TCDefReferable definition) {
    return (new ConcreteBuilder(errorReporter, definition)).buildPattern(clause);
  }

  // Group

  // TODO[server2]: Do not report actual errors in ConcreteBuilder
  public static @NotNull ConcreteGroup convertGroup(Abstract.Group group, ModuleLocation module, ErrorReporter errorReporter) {
    return buildGroup(group, module, null, null, null, errorReporter, null);
  }

  private static ConcreteGroup buildGroup(Abstract.Group group, ModuleLocation module, LocatedReferable parent, Concrete.Definition parentDef, TCDefReferable enclosingClass, ErrorReporter errorReporter, Map<Abstract.AbstractLocatedReferable, LocatedReferableImpl> parentResolved) {
    Abstract.Definition definition = group.getGroupDefinition();
    Map<Abstract.AbstractLocatedReferable, LocatedReferableImpl> resolved = new HashMap<>();
    Concrete.ResolvableDefinition concrete = definition == null ? null : convert(definition, parent, errorReporter, resolved, parentResolved);
    LocatedReferable referable = concrete == null ? convertFileReferable(group.getReferable(), module, parent) : concrete.getData();

    List<ConcreteGroup> dynamicGroups = new ArrayList<>();
    for (Abstract.Group subgroup : group.getDynamicSubgroups()) {
      dynamicGroups.add(buildGroup(subgroup, module, referable, concrete instanceof Concrete.Definition def ? def : null, referable instanceof TCDefReferable ? (TCDefReferable) referable : null, errorReporter, resolved));
    }

    List<ConcreteStatement> statements = new ArrayList<>();
    for (Abstract.Statement statement : group.getStatements()) {
      Abstract.Group subgroup = statement.getGroup();
      statements.add(new ConcreteStatement(subgroup == null ? null : buildGroup(subgroup, module, referable, concrete instanceof Concrete.Definition def ? def : null, enclosingClass, errorReporter, resolved), statement.getNamespaceCommand(), buildLevelsDefinition(statement.getPLevelsDefinition(), true, referable), buildLevelsDefinition(statement.getHLevelsDefinition(), false, referable)));
    }

    if (concrete instanceof Concrete.Definition cDef && parentDef instanceof Concrete.ClassDefinition && cDef.getUseParent() == parentDef.getData()) {
      parentDef.addUsedDefinition(concrete.getData());
    }

    if (definition != null && definition.withUse()) {
      if (parentDef == null) {
        errorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "\\use must belong to a \\where-block of a definition", definition));
      } else {
        parentDef.addUsedDefinition(concrete.getData());
        if (concrete instanceof Concrete.Definition def) {
          def.setUseParent(parentDef.getData());
        }
      }
    }

    if (concrete instanceof Concrete.Definition def && enclosingClass != null) {
      def.enclosingClass = enclosingClass;
    }

    return new ConcreteGroup(group.getDescription(), referable, concrete, statements, dynamicGroups, buildExternalParameters(parentDef));
  }

  private static Concrete.LevelsDefinition buildLevelsDefinition(Abstract.LevelParameters parameters, boolean isPLevels, LocatedReferable parent) {
    if (parameters == null) return null;
    boolean isIncreasing = parameters.isIncreasing();
    List<TCLevelReferable> referables = new ArrayList<>();
    LevelDefinition levelDefinition = new LevelDefinition(isPLevels, isIncreasing, referables, parent);
    for (Abstract.AbstractReferable referable : parameters.getReferables()) {
      referables.add(new TCLevelReferable(referable, referable.getRefName(), levelDefinition));
    }
    return new Concrete.LevelsDefinition(parameters.getData(), referables, isIncreasing, isPLevels);
  }

  private static List<ParameterReferable> buildExternalParameters(Concrete.Definition definition) {
    if (definition == null) return Collections.emptyList();
    List<? extends Concrete.Parameter> parameters = definition.getParameters();
    if (parameters.isEmpty()) return Collections.emptyList();

    Set<String> eliminated = new HashSet<>();
    Concrete.FunctionBody body = definition instanceof Concrete.BaseFunctionDefinition ? ((Concrete.BaseFunctionDefinition) definition).getBody() : null;
    if (body instanceof Concrete.ElimFunctionBody) {
      if (body.getEliminatedReferences().isEmpty()) return Collections.emptyList();
      for (Concrete.ReferenceExpression reference : body.getEliminatedReferences()) {
        eliminated.add(reference.getReferent().getRefName());
      }
    }

    List<ParameterReferable> result = new ArrayList<>();
    int i = 0;
    for (Concrete.Parameter parameter : parameters) {
      for (Referable referable : parameter.getReferableList()) {
        if (referable != null && !eliminated.contains(referable.getRefName())) {
          result.add(new ParameterReferable(definition.getData(), i, referable, TypingInfoVisitor.resolveAbstractBody(parameter.getType())));
        }
        i++;
      }
    }
    return result;
  }

  // Definition

  private static List<LevelReferable> getLevelParametersRefs(Abstract.LevelParameters params, boolean isPLevels) {
    if (params == null) return null;
    List<LevelReferable> result = new ArrayList<>();
    for (Abstract.AbstractReferable ref : params.getReferables()) {
      result.add(new DataLevelReferable(ref, ref.getRefName(), isPLevels));
    }
    return result;
  }

  private Concrete.LevelParameters visitLevelParameters(Abstract.LevelParameters params, boolean isPLevels) {
    if (params == null) return null;
    Boolean increasing = null;
    for (Abstract.Comparison comparison : params.getComparisonList()) {
      boolean inc = comparison == Abstract.Comparison.LESS_OR_EQUALS;
      if (increasing == null) {
        increasing = inc;
      } else if (increasing != inc) {
        myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "Level parameters must be linearly ordered", comparison));
      }
    }
    return new Concrete.LevelParameters(params.getData(), getLevelParametersRefs(params, isPLevels), increasing == null || increasing);
  }

  @Override
  public DefinableMetaDefinition visitMeta(Abstract.MetaDefinition def) {
    var parameters = buildParameters(def.getParameters(), false);
    var term = def.getTerm();
    var body = term != null ? term.accept(this, null) : null;
    MetaReferable metaRef = (MetaReferable) myDefinition;
    var definition = new DefinableMetaDefinition(metaRef, visitLevelParameters(def.getPLevelParameters(), true), visitLevelParameters(def.getHLevelParameters(), false), parameters, body);
    if (term != null) { // if term == null, it may be a generated meta, in which case we shouldn't replace its definition
      metaRef.setDefinition(definition);
    }
    return definition;
  }

  @Override
  public Concrete.BaseFunctionDefinition visitFunction(Abstract.FunctionDefinition def) {
    Concrete.FunctionBody body;
    Abstract.Expression term = def.getTerm();
    if (def.withTerm()) {
      if (term == null) {
        myErrorLevel = GeneralError.Level.ERROR;
        Abstract.AbstractLocatedReferable ref = def.getReferable();
        body = new Concrete.TermFunctionBody(ref, new Concrete.ErrorHoleExpression(ref, null));
      } else {
        body = new Concrete.TermFunctionBody(term.getData(), term.accept(this, null));
      }
    } else if (def.isCowith()) {
      List<Concrete.CoClauseElement> elements = new ArrayList<>();
      for (Abstract.CoClauseElement element : def.getCoClauseElements()) {
        if (element instanceof Abstract.ClassFieldImpl) {
          buildImplementation(def, (Abstract.ClassFieldImpl) element, elements);
        } else {
          myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "Unknown coclause element", element));
        }
      }
      body = new Concrete.CoelimFunctionBody(myDefinition, elements);
    } else {
      body = new Concrete.ElimFunctionBody(myDefinition, buildReferences(def.getEliminatedExpressions()), buildClauses(def.getClauses()));
    }

    List<Concrete.Parameter> parameters = buildParameters(def.getParameters(), true);
    Abstract.Expression resultType = def.getResultType();
    Abstract.Expression resultTypeLevel = checkResultTypeLevel(resultType, def.getResultTypeLevel());
    Concrete.Expression type = resultType == null ? null : resultType.accept(this, null);
    Concrete.Expression typeLevel = resultTypeLevel == null ? null : resultTypeLevel.accept(this, null);

    FunctionKind kind = def.getFunctionKind();

    Concrete.FunctionDefinition result;
    if (kind.isCoclause() && myParent instanceof TCDefReferable) {
      Abstract.Reference implementedField = def.getImplementedField();
      result = new Concrete.CoClauseFunctionDefinition(kind, myDefinition, (TCDefReferable) myParent, implementedField == null ? null : implementedField.getReferent(), parameters, type, typeLevel, body);
    } else {
      result = new Concrete.FunctionDefinition(def.getFunctionKind(), myDefinition, visitLevelParameters(def.getPLevelParameters(), true), visitLevelParameters(def.getHLevelParameters(), false), parameters, type, typeLevel, body);
    }
    return result;
  }

  @Override
  public Concrete.DataDefinition visitData(Abstract.DataDefinition def) {
    Abstract.Expression absUniverse = def.getUniverse();
    Concrete.Expression universe = absUniverse == null ? null : absUniverse.accept(this, null);
    if (universe != null && !(universe instanceof Concrete.UniverseExpression)) {
      myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "Expected a universe", universe.getData()));
    }

    List<Concrete.TypeParameter> typeParameters = buildTypeParameters(def.getParameters(), true);
    Collection<? extends Abstract.ConstructorClause> absClauses = def.getClauses();
    List<Concrete.ConstructorClause> clauses = new ArrayList<>(absClauses.size());
    Collection<? extends Abstract.Reference> elimExpressions = def.getEliminatedExpressions();

    for (Abstract.ConstructorClause clause : absClauses) {
      Collection<? extends Abstract.Constructor> absConstructors = clause.getConstructors();
      if (absConstructors.isEmpty()) {
        myErrorLevel = GeneralError.Level.ERROR;
        continue;
      }

      List<Concrete.Constructor> constructors = new ArrayList<>(absConstructors.size());
      for (Abstract.Constructor constructor : absConstructors) {
        Concrete.Constructor cons = new Concrete.Constructor(convertInternalReferable(constructor.getReferable(), myDefinition, true), buildTypeParameters(constructor.getParameters(), true), buildReferences(constructor.getEliminatedExpressions()), buildClauses(constructor.getClauses()), constructor.isCoerce());
        Abstract.Expression resultType = constructor.getResultType();
        if (resultType != null) {
          cons.setResultType(resultType.accept(this, null));
        }
        constructors.add(cons);
      }

      Collection<? extends Abstract.Pattern> patterns = clause.getPatterns();
      clauses.add(new Concrete.ConstructorClause(clause.getData(), patterns.isEmpty() ? null : buildPatterns(patterns), constructors));
    }

    return new Concrete.DataDefinition(myDefinition, visitLevelParameters(def.getPLevelParameters(), true), visitLevelParameters(def.getHLevelParameters(), false), typeParameters, elimExpressions == null ? null : buildReferences(elimExpressions), def.isTruncated(), universe instanceof Concrete.UniverseExpression ? (Concrete.UniverseExpression) universe : null, clauses);
  }

  public void buildClassParameters(Collection<? extends Abstract.FieldParameter> absParameters, Concrete.ClassDefinition classDef, List<Concrete.ClassElement> elements) {
    for (Abstract.FieldParameter absParameter : absParameters) {
      Concrete.Parameter parameter = buildParameter(absParameter, false, false);
      if (parameter.getType() != null) {
        boolean forced = absParameter.isClassifying();
        boolean explicit = parameter.isExplicit();
        for (Referable referable : parameter.getReferableList()) {
          if (referable instanceof FieldReferableImpl) {
            if (forced || explicit) {
              setClassifyingField(classDef, (FieldReferableImpl) referable, parameter, forced);
            }
            elements.add(new Concrete.ClassField((FieldReferableImpl) referable, explicit, absParameter.getClassFieldKind(), new ArrayList<>(), parameter.getType(), null, absParameter.isCoerce()));
          } else {
            myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "Incorrect field parameter", referable));
          }
        }
      } else {
        myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "Expected a typed parameter", parameter.getData()));
      }
    }
  }

  private void setClassifyingField(Concrete.ClassDefinition classDef, FieldReferable field, Object cause, boolean isForced) {
    if (isForced && classDef.isForcedClassifyingField()) {
      myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "Class can have at most one classifying field", cause));
    } else if (isForced && !classDef.isForcedClassifyingField() || classDef.getClassifyingField() == null) {
      classDef.setClassifyingField(field, isForced);
    }
  }

  @Override
  public Concrete.Definition visitClass(Abstract.ClassDefinition def) {
    List<Concrete.ClassElement> elements = new ArrayList<>();
    Concrete.ClassDefinition classDef = new Concrete.ClassDefinition(myDefinition, visitLevelParameters(def.getPLevelParameters(), true), visitLevelParameters(def.getHLevelParameters(), false), def.isRecord(), def.withoutClassifying(), buildReferenceExpressions(def.getSuperClasses()), elements);
    buildClassParameters(def.getParameters(), classDef, elements);

    for (Abstract.ClassElement element : def.getClassElements()) {
      if (element instanceof Abstract.ClassField field) {
        Abstract.Expression resultType = field.getResultType();
        TCDefReferable fieldRef = convertField(field, myDefinition);
        if (resultType == null || !(fieldRef instanceof FieldReferableImpl)) {
          myErrorLevel = GeneralError.Level.ERROR;
          if (fieldRef != null && !(fieldRef instanceof FieldReferableImpl)) {
            myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "Incorrect field", fieldRef));
          }
        } else {
          List<? extends Abstract.Parameter> parameters = field.getParameters();
          Concrete.Expression type = resultType.accept(this, null);
          Abstract.Expression resultTypeLevel = field.getResultTypeLevel();
          Concrete.Expression typeLevel = resultTypeLevel == null ? null : resultTypeLevel.accept(this, null);
          elements.add(new Concrete.ClassField((FieldReferableImpl) fieldRef, true, field.getClassFieldKind(), buildTypeParameters(parameters, false), type, typeLevel, field.isCoerce()));
          if (field.isClassifying()) {
            setClassifyingField(classDef, (FieldReferable) fieldRef, field, true);
          }
        }
      } else if (element instanceof Abstract.ClassFieldImpl) {
        buildImplementation(def, (Abstract.ClassFieldImpl) element, elements);
      } else if (element instanceof Abstract.OverriddenField field) {
        Abstract.Reference ref = field.getOverriddenField();
        Abstract.Expression type = field.getResultType();
        if (ref == null || type == null) {
          continue;
        }
        Abstract.Expression typeLevel = field.getResultTypeLevel();
        elements.add(new Concrete.OverriddenField(field.getData(), ref.getReferent(), buildTypeParameters(field.getParameters(), false), type.accept(this, null), typeLevel == null ? null : typeLevel.accept(this, null)));
      } else {
        myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "Unknown class element", element));
      }
    }

    return classDef;
  }

  public Concrete.ReferenceExpression buildReference(Abstract.Reference reference) {
    return new Concrete.ReferenceExpression(reference.getData(), reference.getReferent());
  }

  public List<Concrete.ReferenceExpression> buildReferences(Collection<? extends Abstract.Reference> absElimExpressions) {
    List<Concrete.ReferenceExpression> elimExpressions = new ArrayList<>(absElimExpressions.size());
    for (Abstract.Reference reference : absElimExpressions) {
      elimExpressions.add(buildReference(reference));
    }
    return elimExpressions;
  }

  public List<Concrete.ReferenceExpression> buildReferenceExpressions(Collection<? extends Abstract.ReferenceExpression> absElimExpressions) {
    List<Concrete.ReferenceExpression> elimExpressions = new ArrayList<>(absElimExpressions.size());
    for (Abstract.ReferenceExpression expr : absElimExpressions) {
      elimExpressions.add(new Concrete.ReferenceExpression(expr.getData(), expr.getReferent(), visitLevels(expr.getPLevels(), LevelVariable.PVAR), visitLevels(expr.getHLevels(), LevelVariable.HVAR)));
    }
    return elimExpressions;
  }

  private void buildImplementation(Object errorData, Abstract.ClassFieldImpl implementation, List<? super Concrete.ClassFieldImpl> implementations) {
    if (implementation instanceof Abstract.CoClauseFunctionReference) {
      Abstract.AbstractLocatedReferable functionRef = ((Abstract.CoClauseFunctionReference) implementation).getFunctionReference();
      if (functionRef != null) {
        Abstract.Reference implementedField = implementation.getImplementedField();
        if (implementedField != null) {
          LocatedReferableImpl tcFunctionRef = convertReferable(functionRef, myDefinition, null);
          if (myResolved != null) myResolved.put(functionRef, tcFunctionRef);
          implementations.add(new Concrete.CoClauseFunctionReference(implementedField.getReferent(), tcFunctionRef, implementation.isDefault()));
        }
        return;
      }
    }

    Object prec = implementation.getPrec();
    if (prec != null) {
      myErrorReporter.report(new ParsingError(ParsingError.Kind.PRECEDENCE_IGNORED, prec));
    }

    Abstract.Reference implementedField = implementation.getImplementedField();
    if (implementedField == null) {
      return;
    }

    Abstract.Expression impl = implementation.getImplementation();
    if (impl != null) {
      List<? extends Abstract.LamParameter> parameters = implementation.getLamParameters();
      Concrete.Expression term = impl.accept(this, null);
      List<Concrete.Parameter> cParams = new ArrayList<>();
      List<Concrete.Pattern> patterns = buildLamParameters(parameters, cParams);
      if (!parameters.isEmpty() || !patterns.isEmpty()) {
        term = Concrete.PatternLamExpression.make(parameters.get(0).getData(), cParams, patterns, term);
      }

      implementations.add(new Concrete.ClassFieldImpl(implementation.getData(), implementedField.getReferent(), term, null, implementation.isDefault()));
    } else {
      boolean hasImpl = implementation.hasImplementation();
      if (hasImpl) {
        myErrorLevel = GeneralError.Level.ERROR;
      }
      implementations.add(new Concrete.ClassFieldImpl(implementation.getData(), implementedField.getReferent(), hasImpl ? new Concrete.ErrorHoleExpression(errorData, null) : null, new Concrete.Coclauses(implementation.getCoClauseData(), buildImplementations(implementation.getData(), implementation.getCoClauseElements())), implementation.isDefault()));
    }
  }

  private List<Concrete.ClassFieldImpl> buildImplementations(Object errorData, Collection<? extends Abstract.ClassFieldImpl> absImplementations) {
    List<Concrete.ClassFieldImpl> implementations = new ArrayList<>();
    for (Abstract.ClassFieldImpl implementation : absImplementations) {
      buildImplementation(errorData, implementation, implementations);
    }
    return implementations;
  }

  private static DataLocalReferable makeLocalRef(Abstract.AbstractReferable referable) {
    return referable == null ? null : new DataLocalReferable(referable, referable.getRefName());
  }

  public Concrete.Parameter buildParameter(Abstract.Parameter parameter, boolean isNamed, boolean isDefinition) {
    List<? extends Abstract.AbstractReferable> referableList = parameter.getReferableList();
    Abstract.Expression type = parameter.getType();
    Concrete.Expression cType;
    if (type == null) {
      if (referableList.size() == 1) {
        return new Concrete.NameParameter(parameter.getData(), parameter.isExplicit(), makeLocalRef(referableList.get(0)));
      } else {
        myErrorLevel = GeneralError.Level.ERROR;
        cType = new Concrete.ErrorHoleExpression(parameter.getData(), null);
      }
    } else {
      cType = type.accept(this, null);
    }

    boolean isStrict = parameter.isStrict();
    boolean isProperty = parameter.isProperty();
    if (!isNamed && (referableList.isEmpty() || referableList.size() == 1 && referableList.get(0) == null)) {
      if (isDefinition && isStrict) {
        return new Concrete.DefinitionTypeParameter(parameter.getData(), parameter.isExplicit(), true, cType, isProperty);
      } else {
        if (isStrict) {
          myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "\\strict is not allowed here", parameter.getData()));
        }
        return new Concrete.TypeParameter(parameter.getData(), parameter.isExplicit(), cType, isProperty);
      }
    } else {
      List<Referable> dataReferableList = new ArrayList<>(referableList.size());
      for (Abstract.AbstractReferable referable : referableList) {
        dataReferableList.add(referable instanceof Abstract.ClassField ? convertField((Abstract.ClassField) referable, myDefinition) : makeLocalRef(referable));
      }
      if (isDefinition && isStrict) {
        return new Concrete.DefinitionTelescopeParameter(parameter.getData(), parameter.isExplicit(), true, dataReferableList, cType, isProperty);
      } else {
        if (isStrict) {
          myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "\\strict is not allowed here", parameter.getData()));
        }
        return new Concrete.TelescopeParameter(parameter.getData(), parameter.isExplicit(), dataReferableList, cType, isProperty);
      }
    }
  }

  public List<Concrete.Parameter> buildParameters(Collection<? extends Abstract.Parameter> absParameters, boolean isDefinition) {
    List<Concrete.Parameter> parameters = new ArrayList<>(absParameters.size());
    for (Abstract.Parameter absParameter : absParameters) {
      parameters.add(buildParameter(absParameter, true, isDefinition));
    }
    return parameters;
  }

  public List<Concrete.TypeParameter> buildTypeParameters(Collection<? extends Abstract.Parameter> absParameters, boolean isDefinition) {
    List<Concrete.TypeParameter> parameters = new ArrayList<>(absParameters.size());
    for (Abstract.Parameter absParameter : absParameters) {
      Concrete.Parameter parameter = buildParameter(absParameter, false, isDefinition);
      if (parameter instanceof Concrete.TypeParameter) {
        parameters.add((Concrete.TypeParameter) parameter);
      } else {
        myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "Expected a typed parameter", parameter.getData()));
      }
    }
    return parameters;
  }

  public Concrete.TypedReferable buildTypedReferable(Abstract.TypedReferable typedReferable) {
    Abstract.AbstractReferable referable = typedReferable == null ? null : typedReferable.getReferable();
    if (referable == null) return null;
    Abstract.Expression type = typedReferable.getType();
    return new Concrete.TypedReferable(typedReferable.getData(), makeLocalRef(referable), type == null ? null : type.accept(this, null));
  }

  public Concrete.Pattern buildPattern(Abstract.Pattern pattern) {
    List<? extends Abstract.Pattern> subPatterns = pattern.getSequence();
    if (subPatterns.size() == 1) {
      Concrete.Pattern innerPattern = buildPattern(subPatterns.get(0));
      if (!subPatterns.get(0).isExplicit() || !pattern.isExplicit()) {
        innerPattern.setExplicit(false);
      }
      Concrete.TypedReferable typedReferables = buildTypedReferable(pattern.getAsPattern());
      if (typedReferables != null) {
        innerPattern.setAsReferable(typedReferables);
      }
      Abstract.Expression type = pattern.getType();
      if (type != null) {
        if (innerPattern instanceof Concrete.NamePattern) {
          return new Concrete.NamePattern(innerPattern.getData(), pattern.isExplicit(), ((Concrete.NamePattern) innerPattern).getReferable(), type.accept(this, null));
        } else {
          myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.WARNING_UNUSED, "Type is ignored", type));
        }
      }

      return innerPattern;
    }
    Integer number = pattern.getInteger();
    if (number != null) {
      Concrete.Pattern cPattern = new Concrete.NumberPattern(pattern.getData(), number, buildTypedReferable(pattern.getAsPattern()));
      cPattern.setExplicit(pattern.isExplicit());
      return cPattern;
    }

    Abstract.AbstractReferable longRef = pattern.getSingleReferable();
    UnresolvedReference unresolvedRef = pattern.getConstructorReference();

    Abstract.Expression type = pattern.getType();

    if (longRef != null || unresolvedRef != null || pattern.isUnnamed()) {
      Fixity fixity = pattern.getFixity();
      return new Concrete.NamePattern(pattern.getData(), pattern.isExplicit(), longRef != null ? makeLocalRef(longRef) : unresolvedRef, type == null ? null : type.accept(this, null), fixity == null ? Fixity.NONFIX : fixity);
    } else if (pattern.isTuplePattern()) {
      return new Concrete.TuplePattern(pattern.getData(), pattern.isExplicit(), buildPatterns(pattern.getSequence()), buildTypedReferable(pattern.getAsPattern()));
    } else {
      List<? extends Abstract.Pattern> args = pattern.getSequence();
      List<Concrete.BinOpSequenceElem<Concrete.Pattern>> binOps = new ArrayList<>();
      for (Abstract.Pattern abstractPattern : args) {
        binOps.add(new Concrete.BinOpSequenceElem<>(buildPattern(abstractPattern)));
      }
      if (args.isEmpty()) {
        myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "Empty pattern is disallowed here", pattern.getData()));
        return new Concrete.TuplePattern(pattern.getData(), pattern.isExplicit(), List.of(), buildTypedReferable(pattern.getAsPattern()));
      }
      return new Concrete.UnparsedConstructorPattern(pattern.getData(), pattern.isExplicit(), binOps, buildTypedReferable(pattern.getAsPattern()));
    }
  }

  public List<Concrete.Pattern> buildPatterns(Collection<? extends Abstract.Pattern> absPatterns) {
    List<Concrete.Pattern> patterns = new ArrayList<>(absPatterns.size());
    for (Abstract.Pattern pattern : absPatterns) {
      patterns.add(buildPattern(pattern));
    }
    return patterns;
  }

  public List<Concrete.FunctionClause> buildClauses(Collection<? extends Abstract.FunctionClause> absClauses) {
    List<Concrete.FunctionClause> clauses = new ArrayList<>(absClauses.size());
    for (Abstract.FunctionClause clause : absClauses) {
      Collection<? extends Abstract.Pattern> patterns = clause.getPatterns();
      if (patterns.isEmpty()) {
        myErrorLevel = GeneralError.Level.ERROR;
        continue;
      }
      Abstract.Expression expr = clause.getExpression();
      clauses.add(new Concrete.FunctionClause(clause.getData(), buildPatterns(patterns), expr == null ? null : expr.accept(this, null)));
    }
    return clauses;
  }

  // Expression

  private List<Concrete.LevelExpression> visitLevels(Collection<? extends Abstract.LevelExpression> levels, LevelVariable base) {
    if (levels == null) return null;
    List<Concrete.LevelExpression> result = new ArrayList<>(levels.size());
    for (Abstract.LevelExpression level : levels) {
      result.add(level.accept(this, base));
    }
    return result;
  }

  @Override
  public Concrete.ReferenceExpression visitReference(@Nullable Object data, @NotNull Referable referent, @Nullable Fixity fixity, @Nullable Collection<? extends Abstract.LevelExpression> pLevels, @Nullable Collection<? extends Abstract.LevelExpression> hLevels, Void params) {
    return Concrete.FixityReferenceExpression.make(data, referent, fixity, visitLevels(pLevels, LevelVariable.PVAR), visitLevels(hLevels, LevelVariable.HVAR));
  }

  @Override
  public Concrete.ReferenceExpression visitReference(@Nullable Object data, @NotNull Referable referent, int lp, int lh, Void params) {
    return new Concrete.ReferenceExpression(data, referent, new SingletonList<>(new Concrete.NumberLevelExpression(data, lp)), new SingletonList<>(new Concrete.NumberLevelExpression(data, lh)));
  }

  @Override
  public Concrete.Expression visitThis(@Nullable Object data, Void params) {
    return new Concrete.ThisExpression(data, null);
  }

  @Override
  public Concrete.Expression visitApplyHole(@Nullable Object data, Void params) {
    return new Concrete.ApplyHoleExpression(data);
  }

  @Override
  public Concrete.Expression visitLam(@Nullable Object data, @NotNull Collection<? extends Abstract.LamParameter> parameters, @Nullable Abstract.Expression body, Void params) {
    if (parameters.isEmpty() && body == null) {
      return new Concrete.ErrorHoleExpression(data, null);
    }
    Concrete.Expression cBody = body == null ? new Concrete.IncompleteExpression(data) : body.accept(this, null);
    if (parameters.isEmpty()) return cBody;
    List<Concrete.Parameter> cParams = new ArrayList<>();
    return Concrete.PatternLamExpression.make(data, cParams, buildLamParameters(parameters, cParams), cBody);
  }

  private List<Concrete.Pattern> buildLamParameters(Collection<? extends Abstract.LamParameter> parameters, List<Concrete.Parameter> cParams) {
    List<Concrete.Pattern> patterns = Collections.emptyList();
    for (Abstract.LamParameter parameter : parameters) {
      if (parameter instanceof Abstract.Parameter) {
        cParams.add(buildParameter((Abstract.Parameter) parameter, true, false));
        if (!patterns.isEmpty()) patterns.add(null);
      } else if (parameter instanceof Abstract.Pattern) {
        if (patterns.isEmpty()) {
          patterns = new ArrayList<>();
          for (Concrete.Parameter ignored : cParams) {
            patterns.add(null);
          }
        }
        patterns.add(buildPattern((Abstract.Pattern) parameter));
      }
    }
    return patterns;
  }

  @Override
  public Concrete.Expression visitPi(@Nullable Object data, @NotNull Collection<? extends Abstract.Parameter> parameters, @Nullable Abstract.Expression codomain, Void params) {
    if (codomain == null) {
      myErrorLevel = GeneralError.Level.ERROR;
    }
    Concrete.Expression cCodomain = codomain == null ? new Concrete.ErrorHoleExpression(data, null) : codomain.accept(this, null);
    return parameters.isEmpty() ? cCodomain : new Concrete.PiExpression(data, buildTypeParameters(parameters, false), cCodomain);
  }

  @Override
  public Concrete.UniverseExpression visitUniverse(@Nullable Object data, @Nullable Integer pLevelNum, @Nullable Integer hLevelNum, @Nullable Abstract.LevelExpression pLevel, @Nullable Abstract.LevelExpression hLevel, Void params) {
    if (pLevelNum != null && hLevel == null) {
      hLevel = pLevel;
      pLevel = null;
    }
    if (pLevelNum != null && pLevel != null) {
      myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "p-level is already specified", pLevel.getData()));
    }
    if (hLevelNum != null && hLevel != null) {
      myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "h-level is already specified", hLevel.getData()));
    }

    return new Concrete.UniverseExpression(data,
            pLevelNum != null ? new Concrete.NumberLevelExpression(data, pLevelNum) : pLevel != null ? pLevel.accept(this, LevelVariable.PVAR) : null,
            hLevelNum != null ? (hLevelNum == Abstract.INFINITY_LEVEL ? new Concrete.InfLevelExpression(data) : new Concrete.NumberLevelExpression(data, hLevelNum)) : hLevel != null ? hLevel.accept(this, LevelVariable.HVAR) : null);
  }

  @Override
  public Concrete.HoleExpression visitInferHole(@Nullable Object data, Void params) {
    return new Concrete.HoleExpression(data);
  }

  @Override
  public Concrete.GoalExpression visitGoal(@Nullable Object data, @Nullable String name, @Nullable Abstract.Expression expression, Void params) {
    return new Concrete.GoalExpression(data, name, expression == null ? null : expression.accept(this, null));
  }

  @Override
  public Concrete.TupleExpression visitTuple(@Nullable Object data, @NotNull Collection<? extends Abstract.Expression> absFields, @Nullable Object trailingComma, Void params) {
    List<Concrete.Expression> fields = new ArrayList<>();
    for (Abstract.Expression field : absFields) {
      fields.add(field.accept(this, null));
    }
    if (trailingComma != null) {
      fields.add(new Concrete.IncompleteExpression(trailingComma));
    }

    return new Concrete.TupleExpression(data, fields);
  }

  @Override
  public Concrete.SigmaExpression visitSigma(@Nullable Object data, @NotNull Collection<? extends Abstract.Parameter> parameters, Void params) {
    return new Concrete.SigmaExpression(data, buildTypeParameters(parameters, false));
  }

  private Concrete.Expression makeBinOpSequence(Object data, Concrete.Expression left, Collection<? extends Abstract.BinOpSequenceElem> sequence, Abstract.FunctionClauses clauses) {
    if (sequence.isEmpty() && clauses == null) {
      return left;
    }

    if (left instanceof Concrete.BinOpSequenceExpression && sequence.isEmpty()) {
      return new Concrete.BinOpSequenceExpression(left.getData(), ((Concrete.BinOpSequenceExpression) left).getSequence(), new Concrete.FunctionClauses(clauses.getData(), buildClauses(clauses.getClauseList())));
    }

    List<Concrete.BinOpSequenceElem<Concrete.Expression>> elems = new ArrayList<>();
    elems.add(new Concrete.BinOpSequenceElem<>(left));
    for (Abstract.BinOpSequenceElem elem : sequence) {
      Abstract.Expression arg = elem.getExpression();
      if (arg != null) {
        elems.add(new Concrete.BinOpSequenceElem<>(arg.accept(this, null), elem.isVariable() ? Fixity.UNKNOWN : Fixity.NONFIX, elem.isExplicit()));
      }
    }

    return new Concrete.BinOpSequenceExpression(data, elems, clauses == null ? null : new Concrete.FunctionClauses(clauses.getData(), buildClauses(clauses.getClauseList())));
  }

  @Override
  public Concrete.Expression visitBinOpSequence(@Nullable Object data, @NotNull Abstract.Expression left, @NotNull Collection<? extends Abstract.BinOpSequenceElem> sequence, Void params) {
    return makeBinOpSequence(data, left.accept(this, null), sequence, null);
  }

  @Override
  public Concrete.Expression visitCase(@Nullable Object data, boolean isSFunc, @Nullable Abstract.EvalKind evalKind, @NotNull Collection<? extends Abstract.CaseArgument> caseArgs, @Nullable Abstract.Expression resultType, @Nullable Abstract.Expression resultTypeLevel, @NotNull Collection<? extends Abstract.FunctionClause> clauses, Void params) {
    if (caseArgs.isEmpty()) {
      myErrorLevel = GeneralError.Level.ERROR;
      return new Concrete.ErrorHoleExpression(data, null);
    }
    List<Concrete.CaseArgument> concreteCaseArgs = new ArrayList<>(caseArgs.size());
    for (Abstract.CaseArgument caseArg : caseArgs) {
      Abstract.Expression type = caseArg.getType();
      Abstract.Reference elimRef = caseArg.getEliminatedReference();
      Concrete.Expression cType = type == null ? null : type.accept(this, null);
      if (elimRef != null) {
        concreteCaseArgs.add(new Concrete.CaseArgument(buildReference(elimRef), cType));
      } else {
        Object applyHoleData = caseArg.getApplyHoleData();
        if (applyHoleData != null) {
          concreteCaseArgs.add(new Concrete.CaseArgument(new Concrete.ApplyHoleExpression(applyHoleData), cType));
        } else {
          Abstract.Expression expr = caseArg.getExpression();
          if (expr == null) {
            myErrorLevel = GeneralError.Level.ERROR;
          }
          Concrete.Expression cExpr = expr == null ? new Concrete.ErrorHoleExpression(data, null) : expr.accept(this, null);
          concreteCaseArgs.add(new Concrete.CaseArgument(cExpr, makeLocalRef(caseArg.getReferable()), cType));
        }
      }
    }

    resultTypeLevel = checkResultTypeLevel(resultType, resultTypeLevel);
    Concrete.Expression result = new Concrete.CaseExpression(data, isSFunc, concreteCaseArgs, resultType == null ? null : resultType.accept(this, null), resultTypeLevel == null ? null : resultTypeLevel.accept(this, null), buildClauses(clauses));
    return evalKind == Abstract.EvalKind.BOX ? new Concrete.BoxExpression(data, result) : evalKind != null ? new Concrete.EvalExpression(data, evalKind == Abstract.EvalKind.PEVAL, result) : result;
  }

  private Abstract.Expression checkResultTypeLevel(Abstract.Expression resultType, Abstract.Expression resultTypeLevel) {
    if (resultType == null && resultTypeLevel != null) {
      myErrorReporter.report(new AbstractExpressionError(GeneralError.Level.ERROR, "The level of a type can be specified only if the type is also specified", resultTypeLevel));
      return null;
    } else {
      return resultTypeLevel;
    }
  }

  @Override
  public Concrete.Expression visitFieldAccs(@Nullable Object data, @NotNull Abstract.Expression expression, @NotNull List<Abstract.FieldAcc> fieldAccs, @Nullable AbstractReference infixReference, @Nullable String infixName, boolean isInfix, Void params) {
    Concrete.Expression result = expression.accept(this, null);

    int i = 0;
    if (result instanceof Concrete.ReferenceExpression refExpr && refExpr.getReferent() instanceof UnresolvedReference unresolved && (!fieldAccs.isEmpty() && fieldAccs.get(0).getFieldRef() != null || infixName != null)) {
      List<AbstractReference> references = new ArrayList<>(unresolved.getReferenceList());
      List<String> names = new ArrayList<>(unresolved.getPath());
      for (; i < fieldAccs.size(); i++) {
        UnresolvedReference fieldRef = fieldAccs.get(i).getFieldRef();
        if (fieldRef != null) {
          references.add(fieldRef.getData() instanceof AbstractReference abstractRef ? abstractRef : null);
          names.add(fieldRef.getRefName());
        } else {
          break;
        }
      }
      if (infixName != null) {
        references.add(infixReference);
        names.add(infixName);
      }
      Referable referable = Objects.requireNonNull(LongUnresolvedReference.make(data, references, names));
      result = infixName != null ? new Concrete.FixityReferenceExpression(data, referable, isInfix ? Fixity.INFIX : Fixity.POSTFIX) : new Concrete.ReferenceExpression(data, referable);
    }

    for (; i < fieldAccs.size(); i ++) {
      Integer number = fieldAccs.get(i).getNumber();
      if (number != null) {
        result = new Concrete.ProjExpression(data, result, number - 1);
      } else {
        Referable fieldRef = fieldAccs.get(i).getFieldRef();
        if (fieldRef != null) {
          result = new Concrete.FieldCallExpression(data, fieldRef, Fixity.UNKNOWN, result);
        }
      }
    }

    return result;
  }

  @Override
  public Concrete.Expression visitClassExt(@Nullable Object data, boolean isNew, Abstract.@Nullable EvalKind evalKind, Abstract.@Nullable Expression baseClass, @Nullable Object coclausesData, @Nullable Collection<? extends Abstract.ClassFieldImpl> implementations, @NotNull Collection<? extends Abstract.BinOpSequenceElem> sequence, @Nullable Abstract.FunctionClauses clauses, Void params) {
    if (baseClass == null) {
      myErrorLevel = GeneralError.Level.ERROR;
      return new Concrete.ErrorHoleExpression(data, null);
    }

    Concrete.Expression result = baseClass.accept(this, null);
    if (implementations != null) {
      result = Concrete.ClassExtExpression.make(data, result, new Concrete.Coclauses(coclausesData, buildImplementations(data, implementations)));
    }
    if (evalKind != null) {
      result = evalKind == Abstract.EvalKind.BOX ? new Concrete.BoxExpression(data, result) : new Concrete.EvalExpression(data, evalKind == Abstract.EvalKind.PEVAL, result);
    }
    if (isNew) {
      result = new Concrete.NewExpression(data, result);
    }

    return makeBinOpSequence(data, result, sequence, clauses);
  }

  @Override
  public Concrete.Expression visitLet(@Nullable Object data, boolean isHave, boolean isStrict, @NotNull Collection<? extends Abstract.LetClause> absClauses, @Nullable Abstract.Expression expression, Void params) {
    if (absClauses.isEmpty()) {
      myErrorLevel = GeneralError.Level.ERROR;
      return expression != null ? expression.accept(this, null) : new Concrete.ErrorHoleExpression(data, null);
    }

    List<Concrete.LetClause> clauses = new ArrayList<>(absClauses.size());
    boolean ok = true;
    for (Abstract.LetClause clause : absClauses) {
      Abstract.Expression term = clause.getTerm();
      if (term != null) {
        Abstract.AbstractReferable referable = clause.getReferable();
        List<? extends Abstract.Parameter> parameters = clause.getParameters();
        Abstract.Expression resultType = clause.getResultType();
        if (referable != null) {
          clauses.add(new Concrete.LetClause(makeLocalRef(referable), buildParameters(parameters, false), resultType == null ? null : resultType.accept(this, null), term.accept(this, null)));
        } else {
          Abstract.Pattern pattern = clause.getPattern();
          if (pattern != null) {
            clauses.add(new Concrete.LetClause(buildPattern(pattern).toConstructor(), resultType == null ? null : resultType.accept(this, null), term.accept(this, null)));
          }
        }
      } else {
        myErrorLevel = GeneralError.Level.ERROR;
        ok = false;
      }
    }

    return new Concrete.LetExpression(data, isHave, isStrict, clauses, expression != null ? expression.accept(this, null) : ok ? new Concrete.IncompleteExpression(data) : new Concrete.ErrorHoleExpression(data, null));
  }

  @Override
  public Concrete.NumericLiteral visitNumericLiteral(@Nullable Object data, @NotNull BigInteger number, Void params) {
    return new Concrete.NumericLiteral(data, number);
  }

  @Override
  public Concrete.Expression visitStringLiteral(@Nullable Object data, @NotNull String unescapedString, Void params) {
    return new Concrete.StringLiteral(data, unescapedString);
  }

  @Override
  public Concrete.Expression visitTyped(@Nullable Object data, @NotNull Abstract.Expression expr, @NotNull Abstract.Expression type, Void params) {
    return new Concrete.TypedExpression(data, expr.accept(this, null), type.accept(this, null));
  }

  // LevelExpression

  @Override
  public Concrete.InfLevelExpression visitInf(@Nullable Object data, LevelVariable base) {
    return new Concrete.InfLevelExpression(data);
  }

  @Override
  public Concrete.PLevelExpression visitLP(@Nullable Object data, LevelVariable base) {
    return new Concrete.PLevelExpression(data);
  }

  @Override
  public Concrete.HLevelExpression visitLH(@Nullable Object data, LevelVariable base) {
    return new Concrete.HLevelExpression(data);
  }

  @Override
  public Concrete.NumberLevelExpression visitNumber(@Nullable Object data, int number, LevelVariable base) {
    return new Concrete.NumberLevelExpression(data, number);
  }

  @Override
  public Concrete.LevelExpression visitId(@Nullable Object data, Referable ref, LevelVariable base) {
    return new Concrete.VarLevelExpression(data, ref, base.getType());
  }

  @Override
  public Concrete.LevelExpression visitSuc(@Nullable Object data, @Nullable Abstract.LevelExpression expr, LevelVariable base) {
    if (expr == null) {
      myErrorLevel = GeneralError.Level.ERROR;
      return base == LevelVariable.PVAR ? new Concrete.PLevelExpression(data) : new Concrete.HLevelExpression(data);
    }
    return new Concrete.SucLevelExpression(data, expr.accept(this, base));
  }

  @Override
  public Concrete.LevelExpression visitMax(@Nullable Object data, @Nullable Abstract.LevelExpression left, @Nullable Abstract.LevelExpression right, LevelVariable base) {
    if (left == null || right == null) {
      myErrorLevel = GeneralError.Level.ERROR;
    }
    return left == null && right == null
            ? (base == LevelVariable.PVAR ? new Concrete.PLevelExpression(data) : new Concrete.HLevelExpression(data))
            : left == null
            ? right.accept(this, base)
            : right == null
            ? left.accept(this, base)
            : new Concrete.MaxLevelExpression(data, left.accept(this, base), right.accept(this, base));
  }

  @Override
  public Concrete.LevelExpression visitError(@Nullable Object data, LevelVariable base) {
    myErrorLevel = GeneralError.Level.ERROR;
    return base == LevelVariable.PVAR ? new Concrete.PLevelExpression(data) : new Concrete.HLevelExpression(data);
  }
}
