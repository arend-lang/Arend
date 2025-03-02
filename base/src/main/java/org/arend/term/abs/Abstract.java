package org.arend.term.abs;

import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.reference.Precedence;
import org.arend.naming.reference.GlobalReferable;
import org.arend.ext.concrete.definition.ClassFieldKind;
import org.arend.ext.concrete.definition.FunctionKind;
import org.arend.naming.reference.NamedUnresolvedReference;
import org.arend.naming.reference.UnresolvedReference;
import org.arend.naming.scope.Scope;
import org.arend.term.Fixity;
import org.arend.term.group.AccessModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class Abstract {
  private Abstract() {}

  public interface SourceNode {
    @NotNull SourceNode getTopmostEquivalentSourceNode();
    @Nullable SourceNode getParentSourceNode();
  }

  public interface LamParameter extends SourceNode {
    @Nullable Object getData();
  }

  public interface Parameter extends LamParameter {
    boolean isExplicit();
    boolean isStrict();
    boolean isProperty();
    @NotNull List<? extends AbstractReferable> getReferableList();
    @Nullable Expression getType();
  }

  public interface FieldParameter extends Parameter {
    boolean isClassifying();
    boolean isCoerce();
    ClassFieldKind getClassFieldKind();

    @Override
    default boolean isProperty() {
      return getClassFieldKind() == ClassFieldKind.PROPERTY;
    }
  }

  public interface Clause extends SourceNode {
    @Nullable Object getData();
    @NotNull List<? extends Pattern> getPatterns();
  }

  public interface FunctionClauses extends SourceNode {
    @Nullable Object getData();
    @NotNull List<? extends FunctionClause> getClauseList();
  }

  public interface FunctionClause extends Clause {
    @Nullable Expression getExpression();
  }

  public interface ConstructorClause extends Clause {
    @NotNull Collection<? extends Constructor> getConstructors();
  }

  public interface TypedReferable extends SourceNode {
    @Nullable Object getData();
    @Nullable AbstractReferable getReferable();
    @Nullable Expression getType();
  }

  public interface Pattern extends LamParameter {
    boolean isUnnamed();
    boolean isExplicit();
    boolean isTuplePattern();
    @Nullable Integer getInteger();
    @Nullable AbstractReferable getSingleReferable();
    @Nullable UnresolvedReference getConstructorReference();
    @Nullable Fixity getFixity();
    @NotNull List<? extends Pattern> getSequence();
    @Nullable Expression getType();
    @Nullable TypedReferable getAsPattern();
  }

  public interface Reference extends org.arend.naming.reference.Reference, SourceNode {
  }

  public interface LongReference extends Reference {
    @Nullable Reference getHeadReference();
  }

  // Holder

  public interface ParametersHolder extends SourceNode {
    @NotNull List<? extends Parameter> getParameters();
  }

  public interface LamParametersHolder extends ParametersHolder {
    @NotNull List<? extends LamParameter> getLamParameters();
  }

  public interface LetClausesHolder extends SourceNode {
    @NotNull Collection<? extends LetClause> getLetClauses();
  }

  public interface EliminatedExpressionsHolder extends ParametersHolder {
    @Nullable Collection<? extends Reference> getEliminatedExpressions();
  }

  public interface ClassReferenceHolder extends SourceNode {
    @NotNull Collection<? extends CoClauseElement> getCoClauseElements();
  }

  public interface NameRenaming extends SourceNode {
    @NotNull Scope.ScopeContext getScopeContext();
    @NotNull NamedUnresolvedReference getOldReference();
    @Nullable Precedence getPrecedence();
    @Nullable String getNewName();
  }

  public interface NameHiding extends SourceNode {
    @NotNull Scope.ScopeContext getScopeContext();
    @NotNull NamedUnresolvedReference getHiddenReference();
  }

  public interface NamespaceCommand extends SourceNode {
    boolean isImport();
    /* @NotNull */ @Nullable UnresolvedReference getModuleReference();
    boolean isUsing();
    @Nullable LongReference getOpenedReference();
    @NotNull Collection<? extends NameRenaming> getRenamings();
    @NotNull Collection<? extends NameHiding> getHidings();
  }

  // Group

  public interface Statement {
    @Nullable Group getGroup();
    @Nullable NamespaceCommand getNamespaceCommand();
    @Nullable LevelParameters getPLevelsDefinition();
    @Nullable LevelParameters getHLevelsDefinition();
  }

  public interface AbstractReferable {
    @NotNull String getRefName();
  }

  public interface AbstractLocatedReferable extends AbstractReferable {
    default @NotNull AccessModifier getAccessModifier() {
      return AccessModifier.PUBLIC;
    }

    @NotNull GlobalReferable.Kind getKind();
    @NotNull Precedence getPrecedence();
    @NotNull Precedence getAliasPrecedence();
    @Nullable String getAliasName();
  }

  public interface Group {
    @NotNull Doc getDescription();
    @NotNull Abstract.AbstractLocatedReferable getReferable();
    @Nullable Definition getGroupDefinition();
    @NotNull List<? extends Statement> getStatements();
    @NotNull List<? extends Group> getDynamicSubgroups();
  }

  // Expression

  public enum EvalKind { EVAL, PEVAL, BOX }

  public static final int INFINITY_LEVEL = -33;

  public interface Expression extends SourceNode {
    @Nullable Object getData();
    <P, R> R accept(@NotNull AbstractExpressionVisitor<? super P, ? extends R> visitor, @Nullable P params);
  }

  public interface FieldAcc extends SourceNode {
    @Nullable Object getData();
    @Nullable Integer getNumber();
    @Nullable UnresolvedReference getFieldRef();
  }

  public interface ReferenceExpression extends SourceNode {
    @Nullable Object getData();
    @NotNull UnresolvedReference getReferent();
    @Nullable Collection<? extends LevelExpression> getPLevels();
    @Nullable Collection<? extends LevelExpression> getHLevels();
  }

  public interface CaseArgument extends SourceNode {
    @Nullable Object getApplyHoleData();
    @Nullable Expression getExpression();
    @Nullable AbstractReferable getReferable();
    @Nullable Expression getType();
    @Nullable Reference getEliminatedReference();
  }

  public interface CaseArgumentsHolder extends SourceNode {
    @NotNull List<? extends CaseArgument> getCaseArguments();
  }

  public interface BinOpSequenceElem extends SourceNode {
    /* @NotNull */ @Nullable Expression getExpression();
    boolean isVariable();
    boolean isExplicit();
  }

  public interface Argument extends SourceNode {
    boolean isExplicit();
    /* @NotNull */ @Nullable Expression getExpression();
  }

  public interface ClassElement extends SourceNode {
  }

  public interface CoClauseElement extends ClassElement {
    /* @NotNull */ @Nullable Reference getImplementedField();
  }

  public interface ClassFieldImpl extends CoClauseElement, LamParametersHolder, ClassReferenceHolder {
    @Nullable Object getCoClauseData();
    @Override @NotNull Collection<? extends ClassFieldImpl> getCoClauseElements();
    @Nullable Object getData();
    @Nullable Object getPrec();
    /* @NotNull */ @Nullable Expression getImplementation();
    boolean hasImplementation();
    boolean isDefault();
  }

  public interface CoClauseFunctionReference extends ClassFieldImpl {
    @Nullable Abstract.AbstractLocatedReferable getFunctionReference();
  }

  public interface LetClause extends ParametersHolder {
    @Nullable Pattern getPattern();
    @Nullable AbstractReferable getReferable();
    @Nullable Expression getResultType();
    /* @NotNull */ @Nullable Expression getTerm();
  }

  public interface LevelExpression extends SourceNode {
    @Nullable Object getData();
    <P, R> R accept(AbstractLevelExpressionVisitor<? super P, ? extends R> visitor, @Nullable P params);
  }

  // Definition

  public interface ReferableDefinition extends SourceNode {
    /* @NotNull */ @Nullable Abstract.AbstractLocatedReferable getReferable();
  }

  public enum Comparison { LESS_OR_EQUALS, GREATER_OR_EQUALS }

  public interface LevelParameters {
    @Nullable Object getData();
    @NotNull Collection<? extends AbstractReferable> getReferables();
    @NotNull Collection<Comparison> getComparisonList();
    boolean isIncreasing();
  }

  public interface Definition extends ReferableDefinition {
    @Override @NotNull Abstract.AbstractLocatedReferable getReferable();
    <R> R accept(AbstractDefinitionVisitor<? extends R> visitor);
    @Nullable LevelParameters getPLevelParameters();
    @Nullable LevelParameters getHLevelParameters();
    boolean withUse();
  }

  public interface MetaDefinition extends Definition, ParametersHolder {
    @Nullable Expression getTerm();
    @Override @NotNull List<? extends Parameter> getParameters();
  }

  public interface FunctionDefinition extends Definition, EliminatedExpressionsHolder, ClassReferenceHolder {
    @Nullable Expression getResultType();
    @Nullable Expression getResultTypeLevel();
    @Nullable Expression getTerm();
    @Override @NotNull Collection<? extends Reference> getEliminatedExpressions();
    @NotNull Collection<? extends FunctionClause> getClauses();
    boolean withTerm();
    boolean isCowith();
    FunctionKind getFunctionKind();
    @Nullable Reference getImplementedField();
  }

  public interface DataDefinition extends Definition, EliminatedExpressionsHolder {
    boolean isTruncated();
    @Nullable Expression getUniverse();
    @NotNull Collection<? extends ConstructorClause> getClauses();
  }

  public interface ClassDefinition extends Definition, ParametersHolder, ClassReferenceHolder  {
    @Override @NotNull List<? extends FieldParameter> getParameters();
    @Override @NotNull Collection<? extends ClassFieldImpl> getCoClauseElements();
    boolean isRecord();
    boolean withoutClassifying();
    @NotNull Collection<? extends ReferenceExpression> getSuperClasses();
    @NotNull Collection<? extends ClassElement> getClassElements();
  }

  public interface Constructor extends ReferableDefinition, EliminatedExpressionsHolder {
    @Override @NotNull Abstract.AbstractLocatedReferable getReferable();
    @Override @NotNull Collection<? extends Reference> getEliminatedExpressions();
    @NotNull Collection<? extends FunctionClause> getClauses();
    @Nullable Expression getResultType();
    boolean isCoerce();
  }

  public interface ClassField extends ClassElement, ReferableDefinition, ParametersHolder {
    ClassFieldKind getClassFieldKind();
    /* @NotNull */ @Nullable Expression getResultType();
    @Nullable Expression getResultTypeLevel();
    boolean isClassifying();
    boolean isCoerce();
    boolean isParameterField();
    boolean isExplicitField();
  }

  public interface OverriddenField extends ClassElement, ParametersHolder {
    @Nullable Object getData();
    /* @NotNull */ @Nullable Reference getOverriddenField();
    /* @NotNull */ @Nullable Expression getResultType();
    @Nullable Expression getResultTypeLevel();
  }
}
