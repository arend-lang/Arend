package org.arend.lib.meta.cases;

import org.arend.ext.concrete.ConcreteAppBuilder;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.definition.ConcreteMetaDefinition;
import org.arend.ext.concrete.expr.*;
import org.arend.ext.error.NameResolverError;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.reference.ExpressionResolver;
import org.arend.ext.typechecking.*;
import org.arend.lib.StdExtension;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MatchingCasesMetaTypechecker implements MetaTypechecker, MetaResolver {
  private final StdExtension ext;
  private final CasesMetaTypechecker casesResolver;

  public MatchingCasesMetaTypechecker(StdExtension ext, CasesMetaTypechecker casesResolver) {
    this.ext = ext;
    this.casesResolver = casesResolver;
  }

  @Override
  public @Nullable ConcreteExpression resolvePrefix(@NotNull ExpressionResolver resolver, @NotNull ContextData contextData) {
    if (!new ContextDataChecker() {
      @Override
      public boolean @Nullable [] argumentExplicitness() {
        return new boolean[] { false, false, true, true };
      }

      @Override
      public int numberOfOptionalExplicitArguments() {
        return 2;
      }

      @Override
      public boolean allowClauses() {
        return true;
      }
    }.checkContextData(contextData, resolver.getErrorReporter())) {
      return null;
    }

    ConcreteFactory factory = ext.factory.withData(contextData.getMarker());
    List<? extends ConcreteArgument> args = contextData.getArguments();

    int paramsIndex = -1;
    if (!args.isEmpty() && !args.get(0).isExplicit()) {
      if (args.size() >= 2 && !args.get(1).isExplicit()) {
        paramsIndex = 1;
      } else {
        if (args.getFirst().getExpression() instanceof ConcreteAppExpression) {
          paramsIndex = 0;
        } else if (args.getFirst().getExpression() instanceof ConcreteTupleExpression tupleExpr) {
          if (!tupleExpr.getFields().isEmpty() && tupleExpr.getFields().getFirst() instanceof ConcreteAppExpression) {
            paramsIndex = 0;
          }
        }
      }
    }
    boolean hasDefinitionArg = !args.isEmpty() && !args.get(0).isExplicit() && paramsIndex != 0;
    int caseArgsIndex = -1;
    int defaultIndex = -1;
    int firstExplicitIndex = paramsIndex >= 0 ? paramsIndex + 1 : hasDefinitionArg ? 1 : 0;
    if (firstExplicitIndex + 1 < args.size()) {
      caseArgsIndex = firstExplicitIndex;
      if (!(args.get(firstExplicitIndex + 1).getExpression() instanceof ConcreteHoleExpression)) {
        defaultIndex = firstExplicitIndex + 1;
      }
    } else if (firstExplicitIndex < args.size()) {
      defaultIndex = firstExplicitIndex;
    }

    ConcreteAppBuilder builder = factory.appBuilder(contextData.getReferenceExpression());
    if (hasDefinitionArg) {
      builder.app(resolver.resolve(args.getFirst().getExpression()), false);
    } else if (paramsIndex != -1) {
      builder.app(factory.hole(), false);
    }
    if (paramsIndex != -1) {
      List<ConcreteExpression> fields = new ArrayList<>();
      List<? extends ConcreteExpression> params = Utils.getArgumentList(args.get(paramsIndex).getExpression());
      for (ConcreteExpression param : params) {
        List<ConcreteArgument> seq = param.getArgumentsSequence();
        if (seq.size() == 2 && seq.get(0).getExpression() instanceof ConcreteReferenceExpression && seq.get(1).isExplicit() && ((ConcreteReferenceExpression) seq.get(0).getExpression()).getReferent().getRefName().equals(casesResolver.argRef.getRefName())) {
          ConcreteExpression field = casesResolver.parameter.resolve(resolver, seq.get(1).getExpression(), false, true);
          if (field != null) fields.add(field);
        } else {
          resolver.getErrorReporter().report(new NameResolverError("Expected 'arg' with one explicit argument", param));
        }
      }
      builder.app(factory.tuple(fields), false);
    }

    ConcreteExpression caseArgs = caseArgsIndex == -1 ? factory.tuple() : casesResolver.resolveArgument(resolver, args.get(caseArgsIndex).getExpression());
    builder.app(caseArgs);
    builder.app(resolver.resolve(factory.caseExpr(false, Collections.emptyList(), null, null, contextData.getClauses() == null ? Collections.emptyList() : contextData.getClauses().getClauseList())));
    if (defaultIndex != -1) {
      builder.app(casesResolver.resolveDefaultClause(resolver, args.get(defaultIndex).getExpression(), caseArgs));
    } else {
      casesResolver.resolveDefaultClause(resolver, null, caseArgs);
    }
    return builder.build();
  }

  @Override
  public @Nullable MetaDefinition typecheck(@NotNull ExpressionTypechecker typechecker, @NotNull ConcreteMetaDefinition definition) {
    List<ArendRef> refs = Utils.extractReferences(definition, 1, typechecker.getErrorReporter());
    return refs == null ? null : new MatchingCasesMeta(ext, casesResolver, refs.getFirst());
  }
}
