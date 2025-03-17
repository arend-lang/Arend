package org.arend.server.impl;

import org.arend.ext.module.LongName;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.typing.GlobalTypingInfo;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.LexicalScope;
import org.arend.naming.scope.Scope;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;

import java.util.*;

public class GroupData {
  private final long myTimestamp;
  private final ConcreteGroup myRawGroup;
  private final Scope myFileScope;
  private GlobalTypingInfo myTypingInfo;
  private Map<LongName, DefinitionData> myResolvedDefinitions;
  private boolean myResolved;

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
    this(timestamp, prevData == null || prevData.myResolvedDefinitions == null ? rawGroup : prevData.updateGroup(rawGroup), (GlobalTypingInfo) null);
    myResolvedDefinitions = prevData == null ? null : prevData.myResolvedDefinitions;
  }

  private ConcreteGroup updateGroup(ConcreteGroup group) {
    Map<TCDefReferable, TCDefReferable> replaced = new HashMap<>();
    ConcreteGroup result = updateGroup(group, null, replaced);

    if (!replaced.isEmpty()) result.traverseGroup(subgroup -> {
      if (subgroup.definition() instanceof Concrete.Definition def) {
        if (def.getUseParent() != null) {
          TCDefReferable useParent = replaced.get(def.getUseParent());
          if (useParent != null) def.setUseParent(useParent);
        }

        if (def.enclosingClass != null) {
          TCDefReferable enclosingClass = replaced.get(def.enclosingClass);
          if (enclosingClass != null) def.enclosingClass = enclosingClass;
        }

        List<TCDefReferable> usedDefinitions = def.getUsedDefinitions();
        for (int i = 0; i < usedDefinitions.size(); i++) {
          TCDefReferable usedDef = replaced.get(usedDefinitions.get(i));
          if (usedDef != null) usedDefinitions.set(i, usedDef);
        }

        if (def instanceof Concrete.ClassDefinition classDef) {
          for (Concrete.ClassElement element : classDef.getElements()) {
            if (element instanceof Concrete.CoClauseFunctionReference coclauseRef) {
              replaceCoClauseFunctionReference(coclauseRef, replaced);
            }
          }
        } else if (def instanceof Concrete.FunctionDefinition function && function.getBody() instanceof Concrete.CoelimFunctionBody body) {
          for (Concrete.CoClauseElement element : body.getCoClauseElements()) {
            if (element instanceof Concrete.CoClauseFunctionReference coclauseRef) {
              replaceCoClauseFunctionReference(coclauseRef, replaced);
            }
          }
        }
      }
    });

    return result;
  }

  private ConcreteGroup updateGroup(ConcreteGroup group, TCDefReferable parent, Map<TCDefReferable, TCDefReferable> replaced) {
    if (group == null) return null;

    Concrete.ResolvableDefinition newDef = group.definition();
    if (newDef != null) {
      DefinitionData definitionData = myResolvedDefinitions.get(newDef.getData().getRefLongName());
      boolean ok = definitionData != null && newDef.getData().isSimilar(definitionData.definition().getData());
      if (ok) {
        if (newDef instanceof Concrete.DataDefinition dataDef) {
          if (definitionData.definition() instanceof Concrete.DataDefinition oldData && dataDef.getConstructorClauses().size() == oldData.getConstructorClauses().size()) {
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
                  InternalReferable conRef = oldConstructor.getData();
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
          if (definitionData.definition() instanceof Concrete.ClassDefinition oldClass && classDef.getElements().size() == oldClass.getElements().size()) {
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
        TCDefReferable ref = definitionData.definition().getData();
        replaced.put(newDef.getData(), ref);
        ref.setData(newDef.getData().getData());
        newDef.setReferable(ref);
      }
    }

    List<ConcreteStatement> statements = new ArrayList<>(group.statements().size());
    for (ConcreteStatement statement : group.statements()) {
      statements.add(new ConcreteStatement(updateGroup(statement.group(), newDef instanceof Concrete.Definition ? newDef.getData() : null, replaced), statement.command(), statement.pLevelsDefinition(), statement.hLevelsDefinition()));
    }

    List<ConcreteGroup> dynamicGroups = new ArrayList<>(group.dynamicGroups().size());
    for (ConcreteGroup dynamicGroup : group.dynamicGroups()) {
      dynamicGroups.add(updateGroup(dynamicGroup, null, replaced));
    }

    List<ParameterReferable> externalParameters = new ArrayList<>(group.externalParameters().size());
    for (ParameterReferable parameter : group.externalParameters()) {
      externalParameters.add(new ParameterReferable(parent, parameter.getIndex(), parameter.getOriginalReferable(), parameter.getAnyBody()));
    }

    return new ConcreteGroup(group.description(), newDef == null ? group.referable() : newDef.getData(), newDef, statements, dynamicGroups, externalParameters);
  }

  private static void replaceCoClauseFunctionReference(Concrete.CoClauseFunctionReference coclauseRef, Map<TCDefReferable, TCDefReferable> coclauseMap) {
    TCDefReferable newRef = coclauseMap.get(coclauseRef.functionReference);
    if (newRef != null) coclauseRef.functionReference = newRef;
    if (coclauseRef.implementation instanceof Concrete.ReferenceExpression refExpr && refExpr.getReferent() instanceof TCDefReferable defRef) {
      TCDefReferable newDefRef = coclauseMap.get(defRef);
      if (newRef != null) coclauseRef.implementation = new Concrete.ReferenceExpression(refExpr.getData(), newDefRef, refExpr.getPLevels(), refExpr.getHLevels());
    }
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
    return myResolved ? myResolvedDefinitions.get(name) : null;
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
