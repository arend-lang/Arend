package org.arend.term.group;

import org.arend.core.definition.ClassDefinition;
import org.arend.core.definition.ClassField;
import org.arend.core.definition.Constructor;
import org.arend.core.definition.DataDefinition;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.naming.reference.*;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public record ConcreteGroup(@NotNull Doc description, @NotNull LocatedReferable referable, @Nullable Concrete.ResolvableDefinition definition, @NotNull List<ConcreteStatement> statements, @NotNull List<? extends ConcreteGroup> dynamicGroups, @NotNull List<? extends ParameterReferable> externalParameters) {
  public boolean isTopLevel() {
    return referable.getLocatedReferableParent() == null;
  }

  public @NotNull List<? extends InternalReferable> getInternalReferables() {
    // For source groups, `definition` distinguishes data vs. class. For deserialized
    // groups `definition == null`, so also consult the typechecked `Definition` on
    // the referable — otherwise a deserialized `\data Foo` would fall through to
    // `getFields()` (which has no data-type path) and hide all constructors from
    // scope resolution.
    if (definition instanceof Concrete.DataDefinition) return getConstructors();
    if (definition == null
        && referable instanceof TCDefReferable tcRef
        && tcRef.getTypechecked() instanceof DataDefinition) {
      return getConstructors();
    }
    return getFields();
  }

  public @NotNull List<? extends InternalReferable> getConstructors() {
    if (definition instanceof Concrete.DataDefinition dataDef) {
      List<InternalReferable> result = new ArrayList<>();
      for (Concrete.ConstructorClause clause : dataDef.getConstructorClauses()) {
        for (Concrete.Constructor constructor : clause.getConstructors()) {
          result.add(constructor.getData());
        }
      }
      return result;
    }
    // Fallback for deserialized groups where `definition` is null: scope resolution
    // still needs to see the data type's constructors, so look them up via the
    // typechecked DataDefinition whose constructor referables were registered during
    // readDefinition.
    if (referable instanceof TCDefReferable tcRef
        && tcRef.getTypechecked() instanceof DataDefinition dd) {
      List<InternalReferable> result = new ArrayList<>(dd.getConstructors().size());
      for (Constructor c : dd.getConstructors()) {
        if (c.getReferable() instanceof InternalReferable ir) result.add(ir);
      }
      return result;
    }
    return Collections.emptyList();
  }

  public @NotNull List<? extends InternalReferable> getFields() {
    if (definition instanceof Concrete.ClassDefinition classDef) {
      List<InternalReferable> result = new ArrayList<>();
      for (Concrete.ClassElement element : classDef.getElements()) {
        if (element instanceof Concrete.ClassField field) {
          result.add(field.getData());
        }
      }
      return result;
    }
    // Fallback for deserialized groups where `definition` is null: scope resolution
    // must still see this class's personal fields, otherwise downstream fresh
    // typechecking cannot resolve field names (e.g. BaseSet.E) and classes that
    // extend the deserialized class end up with null classifying fields, which
    // triggers an instance-resolution cycle for subclasses without a classifying
    // field (see GlobalInstancePool.getInstancePair line 182-184).
    if (referable instanceof TCDefReferable tcRef
        && tcRef.getTypechecked() instanceof ClassDefinition cd) {
      List<InternalReferable> result = new ArrayList<>(cd.getPersonalFields().size());
      for (ClassField f : cd.getPersonalFields()) {
        if (f.getReferable() instanceof InternalReferable ir) result.add(ir);
      }
      return result;
    }
    return Collections.emptyList();
  }

  public void traverseGroup(Consumer<ConcreteGroup> consumer) {
    consumer.accept(this);
    for (ConcreteStatement statement : statements) {
      ConcreteGroup subgroup = statement.group();
      if (subgroup != null) {
        subgroup.traverseGroup(consumer);
      }
    }
    for (ConcreteGroup subgroup : dynamicGroups) {
      subgroup.traverseGroup(consumer);
    }
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
    if (list.isEmpty() || list.getLast().isDynamic()) return null;

    ConcreteGroup subgroup = this;
    for (int i = 0; i < list.size() - 1; i++) {
      subgroup = subgroup.getSubgroup(list.get(i));
      if (subgroup == null) return null;
    }
    GroupPath.Element element = list.getLast();
    return element.index() < subgroup.statements.size() ? subgroup.statements.get(element.index()) : null;
  }

}
