package org.arend.naming.resolving.visitor;

import org.arend.core.context.Utils;
import org.arend.error.ParsingError;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.NameResolverError;
import org.arend.ext.module.LongName;
import org.arend.ext.prettyprinting.doc.*;
import org.arend.ext.reference.Precedence;
import org.arend.naming.error.DuplicateNameError;
import org.arend.naming.error.DuplicateOpenedNameError;
import org.arend.naming.error.ExistingOpenedNameError;
import org.arend.naming.error.ReferenceError;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.ResolverListener;
import org.arend.naming.resolving.typing.*;
import org.arend.naming.scope.*;
import org.arend.naming.scope.local.ElimScope;
import org.arend.naming.scope.local.ListScope;
import org.arend.prelude.Prelude;
import org.arend.ext.concrete.definition.FunctionKind;
import org.arend.server.impl.GroupData;
import org.arend.term.NameHiding;
import org.arend.term.NameRenaming;
import org.arend.term.NamespaceCommand;
import org.arend.term.concrete.*;
import org.arend.term.group.*;
import org.arend.typechecking.error.local.LocalErrorReporter;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.visitor.SyntacticDesugarVisitor;
import org.arend.ext.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class DefinitionResolveNameVisitor implements ConcreteResolvableDefinitionVisitor<Scope, Void>, DocVisitor<Scope, Void> {
  private final TypingInfo myTypingInfo;
  private final ConcreteProvider myConcreteProvider;
  private final ErrorReporter myErrorReporter;
  private ErrorReporter myLocalErrorReporter;
  private final ResolverListener myResolverListener;
  private final Map<TCDefReferable, Concrete.ExternalParameters> myExternalParameters = new HashMap<>();

  public DefinitionResolveNameVisitor(ConcreteProvider concreteProvider, TypingInfo typingInfo, ErrorReporter errorReporter) {
    myConcreteProvider = concreteProvider;
    myTypingInfo = typingInfo;
    myErrorReporter = errorReporter;
    myResolverListener = null;
  }

  public DefinitionResolveNameVisitor(ConcreteProvider concreteProvider, TypingInfo typingInfo, ErrorReporter errorReporter, ResolverListener resolverListener) {
    myConcreteProvider = concreteProvider;
    myTypingInfo = typingInfo;
    myErrorReporter = errorReporter;
    myResolverListener = resolverListener;
  }

  @Override
  public Void visitMeta(DefinableMetaDefinition def, Scope scope) {
    if (def.getStage().ordinal() >= Concrete.Stage.RESOLVED.ordinal()) {
      return null;
    }

    scope = new PrivateFilteredScope(scope);
    myLocalErrorReporter = new ConcreteProxyErrorReporter(def);

    checkNameAndPrecedence(def, def.getData());

    List<TypedReferable> context = new ArrayList<>();
    var exprVisitor = new ExpressionResolveNameVisitor(scope, context, myTypingInfo, myLocalErrorReporter, myResolverListener, visitLevelParameters(def.getPLevelParameters()), visitLevelParameters(def.getHLevelParameters()));
    for (Iterator<? extends Concrete.Parameter> iterator = def.getParameters().iterator(); iterator.hasNext(); ) {
      Concrete.Parameter parameter = iterator.next();
      if (parameter.getType() == null && !parameter.isExplicit()) {
        myErrorReporter.report(new NameResolverError("Untyped parameters must be explicit", parameter));
        iterator.remove();
      }
    }
    exprVisitor.visitParameters(def.getParameters(), null);

    if (def.body != null) {
      def.body = def.body.accept(exprVisitor, null);
    }

    if (def.getData().getDefinition() instanceof DefinableMetaDefinition) {
      def.getData().setDefinition(def);
    }
    def.setResolved();
    SyntacticDesugarVisitor.desugar(def, myLocalErrorReporter);
    if (myResolverListener != null) {
      myResolverListener.definitionResolved(def);
    }

    return null;
  }

  @Override
  public Void visitVList(VListDoc doc, Scope scope) {
    for (Doc line : doc.getDocs()) {
      line.accept(this, scope);
    }
    return null;
  }

  @Override
  public Void visitHList(HListDoc doc, Scope scope) {
    for (Doc line : doc.getDocs()) {
      line.accept(this, scope);
    }
    return null;
  }

  @Override
  public Void visitText(TextDoc doc, Scope scope) {
    return null;
  }

  @Override
  public Void visitHang(HangDoc doc, Scope scope) {
    doc.getTop().accept(this, scope);
    doc.getBottom().accept(this, scope);
    return null;
  }

  @Override
  public Void visitReference(ReferenceDoc doc, Scope scope) {
    if (doc.getReference() instanceof UnresolvedReference referable) {
      new ExpressionResolveNameVisitor(scope, Collections.emptyList(), myTypingInfo, myErrorReporter, myResolverListener).visitReference(new Concrete.ReferenceExpression(referable.getData(), referable), null);
    }
    return null;
  }

  @Override
  public Void visitCaching(CachingDoc doc, Scope params) {
    return null;
  }

  @Override
  public Void visitTermLine(TermLineDoc doc, Scope params) {
    return null;
  }

  @Override
  public Void visitPattern(PatternDoc doc, Scope params) {
    return null;
  }

  private class ConcreteProxyErrorReporter extends LocalErrorReporter {
    private final Concrete.ResolvableDefinition definition;

    private ConcreteProxyErrorReporter(Concrete.ResolvableDefinition definition) {
      super(definition.getData(), myErrorReporter);
      this.definition = definition;
    }

    @Override
    public void report(GeneralError error) {
      definition.setStatus(error.level);
      super.report(error);
    }
  }

  private void checkNameAndPrecedence(Concrete.ReferableDefinition definition) {
    checkNameAndPrecedence(definition, definition.getData());
  }

  private void checkNameAndPrecedence(ConcreteSourceNode definition, LocatedReferable referable) {
    ExpressionResolveNameVisitor.checkName(referable, myLocalErrorReporter);

    Precedence prec = referable.getPrecedence();
    if (prec.priority < 0 || prec.priority > 10) {
      myLocalErrorReporter.report(new ParsingError(ParsingError.Kind.INVALID_PRIORITY, definition));
    }
  }

  private static List<? extends Referable> visitLevelParameters(Concrete.LevelParameters params) {
    return params == null ? Collections.emptyList() : params.referables;
  }

  private void resolveCoclauseImplementedField(Concrete.CoClauseFunctionDefinition function, Scope scope) {
    if (!(function.getImplementedField() instanceof UnresolvedReference)) {
      return;
    }

    Concrete.GeneralDefinition enclosingDef = myConcreteProvider.getConcrete(function.getUseParent());
    DynamicScopeProvider dynamicScopeProvider = null;
    List<? extends Concrete.ClassElement> elements = Collections.emptyList();
    if (enclosingDef instanceof Concrete.BaseFunctionDefinition enclosingFunction) {
      if (enclosingFunction.getResultType() != null) {
        dynamicScopeProvider = myTypingInfo.getBodyDynamicScopeProvider(enclosingFunction.getResultType());
        elements = enclosingFunction.getBody().getCoClauseElements();
      }
    } else if (enclosingDef instanceof Concrete.ClassDefinition classDef) {
      dynamicScopeProvider = myTypingInfo.getDynamicScopeProvider(classDef.getData());
      elements = classDef.getElements();
    }

    if (dynamicScopeProvider != null) {
      Concrete.CoClauseFunctionReference functionRef = null;
      for (Concrete.ClassElement element : elements) {
        if (element instanceof Concrete.CoClauseFunctionReference && ((Concrete.CoClauseFunctionReference) element).getFunctionReference().equals(function.getData())) {
          functionRef = (Concrete.CoClauseFunctionReference) element;
          break;
        }
      }
      if (functionRef != null) {
        function.setImplementedField(new ExpressionResolveNameVisitor(scope, null, myTypingInfo, myLocalErrorReporter, myResolverListener).visitClassFieldReference(functionRef, function.getImplementedField(), dynamicScopeProvider));
      }
    }
    if (function.getData() instanceof LocatedReferableImpl && !((LocatedReferableImpl) function.getData()).isPrecedenceSet() && function.getImplementedField() instanceof GlobalReferable) {
      ((LocatedReferableImpl) function.getData()).setPrecedence(((GlobalReferable) function.getImplementedField()).getPrecedence());
    }
  }

  private ExpressionResolveNameVisitor resolveFunctionHeader(Concrete.BaseFunctionDefinition def, Scope scope) {
    myLocalErrorReporter = new ConcreteProxyErrorReporter(def);

    if (def instanceof Concrete.FunctionDefinition funDef && (funDef.getKind().isUse() || funDef.getKind().isCoclause())) {
      if (def.getUseParent() == null) {
        myErrorReporter.report(new NameResolverError("This function must be declared with \\use", def));
        funDef.setKind(FunctionKind.FUNC);
      } else {
        Concrete.GeneralDefinition enclosingDef = myConcreteProvider.getConcrete(def.getUseParent());
        if (enclosingDef instanceof Concrete.Definition) {
          if (def.getPLevelParameters() == null && ((Concrete.Definition) enclosingDef).getPLevelParameters() != null) {
            def.setPLevelParameters(((Concrete.Definition) enclosingDef).getPLevelParameters());
          }
          if (def.getHLevelParameters() == null && ((Concrete.Definition) enclosingDef).getHLevelParameters() != null) {
            def.setHLevelParameters(((Concrete.Definition) enclosingDef).getHLevelParameters());
          }
        }

        if (def instanceof Concrete.CoClauseFunctionDefinition function) {
          resolveCoclauseImplementedField(function, scope);
          if (function.getNumberOfExternalParameters() == 0 && enclosingDef instanceof Concrete.BaseFunctionDefinition) {
            List<Concrete.Parameter> parameters = new SubstConcreteVisitor(def.getData()).visitParameters(((Concrete.BaseFunctionDefinition) enclosingDef).getParameters());
            for (Concrete.Parameter parameter : parameters) {
              parameter.setExplicit(false);
            }
            def.getParameters().addAll(0, parameters);
            function.setNumberOfExternalParameters(parameters.size());
          }
        }
      }
    }

    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(scope, new ArrayList<>(), myTypingInfo, myLocalErrorReporter, myResolverListener, visitLevelParameters(def.getPLevelParameters()), visitLevelParameters(def.getHLevelParameters()));
    exprVisitor.visitParameters(def.getParameters(), null);
    def.setHeaderResolved();
    return exprVisitor;
  }

  @Override
  public Void visitFunction(Concrete.BaseFunctionDefinition def, Scope scope) {
    if (def.getStage().ordinal() >= Concrete.Stage.RESOLVED.ordinal()) {
      return null;
    }

    scope = new PrivateFilteredScope(scope);
    ExpressionResolveNameVisitor exprVisitor = resolveFunctionHeader(def, scope);
    List<TypedReferable> context = exprVisitor.getContext();
    checkNameAndPrecedence(def);

    Concrete.FunctionBody body = def.getBody();
    if (def.getResultType() != null) {
      def.setResultType(def.getResultType().accept(exprVisitor, null));
    }
    if (def.getResultTypeLevel() != null) {
      def.setResultTypeLevel(def.getResultTypeLevel().accept(exprVisitor, null));
    }

    if (body instanceof Concrete.TermFunctionBody) {
      ((Concrete.TermFunctionBody) body).setTerm(((Concrete.TermFunctionBody) body).getTerm().accept(exprVisitor, null));
    }
    if (body instanceof Concrete.CoelimFunctionBody) {
      DynamicScopeProvider provider = def.getResultType() == null ? null : myTypingInfo.getBodyDynamicScopeProvider(def.getResultType());
      if (provider != null) {
        for (Concrete.CoClauseElement element : body.getCoClauseElements()) {
          if (element instanceof Concrete.ClassFieldImpl) {
            exprVisitor.visitClassFieldImpl((Concrete.ClassFieldImpl) element, provider);
          }
        }
      } else {
        myLocalErrorReporter.report(def.getResultType() != null ? new NameResolverError("Expected a class", def.getResultType()) : new NameResolverError("The type of a function defined by copattern matching must be specified explicitly", def));
        body.getCoClauseElements().clear();
      }
    }
    if (body instanceof Concrete.ElimFunctionBody) {
      if (def.getResultType() == null && !(def instanceof Concrete.CoClauseFunctionDefinition)) {
        myLocalErrorReporter.report(new NameResolverError("The type of a function defined by pattern matching must be specified explicitly", def));
      }
      visitEliminatedReferences(exprVisitor, body.getEliminatedReferences());
      context.clear();
      if (def instanceof Concrete.CoClauseFunctionDefinition && body.getEliminatedReferences().isEmpty() && ((Concrete.CoClauseFunctionDefinition) def).getNumberOfExternalParameters() > 0) {
        List<Boolean> explicitness = new ArrayList<>();
        for (int i = ((Concrete.CoClauseFunctionDefinition) def).getNumberOfExternalParameters(); i < def.getParameters().size(); i++) {
          for (Referable referable : def.getParameters().get(i).getReferableList()) {
            ((Concrete.ElimFunctionBody) body).getEliminatedReferences().add(new Concrete.ReferenceExpression(def.getData(), referable));
            explicitness.add(def.getParameters().get(i).isExplicit());
          }
        }
        for (Concrete.FunctionClause clause : body.getClauses()) {
          int i = 0;
          for (int j = 0; j < clause.getPatterns().size(); j++) {
            Concrete.Pattern pattern = clause.getPatterns().get(j);
            if (i >= explicitness.size()) break;
            if (explicitness.get(i) && !pattern.isExplicit()) {
              myLocalErrorReporter.report(new NameResolverError("Expected an explicit pattern", pattern));
            } else {
              if (!explicitness.get(i) && pattern.isExplicit()) {
                clause.getPatterns().add(j, new Concrete.NamePattern(pattern.getData(), true, null, null));
              }
              if (!pattern.isExplicit()) {
                pattern.setExplicit(true);
              }
              i++;
            }
          }
        }
      }
      addNotEliminatedParameters(def.getParameters(), body.getEliminatedReferences(), context);
      exprVisitor.visitClauses(body.getClauses(), null);
    }

    if (def.getKind().isUse()) {
      TCReferable useParent = def.getUseParent();
      boolean isFunc = useParent.getKind() == GlobalReferable.Kind.FUNCTION || useParent.getKind() == GlobalReferable.Kind.INSTANCE;
      if (isFunc || useParent.getKind() == GlobalReferable.Kind.CLASS || useParent.getKind() == GlobalReferable.Kind.DATA) {
        if (def.getKind() == FunctionKind.COERCE) {
          if (isFunc) {
            myLocalErrorReporter.report(new ParsingError(ParsingError.Kind.MISPLACED_COERCE, def));
          }
          if (def.getParameters().isEmpty() && def.enclosingClass == null) {
            myLocalErrorReporter.report(new ParsingError(ParsingError.Kind.COERCE_WITHOUT_PARAMETERS, def));
          }
        }
      } else {
        myLocalErrorReporter.report(new ParsingError(ParsingError.Kind.MISPLACED_USE, def));
      }
    }

    SyntacticDesugarVisitor.desugar(def, myLocalErrorReporter);

    if (def instanceof Concrete.CoClauseFunctionDefinition function && def.getKind() == FunctionKind.FUNC_COCLAUSE && function.getNumberOfExternalParameters() > 0) {
      BaseConcreteExpressionVisitor<Void> visitor = new BaseConcreteExpressionVisitor<>() {
        @Override
        public Concrete.Expression visitReference(Concrete.ReferenceExpression expr, Void params) {
          if (expr.getReferent() instanceof TCReferable tcRef && tcRef.getKind() == GlobalReferable.Kind.COCLAUSE_FUNCTION) {
            Concrete.GeneralDefinition definition = myConcreteProvider.getConcrete(tcRef);
            if (definition instanceof Concrete.CoClauseFunctionDefinition coClause && coClause.getUseParent() == function.getUseParent()) {
              List<Concrete.Argument> args = new ArrayList<>();
              int i = 0;
              loop:
              for (Concrete.Parameter parameter : def.getParameters()) {
                for (Referable referable : parameter.getReferableList()) {
                  args.add(new Concrete.Argument(new Concrete.ReferenceExpression(expr.getData(), referable), false));
                  if (++i >= function.getNumberOfExternalParameters()) {
                    break loop;
                  }
                }
              }
              return Concrete.AppExpression.make(expr.getData(), expr, args);
            }
          }
          return expr;
        }

        @Override
        public Concrete.Expression visitApp(Concrete.AppExpression expr, Void params) {
          if (expr.getArguments().get(0).isExplicit() || !(expr.getFunction() instanceof Concrete.ReferenceExpression)) {
            return super.visitApp(expr, params);
          }
          for (Concrete.Argument argument : expr.getArguments()) {
            argument.expression = argument.expression.accept(this, params);
          }
          return expr;
        }
      };
      visitor.visitFunctionHeader(function, null);
    }

    def.setResolved();
    if (myResolverListener != null) {
      myResolverListener.definitionResolved(def);
    }

    return null;
  }

  private void visitEliminatedReferences(ExpressionResolveNameVisitor exprVisitor, List<? extends Concrete.ReferenceExpression> eliminatedReferences) {
    for (Concrete.ReferenceExpression eliminatedReference : eliminatedReferences) {
      exprVisitor.resolveLocal(eliminatedReference);
    }
  }

  private void addNotEliminatedParameters(List<? extends Concrete.Parameter> parameters, List<? extends Concrete.ReferenceExpression> eliminated, List<TypedReferable> context) {
    if (eliminated.isEmpty()) {
      return;
    }

    Set<Referable> referables = eliminated.stream().map(Concrete.ReferenceExpression::getReferent).collect(Collectors.toSet());
    for (Concrete.Parameter parameter : parameters) {
      AbstractBody abstractBody = TypingInfoVisitor.resolveAbstractBody(parameter.getType());
      for (Referable referable : parameter.getReferableList()) {
        if (referable != null && !referable.textRepresentation().equals("_") && !referables.contains(referable)) {
          context.add(new TypedReferable(referable, abstractBody));
        }
      }
    }
  }

  private ExpressionResolveNameVisitor resolveDataHeader(Concrete.DataDefinition def, Scope scope) {
     if (def.getStage().ordinal() >= Concrete.Stage.HEADER_RESOLVED.ordinal()) {
      return null;
    }

    myLocalErrorReporter = new ConcreteProxyErrorReporter(def);

    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(scope, new ArrayList<>(), myTypingInfo, myLocalErrorReporter, myResolverListener, visitLevelParameters(def.getPLevelParameters()), visitLevelParameters(def.getHLevelParameters()));
    exprVisitor.visitParameters(def.getParameters(), null);
    return exprVisitor;
  }

  @Override
  public Void visitData(Concrete.DataDefinition def, Scope scope) {
    if (def.getStage().ordinal() >= Concrete.Stage.RESOLVED.ordinal()) {
      return null;
    }

    scope = new PrivateFilteredScope(scope);
    ExpressionResolveNameVisitor exprVisitor = resolveDataHeader(def, scope);
    List<? extends Referable> pLevels = visitLevelParameters(def.getPLevelParameters());
    List<? extends Referable> hLevels = visitLevelParameters(def.getHLevelParameters());
    if (exprVisitor == null) exprVisitor = new ExpressionResolveNameVisitor(scope, new ArrayList<>(), myTypingInfo, myLocalErrorReporter, myResolverListener, pLevels, hLevels);
    List<TypedReferable> context = exprVisitor.getContext();
    checkNameAndPrecedence(def);

    Map<String, TCReferable> constructorNames = new HashMap<>();
    for (Concrete.ConstructorClause clause : def.getConstructorClauses()) {
      for (Concrete.Constructor constructor : clause.getConstructors()) {
        TCReferable ref = constructor.getData();
        TCReferable oldRef = constructorNames.putIfAbsent(ref.textRepresentation(), ref);
        if (oldRef != null) {
          myLocalErrorReporter.report(new DuplicateNameError(GeneralError.Level.ERROR, ref, oldRef));
        }
        if (constructor.isCoerce() && constructor.getParameters().isEmpty()) {
          myLocalErrorReporter.report(new ParsingError(ParsingError.Kind.COERCE_WITHOUT_PARAMETERS, constructor));
        }
      }
    }

    if (def.getEliminatedReferences() != null) {
      visitEliminatedReferences(exprVisitor, def.getEliminatedReferences());
    } else {
      for (Concrete.ConstructorClause clause : def.getConstructorClauses()) {
        for (Concrete.Constructor constructor : clause.getConstructors()) {
          visitConstructor(constructor, scope, context, pLevels, hLevels);
        }
      }
    }

    if (def.getEliminatedReferences() != null) {
      context.clear();
      addNotEliminatedParameters(def.getParameters(), def.getEliminatedReferences(), context);
      for (Concrete.ConstructorClause clause : def.getConstructorClauses()) {
        try (Utils.ContextSaver ignore = new Utils.ContextSaver(context)) {
          visitConstructorClause(clause, exprVisitor);
          for (Concrete.Constructor constructor : clause.getConstructors()) {
            visitConstructor(constructor, scope, context, pLevels, hLevels);
          }
        }
      }
    }

    def.setResolved();
    SyntacticDesugarVisitor.desugar(def, myLocalErrorReporter);
    if (myResolverListener != null) {
      myResolverListener.definitionResolved(def);
    }

    return null;
  }

  private void visitConstructor(Concrete.Constructor def, Scope parentScope, List<TypedReferable> context, List<? extends Referable> pLevels, List<? extends Referable> hLevels) {
    checkNameAndPrecedence(def);

    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(parentScope, context, myTypingInfo, myLocalErrorReporter, myResolverListener, pLevels, hLevels);
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(context)) {
      exprVisitor.visitParameters(def.getParameters(), null);
      if (def.getResultType() != null) {
        def.setResultType(def.getResultType().accept(exprVisitor, null));
      }
      visitEliminatedReferences(exprVisitor, def.getEliminatedReferences());
    }

    try (Utils.ContextSaver ignored = new Utils.ContextSaver(context)) {
      addNotEliminatedParameters(def.getParameters(), def.getEliminatedReferences(), context);
      exprVisitor.visitClauses(def.getClauses(), null);
    }
  }

  private void visitConstructorClause(Concrete.ConstructorClause clause, ExpressionResolveNameVisitor exprVisitor) {
    List<Concrete.Pattern> patterns = clause.getPatterns();
    if (patterns != null) {
      exprVisitor.visitPatterns(patterns, new HashMap<>());
    }
  }

  private void resolveSuperClasses(Concrete.ClassDefinition def, Scope scope, boolean resolveLevels) {
    if (def.getSuperClasses().isEmpty()) {
      return;
    }

    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(Prelude.DEP_ARRAY == null ? scope : new ElimScope(scope, Collections.singleton(Prelude.DEP_ARRAY.getRef())), null, myTypingInfo, myErrorReporter, myResolverListener, visitLevelParameters(def.getPLevelParameters()), visitLevelParameters(def.getHLevelParameters()));
    for (int i = 0; i < def.getSuperClasses().size(); i++) {
      Concrete.ReferenceExpression superClass = def.getSuperClasses().get(i);
      Concrete.Expression resolved = exprVisitor.visitReference(superClass, true, resolveLevels);
      Referable ref = RedirectingReferable.getOriginalReferable(superClass.getReferent());
      if (resolved == superClass && myTypingInfo.getBodyDynamicScopeProvider(ref) != null) {
        superClass.setReferent(ref);
      } else {
        if (!(ref instanceof ErrorReference)) {
          myLocalErrorReporter.report(new NameResolverError("Expected a class", superClass));
        }
        def.getSuperClasses().remove(i--);
      }
    }
  }

  @Override
  public Void visitClass(Concrete.ClassDefinition def, Scope scope) {
    if (def.getStage().ordinal() >= Concrete.Stage.RESOLVED.ordinal()) {
      return null;
    }

    scope = new PrivateFilteredScope(scope);
    myLocalErrorReporter = new ConcreteProxyErrorReporter(def);

    checkNameAndPrecedence(def);

    if (def.isRecord() && def.withoutClassifying()) {
      myErrorReporter.report(new ParsingError(ParsingError.Kind.CLASSIFYING_FIELD_IN_RECORD, def));
    }

    List<Concrete.ClassField> classFields = new ArrayList<>();
    for (Concrete.ClassElement element : def.getElements()) {
      if (element instanceof Concrete.ClassField) {
        classFields.add((Concrete.ClassField) element);
      }
    }

    Map<String, TCReferable> fieldNames = new HashMap<>();
    for (Concrete.ClassField field : classFields) {
      TCReferable ref = field.getData();
      TCReferable oldRef = fieldNames.putIfAbsent(ref.textRepresentation(), ref);
      if (oldRef != null) {
        myLocalErrorReporter.report(new DuplicateNameError(GeneralError.Level.ERROR, ref, oldRef));
      }
    }

    resolveSuperClasses(def, scope, true);

    List<TypedReferable> context = new ArrayList<>();
    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(scope, context, myTypingInfo, myLocalErrorReporter, myResolverListener, visitLevelParameters(def.getPLevelParameters()), visitLevelParameters(def.getHLevelParameters()));
    Concrete.Expression previousType = null;
    for (int i = 0; i < classFields.size(); i++) {
      Concrete.ClassField field = classFields.get(i);
      checkNameAndPrecedence(field);

      Concrete.Expression fieldType = field.getResultType();
      if (fieldType == previousType && field.getParameters().isEmpty()) {
        field.setResultType(classFields.get(i - 1).getResultType());
        field.setResultTypeLevel(classFields.get(i - 1).getResultTypeLevel());
      } else {
        try (Utils.ContextSaver ignore = new Utils.ContextSaver(context)) {
          previousType = field.getParameters().isEmpty() ? fieldType : null;
          exprVisitor.visitParameters(field.getParameters(), null);
          field.setResultType(fieldType.accept(exprVisitor, null));
          if (field.getResultTypeLevel() != null) {
            field.setResultTypeLevel(field.getResultTypeLevel().accept(exprVisitor, null));
          }
        }
      }
    }

    DynamicScopeProvider dynamicScopeProvider = myTypingInfo.getDynamicScopeProvider(def.getData());
    if (dynamicScopeProvider == null) dynamicScopeProvider = new EmptyDynamicScopeProvider(def.getData());
    for (Concrete.ClassElement element : def.getElements()) {
      if (element instanceof Concrete.ClassFieldImpl) {
        exprVisitor.visitClassFieldImpl((Concrete.ClassFieldImpl) element, dynamicScopeProvider);
      } else if (element instanceof Concrete.OverriddenField field) {
        exprVisitor.visitClassFieldReference(field, field.getOverriddenField(), dynamicScopeProvider);
        try (Utils.ContextSaver ignore = new Utils.ContextSaver(context)) {
          exprVisitor.visitParameters(field.getParameters(), null);
          field.setResultType(field.getResultType().accept(exprVisitor, null));
          if (field.getResultTypeLevel() != null) {
            field.setResultTypeLevel(field.getResultTypeLevel().accept(exprVisitor, null));
          }
        }
      }
    }

    if ((def.isRecord() || def.withoutClassifying()) && def.isForcedClassifyingField()) {
      myLocalErrorReporter.report(new ParsingError(def.isRecord() ? ParsingError.Kind.CLASSIFYING_FIELD_IN_RECORD : ParsingError.Kind.CLASSIFYING_IGNORED, def));
      def.setClassifyingField(def.getClassifyingField(), false);
    }

    def.setResolved();
    SyntacticDesugarVisitor.desugar(def, myLocalErrorReporter);
    if (myResolverListener != null) {
      myResolverListener.definitionResolved(def);
    }

    return null;
  }

  private static Scope makeScope(Group group, Scope parentScope, boolean isDynamicScope) {
    return parentScope == null ? null : LexicalScope.insideOf(group, parentScope, isDynamicScope);
  }

  private boolean addExternalParameters(Concrete.GeneralDefinition def) {
    List<? extends Concrete.Parameter> defParams = def == null || !(def.getData() instanceof TCDefReferable) ? Collections.emptyList() : def.getParameters();
    if (defParams.isEmpty()) {
      return false;
    }

    List<Concrete.Parameter> newParams = new ArrayList<>(defParams.size());
    for (Concrete.Parameter defParam : defParams) {
      if (defParam.getType() != null) {
        Concrete.Parameter newParam = defParam.copy(defParam.getData());
        if (newParam instanceof Concrete.TypeParameter) {
          ((Concrete.TypeParameter) newParam).type = defParam.getType().accept(new SubstConcreteVisitor(null), null);
        }
        newParams.add(newParam);
      } else {
        newParams.add(defParam);
      }
    }
    myExternalParameters.put((TCDefReferable) def.getData(), new Concrete.ExternalParameters(newParams, def instanceof Concrete.Definition ? ((Concrete.Definition) def).getPLevelParameters() : null, def instanceof Concrete.Definition ? ((Concrete.Definition) def).getHLevelParameters() : null));
    return true;
  }

  public void resolveGroup(Group group, Scope scope, Map<LongName, GroupData.DefinitionData> definitionData) {
    LocatedReferable groupRef = group.getReferable();
    Collection<? extends Statement> statements = group.getStatements();
    Collection<? extends Group> dynamicSubgroups = group.getDynamicSubgroups();

    Concrete.GeneralDefinition def = myConcreteProvider.getConcrete(groupRef);
    Scope cachedScope = CachingScope.make(makeScope(group, scope, false));
    myLocalErrorReporter = new LocalErrorReporter(groupRef, myErrorReporter);
    if (def instanceof Concrete.ClassDefinition) {
      resolveSuperClasses((Concrete.ClassDefinition) def, new PrivateFilteredScope(cachedScope), false);
    }

    for (ParameterReferable parameter : group.getExternalParameters()) {
      parameter.resolve(cachedScope);
    }

    Scope docScope;
    DynamicScopeProvider dynamicScopeProvider = def == null ? null : myTypingInfo.getDynamicScopeProvider(def.getData());
    if (def instanceof Concrete.ResolvableDefinition) {
      Scope classScope = dynamicScopeProvider == null ? cachedScope : new MergeScope(new DynamicScope(dynamicScopeProvider, myTypingInfo, DynamicScope.Extent.WITH_SUPER_DYNAMIC), cachedScope);
      ((Concrete.ResolvableDefinition) def).accept(this, classScope);

      List<Referable> parameters = new ArrayList<>();
      for (Concrete.Parameter parameter : def.getParameters()) {
        for (Referable referable : parameter.getRefList()) {
          if (referable != null) parameters.add(referable);
        }
      }
      docScope = parameters.isEmpty() ? classScope : new ListScope(classScope, parameters);

      if (definitionData != null) definitionData.putIfAbsent(def.getData().getRefLongName(), new GroupData.DefinitionData((Concrete.ResolvableDefinition) def, CachingScope.make(new InstanceScope(cachedScope))));
    } else {
      docScope = cachedScope;
    }

    if (def instanceof Concrete.Definition && !myExternalParameters.isEmpty()) {
      ((Concrete.Definition) def).setExternalParameters(new HashMap<>(myExternalParameters));
    }

    boolean isTopLevel = group.isTopLevel();
    Scope namespaceScope = CachingScope.make(new LexicalScope(scope, group, null, true, false));
    if (myResolverListener != null && myResolverListener != ResolverListener.EMPTY) {
      group.getDescription().accept(this, docScope);
    }

    for (Statement statement : statements) {
      NamespaceCommand namespaceCommand = statement.getNamespaceCommand();
      if (namespaceCommand == null) {
        continue;
      }
      List<String> path = namespaceCommand.getPath();
      NamespaceCommand.Kind kind = namespaceCommand.getKind();
      if (path.isEmpty() || kind == NamespaceCommand.Kind.IMPORT && !isTopLevel) {
        continue;
      }

      LongUnresolvedReference reference = new LongUnresolvedReference(namespaceCommand, namespaceCommand.getReferenceList(), path);
      Scope importedScope = kind == NamespaceCommand.Kind.IMPORT ? namespaceScope.getImportedSubscope() : namespaceScope;
      List<Referable> resolvedRefs = myResolverListener == null ? null : new ArrayList<>();
      reference.resolve(importedScope, resolvedRefs, myResolverListener);
      if (myResolverListener != null) {
        myResolverListener.namespaceResolved(namespaceCommand, resolvedRefs);
      }
      Scope curScope = reference.resolveNamespace(importedScope);
      if (curScope == null) {
        myErrorReporter.report(reference.getErrorReference().getError());
      }

      if (curScope != null) {
        for (NameRenaming renaming : namespaceCommand.getOpenedReferences()) {
          Referable oldRef = renaming.getOldReference();
          Referable ref = ExpressionResolveNameVisitor.resolve(oldRef, new PrivateFilteredScope(curScope, true), null);
          if (myResolverListener != null) {
            myResolverListener.renamingResolved(renaming, oldRef, ref);
          }
          if (ref instanceof ErrorReference) {
            myErrorReporter.report(((ErrorReference) ref).getError());
          }
        }

        for (NameHiding nameHiding : namespaceCommand.getHiddenReferences()) {
          Referable ref = ExpressionResolveNameVisitor.resolve(nameHiding.getHiddenReference(), new PrivateFilteredScope(curScope, true), nameHiding.getScopeContext());
          if (ref instanceof ErrorReference) {
            myErrorReporter.report(((ErrorReference) ref).getError());
          }
        }
      }
    }

    boolean added = addExternalParameters(def);
    for (Statement statement : statements) {
      Group subgroup = statement.getGroup();
      if (subgroup != null) {
        resolveGroup(subgroup, cachedScope, definitionData);
      }
    }
    if (!dynamicSubgroups.isEmpty()) {
      Scope dynamicScope = CachingScope.make(makeScope(group, scope, true));
      if (dynamicScopeProvider != null) {
        dynamicScope = new MergeScope(dynamicScope, new DynamicScope(dynamicScopeProvider, myTypingInfo, DynamicScope.Extent.WITH_SUPER_DYNAMIC));
      }
      for (Group subgroup : dynamicSubgroups) {
        resolveGroup(subgroup, dynamicScope, definitionData);
      }
    }
    if (added) {
      assert def != null;
      myExternalParameters.remove((TCDefReferable) def.getData());
    }

    myLocalErrorReporter = myErrorReporter;

    // Some checks

    Collection<? extends Group.InternalReferable> fields = group.getFields();
    if (!fields.isEmpty()) {
      Map<String, Pair<LocatedReferable, ClassReferable>> superFields = collectClassFields(groupRef);
      for (Group.InternalReferable internalRef : fields) {
        checkField(internalRef.getReferable(), superFields);
      }
    }

    Map<String, LocatedReferable> referables = new HashMap<>();
    for (Group.InternalReferable internalRef : group.getInternalReferables()) {
      LocatedReferable ref = internalRef.getReferable();
      String name = ref.textRepresentation();
      if (!name.isEmpty() && !"_".equals(name)) {
        referables.putIfAbsent(name, ref);
      }
    }

    for (Statement statement : statements) {
      Group subgroup = statement.getGroup();
      if (subgroup != null) {
        checkReference(subgroup.getReferable(), referables, false);
      }
    }

    for (Group subgroup : dynamicSubgroups) {
      checkReference(subgroup.getReferable(), referables, false);
    }

    for (Group subgroup : dynamicSubgroups) {
      checkSubgroup(subgroup, referables);
    }

    for (Statement statement : statements) {
      Group subgroup = statement.getGroup();
      if (subgroup != null) {
        checkSubgroup(subgroup, referables);
      }
    }

    class NamespaceStruct {
      final Scope.ScopeContext context;
      final NamespaceCommand command;
      final Map<String, Referable> refMap;

      NamespaceStruct(Scope.ScopeContext context, NamespaceCommand command, Map<String, Referable> refMap) {
        this.context = context;
        this.command = command;
        this.refMap = refMap;
      }
    }

    List<NamespaceStruct> namespaces = new ArrayList<>();
    for (Statement statement : statements) {
      NamespaceCommand cmd = statement.getNamespaceCommand();
      if (cmd == null) {
        continue;
      }
      if (!isTopLevel && cmd.getKind() == NamespaceCommand.Kind.IMPORT) {
        myLocalErrorReporter.report(new ParsingError(ParsingError.Kind.MISPLACED_IMPORT, cmd));
      } else {
        checkNamespaceCommand(cmd, referables.keySet());
      }
      for (Scope.ScopeContext context : Scope.ScopeContext.values()) {
        Collection<? extends Referable> elements = NamespaceCommandNamespace.resolveNamespace(cmd.getKind() == NamespaceCommand.Kind.IMPORT ? namespaceScope.getImportedSubscope() : namespaceScope, cmd).getElements(context);
        if (!elements.isEmpty()) {
          Map<String, Referable> map = new LinkedHashMap<>();
          for (Referable element : elements) {
            map.put(element.getRefName(), element);
          }
          namespaces.add(new NamespaceStruct(context, cmd, map));
        }
      }
    }

    for (int i = 0; i < namespaces.size(); i++) {
      NamespaceStruct struct = namespaces.get(i);
      for (Map.Entry<String, Referable> entry : struct.refMap.entrySet()) {
        if (!(struct.context == Scope.ScopeContext.STATIC || struct.context == Scope.ScopeContext.DYNAMIC) || referables.containsKey(entry.getKey())) {
          continue;
        }

        for (int j = i + 1; j < namespaces.size(); j++) {
          if (!struct.context.equals(namespaces.get(j).context)) continue;
          Referable ref = namespaces.get(j).refMap.get(entry.getKey());
          if (ref != null && !ref.equals(entry.getValue())) {
            NamespaceCommand nsCmd = namespaces.get(j).command;
            Object cause = nsCmd;
            for (NameRenaming renaming : nsCmd.getOpenedReferences()) {
              String name = renaming.getName();
              if (entry.getKey().equals(name != null ? name : renaming.getOldReference().textRepresentation())) {
                cause = renaming;
                break;
              }
            }
            myLocalErrorReporter.report(new DuplicateOpenedNameError(struct.context, ref, struct.command, cause));
            if (ref instanceof LocatedReferable) {
              referables.putIfAbsent(ref.getRefName(), (LocatedReferable) ref);
            }
          }
        }
      }
    }
  }

  private static Map<String, Pair<LocatedReferable, ClassReferable>> collectClassFields(LocatedReferable referable) {
    Collection<? extends ClassReferable> superClasses = referable instanceof ClassReferable ? ((ClassReferable) referable).getSuperClassReferences() : Collections.emptyList();
    if (superClasses.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, Pair<LocatedReferable, ClassReferable>> fields = new HashMap<>();
    Set<ClassReferable> visited = new HashSet<>();
    visited.add((ClassReferable) referable);
    Deque<ClassReferable> toVisit = new ArrayDeque<>(superClasses);
    while (!toVisit.isEmpty()) {
      ClassReferable superClass = toVisit.pop();
      if (!visited.add(superClass)) {
        continue;
      }

      for (LocatedReferable fieldRef : superClass.getFieldReferables()) {
        String name = fieldRef.textRepresentation();
        if (!name.isEmpty() && !"_".equals(name)) {
          fields.putIfAbsent(name, new Pair<>(fieldRef, superClass));
        }
      }

      toVisit.addAll(superClass.getSuperClassReferences());
    }

    return fields;
  }

  private void checkField(LocatedReferable field, Map<String, Pair<LocatedReferable, ClassReferable>> fields) {
    if (field == null || fields.isEmpty()) {
      return;
    }

    String name = field.textRepresentation();
    if (!name.isEmpty() && !"_".equals(name)) {
      Pair<LocatedReferable, ClassReferable> oldField = fields.get(name);
      if (oldField != null) {
        myLocalErrorReporter.report(new ReferenceError(GeneralError.Level.WARNING, GeneralError.Stage.RESOLVER, "Field '" + field.textRepresentation() + ("' is already defined in super class " + oldField.proj2.textRepresentation()), field));
      }
    }
  }

  private void checkNamespaceCommand(NamespaceCommand cmd, Set<String> defined) {
    if (defined == null) {
      return;
    }

    for (NameRenaming renaming : cmd.getOpenedReferences()) {
      String name = renaming.getName();
      if (name == null) {
        name = renaming.getOldReference().textRepresentation();
      }
      if (defined.contains(name)) {
        myLocalErrorReporter.report(new ExistingOpenedNameError(renaming));
      }
    }
  }

  private void checkSubgroup(Group subgroup, Map<String, LocatedReferable> referables) {
    for (Group.InternalReferable internalReferable : subgroup.getInternalReferables()) {
      if (internalReferable.isVisible()) {
        checkReference(internalReferable.getReferable(), referables, true);
      }
    }
    for (Group.InternalReferable internalReferable : subgroup.getInternalReferables()) {
      if (internalReferable.isVisible()) {
        LocatedReferable newRef = internalReferable.getReferable();
        String name = newRef.textRepresentation();
        if (!name.isEmpty() && !"_".equals(name)) {
          referables.putIfAbsent(name, newRef);
        }
      }
    }
  }

  private void checkReference(LocatedReferable newRef, Map<String, LocatedReferable> referables, boolean isInternal) {
    String name = newRef.textRepresentation();
    if (name.isEmpty() || "_".equals(name)) {
      return;
    }

    LocatedReferable oldRef = isInternal ? referables.get(name) : referables.putIfAbsent(name, newRef);
    if (oldRef != null) {
      myLocalErrorReporter.report(new DuplicateNameError(isInternal ? GeneralError.Level.WARNING : GeneralError.Level.ERROR, newRef, oldRef));
    }
  }
}
