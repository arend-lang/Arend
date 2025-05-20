package org.arend.extImpl;

import org.arend.core.context.binding.LevelVariable;
import org.arend.core.context.param.DependentLink;
import org.arend.core.definition.Definition;
import org.arend.core.definition.MetaTopDefinition;
import org.arend.ext.concrete.definition.ConcreteMetaDefinition;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaTypechecker;
import org.arend.ext.util.Pair;
import org.arend.ext.variable.Variable;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class DefaultMetaTypechecker implements MetaTypechecker {
  @Override
  public @Nullable MetaDefinition typecheck(@NotNull ExpressionTypechecker expressionTypechecker, @NotNull ConcreteMetaDefinition definition, @NotNull Supplier<@Nullable List<? extends Variable>> levelParametersSupplier, @NotNull Supplier<Pair<CoreParameter, List<Boolean>>> parametersSupplier) {
    if (!(expressionTypechecker instanceof CheckTypeVisitor typechecker && definition instanceof Concrete.MetaDefinition def)) {
      throw new IllegalArgumentException();
    }

    MetaTopDefinition typedDef = new MetaTopDefinition(def.getData());
    typechecker.setDefinition(typedDef);
    typedDef.setStatus(Definition.TypeCheckingStatus.TYPE_CHECKING);

    def.getData().setTypecheckedIfNotCancelled(typedDef);
    List<? extends Variable> levelParams = levelParametersSupplier.get();
    if (levelParams != null) {
      List<LevelVariable> levelVars = new ArrayList<>(levelParams.size());
      for (Variable variable : levelParams) {
        if (!(variable instanceof LevelVariable)) {
          throw new IllegalArgumentException();
        }
        levelVars.add((LevelVariable) variable);
      }
      typedDef.setLevelParameters(levelVars);
    }

    var pair = parametersSupplier.get();
    if (!(pair.proj1 instanceof DependentLink)) {
      throw new IllegalArgumentException();
    }

    typedDef.setParameters((DependentLink) pair.proj1, pair.proj2);
    typedDef.addStatus(Definition.TypeCheckingStatus.NO_ERRORS);
    typechecker.setStatus(def.getStatus().getTypecheckingStatus());
    typedDef.addStatus(typechecker.getStatus());

    return new DefaultMetaDefinition(def);
  }
}
