package org.arend.server.impl;

import org.arend.ext.module.LongName;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.typing.GlobalTypingInfo;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.LexicalScope;
import org.arend.naming.scope.Scope;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ConcreteCompareVisitor;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.util.list.PersistentList;

import java.util.*;

public class GroupData {
  private final long myTimestamp;
  private final ConcreteGroup myRawGroup;
  private final Scope myFileScope;
  private GlobalTypingInfo myTypingInfo;
  private Map<LongName, DefinitionData> myResolvedDefinitions;
  private boolean myResolved;

  public record DefinitionData(Concrete.ResolvableDefinition definition, PersistentList<TCDefReferable> instances) {
    public boolean compare(DefinitionData other) {
      return definition.accept(new ConcreteCompareVisitor(), other.definition) && instances.equals(other.instances);
    }
  }

  private GroupData(long timestamp, ConcreteGroup rawGroup, GlobalTypingInfo typingInfo) {
    myTimestamp = timestamp;
    myRawGroup = rawGroup;
    myFileScope = CachingScope.make(LexicalScope.opened(rawGroup));
    myTypingInfo = typingInfo;
  }

  public GroupData(ConcreteGroup rawGroup, GlobalTypingInfo typingInfo) {
    this(-1, rawGroup, typingInfo);
  }

  public GroupData(long timestamp, ConcreteGroup rawGroup, GroupData prevData) {
    this(timestamp, prevData == null || prevData.myResolvedDefinitions == null ? rawGroup : prevData.updateGroup(rawGroup, null), (GlobalTypingInfo) null);
    myResolvedDefinitions = prevData == null ? null : prevData.myResolvedDefinitions;
  }

  private ConcreteGroup updateGroup(ConcreteGroup group, TCDefReferable parent) {
    if (group == null) return null;

    Concrete.ResolvableDefinition newDef = group.definition();
    if (newDef != null) {
      DefinitionData definitionData = myResolvedDefinitions.get(newDef.getData().getRefLongName());
      boolean ok = definitionData != null && newDef.getData().isSimilar(definitionData.definition.getData());
      if (ok) {
        if (newDef instanceof Concrete.DataDefinition dataDef) {
          if (definitionData.definition instanceof Concrete.DataDefinition oldData && dataDef.getConstructorClauses().size() == oldData.getConstructorClauses().size()) {
            List<Concrete.ConstructorClause> clauses = dataDef.getConstructorClauses();
            List<Concrete.ConstructorClause> oldClauses = oldData.getConstructorClauses();
            for (int i = 0; i < clauses.size(); i++) {
              List<Concrete.Constructor> constructors = clauses.get(i).getConstructors();
              List<Concrete.Constructor> oldConstructors = oldClauses.get(i).getConstructors();
              if (constructors.size() != oldConstructors.size()) {
                ok = false;
                break;
              }
              for (int j = 0; j < constructors.size(); j++) {
                Concrete.Constructor constructor = constructors.get(j);
                Concrete.Constructor oldConstructor = oldConstructors.get(j);
                if (constructor.getData().getRefName().equals(oldConstructor.getData().getRefName()) && constructor.getData().isSimilar(oldConstructor.getData())) {
                  TCDefReferable conRef = oldConstructor.getData();
                  conRef.setData(constructor.getData().getData());
                  constructor.setReferable(conRef);
                } else {
                  ok = false;
                }
              }
            }
          } else {
            ok = false;
          }
        } else if (newDef instanceof Concrete.ClassDefinition classDef) {
          if (definitionData.definition instanceof Concrete.ClassDefinition oldClass && classDef.getElements().size() == oldClass.getElements().size()) {
            List<Concrete.ClassElement> elements = classDef.getElements();
            List<Concrete.ClassElement> oldElements = oldClass.getElements();
            for (int i = 0; i < elements.size(); i++) {
              Concrete.ClassElement element = elements.get(i);
              Concrete.ClassElement oldElement = oldElements.get(i);
              if (element.getClass().equals(oldElement.getClass())) {
                if (element instanceof Concrete.ClassField field) {
                  Concrete.ClassField oldField = (Concrete.ClassField) oldElement;
                  if (field.getData().getRefName().equals(oldField.getData().getRefName()) && field.getData().isSimilar(oldField.getData())) {
                    FieldReferableImpl fieldRef = oldField.getData();
                    if (field.getData().equals(classDef.getClassifyingField())) {
                      classDef.setClassifyingField(fieldRef);
                    }
                    fieldRef.setData(field.getData().getData());
                    field.setReferable(fieldRef);
                  } else {
                    ok = false;
                  }
                }
              } else {
                ok = false;
              }
            }
          } else {
            ok = false;
          }
        }
      }

      if (ok) {
        TCDefReferable ref = definitionData.definition.getData();
        ref.setData(newDef.getData().getData());
        newDef.setReferable(ref);
      }
    }

    List<ConcreteStatement> statements = new ArrayList<>(group.getStatements().size());
    for (ConcreteStatement statement : group.statements()) {
      statements.add(new ConcreteStatement(updateGroup(statement.group(), newDef instanceof Concrete.Definition ? newDef.getData() : null), statement.command(), statement.pLevelsDefinition(), statement.hLevelsDefinition()));
    }

    List<ConcreteGroup> dynamicGroups = new ArrayList<>(group.getDynamicSubgroups().size());
    for (ConcreteGroup dynamicGroup : group.dynamicGroups()) {
      dynamicGroups.add(updateGroup(dynamicGroup, null));
    }

    List<ParameterReferable> externalParameters = new ArrayList<>(group.getExternalParameters().size());
    for (ParameterReferable parameter : group.externalParameters()) {
      externalParameters.add(new ParameterReferable(parent, parameter.getIndex(), parameter.getUnderlyingReferable(), parameter.getAbstractBody()));
    }

    return new ConcreteGroup(group.description(), newDef == null ? group.referable() : newDef.getData(), newDef, statements, dynamicGroups, externalParameters);
  }

  public long getTimestamp() {
    return myTimestamp;
  }

  public boolean isReadOnly() {
    return myTimestamp < 0;
  }

  public LocatedReferable getFileReferable() {
    return myRawGroup.referable();
  }

  public Scope getFileScope() {
    return myFileScope;
  }

  public ConcreteGroup getRawGroup() {
    return myRawGroup;
  }

  public GlobalTypingInfo getTypingInfo() {
    return myTypingInfo;
  }

  public void setTypingInfo(GlobalTypingInfo typingInfo) {
    myTypingInfo = typingInfo;
  }

  public boolean isResolved() {
    return myResolved;
  }

  public Collection<DefinitionData> getResolvedDefinitions() {
    return myResolved ? myResolvedDefinitions.values() : null;
  }

  public Map<LongName, DefinitionData> getPreviousDefinitions() {
    return myResolved ? null : myResolvedDefinitions;
  }

  public DefinitionData getDefinitionData(LongName name) {
    return myResolvedDefinitions.get(name);
  }

  public void updateResolvedDefinitions(Map<LongName, DefinitionData> definitions) {
    myResolvedDefinitions = definitions;
    myResolved = true;
  }

  public void clearResolved() {
    myResolved = false;
    myTypingInfo = null;
  }
}
