package org.arend.ext.typechecking.meta;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.definition.ConcreteMetaDefinition;
import org.arend.ext.concrete.expr.*;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.module.LongName;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.DeferredMetaDefinition;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.MetaDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class DependencyMetaTypechecker implements MetaTypechecker {
  private final List<LongName> names;
  private final List<Field> fields;
  private final Supplier<MetaDefinition> metaSupplier;

  public DependencyMetaTypechecker(@NotNull Class<? extends MetaDefinition> container, @NotNull Supplier<MetaDefinition> metaSupplier) {
    names = new ArrayList<>();
    fields = new ArrayList<>();
    this.metaSupplier = metaSupplier;

    for (Field field : container.getDeclaredFields()) {
      if (ArendRef.class.equals(field.getType())) {
        Dependency dependency = field.getAnnotation(Dependency.class);
        if (dependency != null) {
          field.setAccessible(true);
          String name = dependency.name();
          names.add(name.isEmpty() ? new LongName(field.getName()) : LongName.fromString(name));
          fields.add(field);
        }
      }
    }

    if (names.isEmpty()) {
      throw new IllegalArgumentException();
    }
  }

  public static List<ArendRef> extractReferences(ConcreteMetaDefinition definition, int numberOfReferences, ErrorReporter errorReporter) {
    if (definition.getBody() instanceof ConcreteAppExpression appExpr && appExpr.getFunction() instanceof ConcreteHoleExpression && appExpr.getArguments().size() == 1 && appExpr.getArguments().getFirst().isExplicit()) {
      ConcreteExpression argument = appExpr.getArguments().getFirst().getExpression();
      if (argument instanceof ConcreteReferenceExpression refExpr && numberOfReferences == 1) {
        return Collections.singletonList(refExpr.getReferent());
      } else if (argument instanceof ConcreteTupleExpression tuple && tuple.getFields().size() == numberOfReferences) {
        List<ArendRef> result = new ArrayList<>(numberOfReferences);
        for (ConcreteExpression field : tuple.getFields()) {
          if (field instanceof ConcreteReferenceExpression refExpr) {
            result.add(refExpr.getReferent());
          } else {
            break;
          }
        }
        if (result.size() == numberOfReferences) {
          return result;
        }
      }
    }

    errorReporter.report(new TypecheckingError("Cannot extract " + numberOfReferences + " references", definition));
    return null;
  }

  public static ConcreteExpression makeReferences(ConcreteFactory factory, List<LongName> references) {
    ConcreteExpression body;
    if (references.size() == 1) {
      body = factory.ref(factory.unresolved(references.getFirst()));
    } else {
      List<ConcreteExpression> fields = new ArrayList<>(references.size());
      for (LongName reference : references) {
        fields.add(factory.ref(factory.unresolved(reference)));
      }
      body = factory.tuple(fields);
    }
    return factory.app(factory.hole(), true, body);
  }

  public List<LongName> getNames() {
    return names;
  }

  public ConcreteExpression makeBody(ConcreteFactory factory) {
    return makeReferences(factory, names);
  }

  @Override
  public @Nullable MetaDefinition typecheck(@NotNull ExpressionTypechecker typechecker, @NotNull ConcreteMetaDefinition definition) {
    List<ArendRef> refs = extractReferences(definition, fields.size(), typechecker.getErrorReporter());
    if (refs == null) return null;
    MetaDefinition meta = metaSupplier.get();
    try {
      MetaDefinition actual = meta instanceof DeferredMetaDefinition deferred ? deferred.deferredMeta : meta;
      for (int i = 0; i < fields.size(); i++) {
        fields.get(i).set(actual, refs.get(i));
      }
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
    return meta;
  }
}
