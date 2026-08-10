package org.arend.core.definition;

import org.arend.core.context.binding.LevelVariable;
import org.arend.core.context.binding.inference.InferenceLevelVariable;
import org.arend.core.context.param.DependentLink;
import org.arend.core.context.param.EmptyDependentLink;
import org.arend.core.expr.ClassCallExpression;
import org.arend.core.expr.Expression;
import org.arend.core.sort.Level;
import org.arend.core.subst.Levels;
import org.arend.core.subst.ListLevels;
import org.arend.ext.core.definition.CoreDefinition;
import org.arend.ext.util.Pair;
import org.arend.extImpl.userData.UserDataHolderImpl;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.implicitargs.equations.Equations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;

public abstract class Definition extends UserDataHolderImpl implements CoreDefinition {
  private final TCDefReferable myReferable;
  private TypeCheckingStatus myStatus;

  public Definition(TCDefReferable referable, TypeCheckingStatus status) {
    myReferable = referable;
    myStatus = status;
  }

  @NotNull
  @Override
  public String getName() {
    return myReferable.textRepresentation();
  }

  @NotNull
  @Override
  public TCDefReferable getRef() {
    return myReferable;
  }

  public TCDefReferable getReferable() {
    return myReferable;
  }

  @Override
  public @NotNull Set<? extends TopLevelDefinition> getRecursiveDefinitions() {
    return Collections.emptySet();
  }

  public abstract TopLevelDefinition getTopLevelDefinition();

  public @NotNull List<? extends LevelVariable> getLevelParameters() {
    return getTopLevelDefinition().getLevelParameters();
  }

  public Set<? extends FunctionDefinition> getAxioms() {
    return getTopLevelDefinition().getAxioms();
  }

  public Set<? extends Definition> getGoals() {
    return getTopLevelDefinition().getGoals();
  }

  public List<? extends Pair<TCDefReferable,Integer>> getParametersOriginalDefinitions() {
    return Collections.emptyList();
  }

  public boolean isIdLevels(Levels levels) {
    List<? extends LevelVariable> vars = getLevelParameters();
    List<? extends Level> list = levels.toList();
    if (list.size() != vars.size()) {
      return false;
    }
    for (int i = 0; i < vars.size(); i++) {
      Level level = list.get(i);
      if (!vars.get(i).equals(level.getSingleVar())) {
        return false;
      }
    }
    return true;
  }

  public Levels makeIdLevels() {
    List<? extends LevelVariable> vars = getLevelParameters();
    List<Level> result = new ArrayList<>(vars.size());
    for (LevelVariable var : vars) {
      result.add(new Level(var));
    }
    return new ListLevels(result);
  }

  public Levels makeMinLevels() {
    List<? extends LevelVariable> vars = getLevelParameters();
    List<Level> result = new ArrayList<>(vars.size());
    for (LevelVariable ignored : vars) {
      result.add(new Level(BigInteger.ZERO));
    }
    return new ListLevels(result);
  }

  public Levels generateInferVars(Equations equations, Concrete.SourceNode sourceNode, boolean isGenerated) {
    List<? extends LevelVariable> vars = getLevelParameters();
    List<Level> result = new ArrayList<>(vars.size());
    for (LevelVariable ignored : vars) {
      InferenceLevelVariable infVar = new InferenceLevelVariable(sourceNode, isGenerated);
      equations.addVariable(infVar);
      result.add(new Level(infVar));
    }
    return new ListLevels(result);
  }

  @NotNull
  @Override
  public DependentLink getParameters() {
    return EmptyDependentLink.getInstance();
  }

  public void setParameters(DependentLink parameters) {

  }

  public boolean hasStrictParameters() {
    return false;
  }

  public boolean isStrict(int parameter) {
    return false;
  }

  public boolean hasEnclosingClass() {
    return false;
  }

  public ClassDefinition getEnclosingClass() {
    if (hasEnclosingClass()) {
      DependentLink parameters = getParameters();
      if (!parameters.hasNext()) {
        return null;
      }
      Expression type = parameters.getType();
      return type instanceof ClassCallExpression ? ((ClassCallExpression) type).getDefinition() : null;
    } else {
      return null;
    }
  }

  public abstract Expression getTypeWithParams(List<? super DependentLink> params, Levels levels);

  @Override
  public @Nullable CoerceData getCoerceData() {
    return null;
  }

  public int getVisibleParameter() {
    return -1;
  }

  public boolean isHideable() {
    return getVisibleParameter() >= 0;
  }

  public List<Integer> getParametersTypecheckingOrder() {
    return null;
  }

  public void setParametersTypecheckingOrder(List<Integer> order) {

  }

  public List<Boolean> getGoodThisParameters() {
    return Collections.emptyList();
  }

  public boolean isGoodParameter(int index) {
    List<Boolean> goodParameters = getGoodThisParameters();
    return index < goodParameters.size() && goodParameters.get(index);
  }

  public void setGoodThisParameters(List<Boolean> goodThisParameters) {

  }

  public enum TypeClassParameterKind { NO, YES, ONLY_LOCAL }

  public List<TypeClassParameterKind> getTypeClassParameters() {
    return Collections.emptyList();
  }

  public TypeClassParameterKind getTypeClassParameterKind(int index) {
    List<TypeClassParameterKind> typeClassParameters = getTypeClassParameters();
    return index < typeClassParameters.size() ? typeClassParameters.get(index) : TypeClassParameterKind.NO;
  }

  public void setTypeClassParameters(List<TypeClassParameterKind> typeClassParameters) {

  }

  public List<? extends ParametersLevel> getParametersLevels() {
    return Collections.emptyList();
  }

  public enum TypeCheckingStatus {
    HAS_ERRORS, HAS_WARNINGS, NO_ERRORS, TYPE_CHECKING, NEEDS_TYPE_CHECKING;

    public boolean isOK() {
      return this.ordinal() >= NO_ERRORS.ordinal();
    }

    public boolean headerIsOK() {
      return this != NEEDS_TYPE_CHECKING;
    }

    public boolean hasErrors() {
      return this == HAS_ERRORS;
    }

    public boolean needsTypeChecking() {
      return this == NEEDS_TYPE_CHECKING || this == TYPE_CHECKING;
    }

    public boolean noErrors() {
      return this == NO_ERRORS;
    }

    public TypeCheckingStatus max(TypeCheckingStatus status) {
      return ordinal() <= status.ordinal() ? this : status;
    }
  }

  public TypeCheckingStatus status() {
    return myStatus;
  }

  public void setStatus(TypeCheckingStatus status) {
    myStatus = status;
  }

  public void addStatus(TypeCheckingStatus status) {
    myStatus = myStatus.needsTypeChecking() && !status.needsTypeChecking() ? status : myStatus.max(status);
  }

  public abstract <P, R> R accept(DefinitionVisitor<? super P, ? extends R> visitor, P params);

  @Override
  public String toString() {
    return myReferable.toString();
  }
}
