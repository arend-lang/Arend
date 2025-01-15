package org.arend.naming.reference;

import org.arend.ext.reference.Precedence;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.AccessModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ConcreteResolvedClassReferable extends ConcreteLocatedReferable implements ClassReferable {
  protected List<ClassReferable> superClasses;
  protected List<ConcreteClassFieldReferable> fields;
  protected List<GlobalReferable> dynamicReferables = Collections.emptyList();

  public ConcreteResolvedClassReferable(Object data, AccessModifier accessModifier, @NotNull String name, Precedence precedence, @Nullable String aliasName, Precedence aliasPrecedence, LocatedReferable parent, List<ConcreteClassFieldReferable> fields) {
    super(data, accessModifier, name, precedence, aliasName, aliasPrecedence, parent, Kind.CLASS);
    superClasses = new ArrayList<>();
    this.fields = fields;
  }

  @Override
  public Concrete.ClassDefinition getDefinition() {
    return (Concrete.ClassDefinition) super.getDefinition();
  }

  @Override
  public boolean isRecord() {
    return getDefinition().isRecord();
  }

  @Override
  public @NotNull List<? extends ClassReferable> getSuperClassReferences() {
    return superClasses;
  }

  public void setSuperClasses(List<ClassReferable> superClasses) {
    this.superClasses = superClasses;
  }

  @Override
  public @NotNull Collection<? extends FieldReferable> getFieldReferables() {
    return fields;
  }

  public void setFields(List<ConcreteClassFieldReferable> fields) {
    this.fields = fields;
  }

  @NotNull
  @Override
  public Collection<? extends Referable> getImplementedFields() {
    List<Referable> result = new ArrayList<>();
    for (Concrete.ClassElement element : getDefinition().getElements()) {
      if (element instanceof Concrete.ClassFieldImpl) {
        result.add(((Concrete.ClassFieldImpl) element).getImplementedField());
      }
    }
    return result;
  }

  @Override
  public @NotNull Collection<? extends GlobalReferable> getDynamicReferables() {
    return dynamicReferables;
  }

  public void addDynamic(Concrete.Definition def) {
    if (def.enclosingClass != null) {
      throw new IllegalStateException();
    }
    if (dynamicReferables.isEmpty()) {
      dynamicReferables = new ArrayList<>();
    }
    dynamicReferables.add(def.getData());
    def.enclosingClass = this;
  }
}
