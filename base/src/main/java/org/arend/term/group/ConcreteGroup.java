package org.arend.term.group;

import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.naming.reference.*;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public record ConcreteGroup(@NotNull Doc description, @NotNull LocatedReferable referable, @Nullable Concrete.ResolvableDefinition definition, @NotNull List<? extends ConcreteStatement> statements, @NotNull List<? extends ConcreteGroup> dynamicGroups, @NotNull List<? extends ParameterReferable> externalParameters) implements Group {
  @Override
  public @NotNull Doc getDescription() {
    return description;
  }

  @Override
  public @NotNull LocatedReferable getReferable() {
    return referable;
  }

  @Override
  public @NotNull List<? extends Statement> getStatements() {
    return statements;
  }

  @Override
  public @NotNull List<? extends InternalReferable> getInternalReferables() {
    return definition instanceof Concrete.DataDefinition ? getConstructors() : getFields();
  }

  @Override
  public @NotNull List<? extends InternalReferable> getConstructors() {
    if (definition instanceof Concrete.DataDefinition dataDef) {
      List<InternalReferable> result = new ArrayList<>();
      for (Concrete.ConstructorClause clause : dataDef.getConstructorClauses()) {
        for (Concrete.Constructor constructor : clause.getConstructors()) {
          result.add(new SimpleInternalReferable(constructor.getData(), true));
        }
      }
      return result;
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public @NotNull List<? extends InternalReferable> getFields() {
    if (definition instanceof Concrete.ClassDefinition classDef) {
      List<InternalReferable> result = new ArrayList<>();
      for (Concrete.ClassElement element : classDef.getElements()) {
        if (element instanceof Concrete.ClassField field) {
          result.add(new SimpleInternalReferable(field.getData(), !field.getData().isParameterField()));
        }
      }
      return result;
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public @NotNull List<? extends Group> getDynamicSubgroups() {
    return dynamicGroups;
  }

  @Override
  public @NotNull List<? extends ParameterReferable> getExternalParameters() {
    return externalParameters;
  }

  public @Nullable ConcreteGroup getSubgroup(@NotNull GroupPath.Element element) {
    if (element.isDynamic()) {
      return element.index() < dynamicGroups.size() ? dynamicGroups.get(element.index()) : null;
    } else {
      return element.index() < statements.size() ? statements.get(element.index()).group() : null;
    }
  }

  public @Nullable ConcreteGroup getSubgroup(@NotNull GroupPath path) {
    ConcreteGroup subgroup = this;
    for (GroupPath.Element element : path.getList()) {
      subgroup = subgroup.getSubgroup(element);
      if (subgroup == null) return null;
    }
    return subgroup;
  }

  public @Nullable ConcreteStatement getSubStatement(@NotNull GroupPath path) {
    List<GroupPath.Element> list = path.getList();
    if (list.isEmpty() || list.get(list.size() - 1).isDynamic()) return null;

    ConcreteGroup subgroup = this;
    for (int i = 0; i < list.size() - 1; i++) {
      subgroup = subgroup.getSubgroup(list.get(i));
      if (subgroup == null) return null;
    }
    GroupPath.Element element = list.get(list.size() - 1);
    return element.index() < subgroup.statements.size() ? subgroup.statements.get(element.index()) : null;
  }

}
