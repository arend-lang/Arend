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

  /**
   * Replaces referables of {@code group} with their versions from the previous pass wherever possible.
   * Every top-level subtree is either reused entirely or kept as it was parsed:
   * a reused referable is a child of a referable of the previous pass and a new one is a child of a new one,
   * so mixing them would leave referables whose parents are not in the group.
   */
  private ConcreteGroup updateGroup(ConcreteGroup group) {
    Map<ConcreteGroup, LocatedReferable> reusable = new IdentityHashMap<>();
    Map<TCDefReferable, TCDefReferable> replaced = new HashMap<>();
    List<ConcreteStatement> statements = new ArrayList<>(group.statements().size());
    for (ConcreteStatement statement : group.statements()) {
      ConcreteGroup subgroup = statement.group();
      statements.add(subgroup != null && findReusable(subgroup, reusable) != null ? new ConcreteStatement(reuseGroup(subgroup, null, reusable, replaced), statement.command()) : statement);
    }
    ConcreteGroup result = new ConcreteGroup(group.description(), group.referable(), group.definition(), statements, group.dynamicGroups(), group.externalParameters());

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

  /**
   * Checks if {@code group} and all of its subgroups can be replaced with their versions from the previous pass.
   *
   * @return the previous version of the referable of {@code group} or null if the subtree cannot be reused.
   */
  private LocatedReferable findReusable(ConcreteGroup group, Map<ConcreteGroup, LocatedReferable> reusable) {
    LocatedReferable oldRef = null;
    Concrete.ResolvableDefinition newDef = group.definition();
    if (newDef != null) {
      DefinitionData definitionData = myResolvedDefinitions.get(newDef.getData().getRefLongName());
      if (definitionData == null || !isSimilar(newDef, definitionData.definition())) return null;
      oldRef = definitionData.definition().getData();
    }

    List<ConcreteGroup> subgroups = new ArrayList<>(group.dynamicGroups());
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) subgroups.add(statement.group());
    }

    for (ConcreteGroup subgroup : subgroups) {
      LocatedReferable oldSubref = findReusable(subgroup, reusable);
      if (oldSubref == null) return null;
      LocatedReferable oldParent = oldSubref.getLocatedReferableParent();
      if (oldRef == null) {
        // A \module has no definition, so the previous version of its referable is found as the parent of the previous versions of its members
        LocatedReferable newRef = group.referable();
        if (oldParent == null || oldParent.getClass() != newRef.getClass() || oldParent.getKind() != newRef.getKind() || !oldParent.getRefName().equals(newRef.getRefName())) return null;
        oldRef = oldParent;
      } else if (oldParent != oldRef) {
        return null;
      }
    }

    if (oldRef != null) reusable.put(group, oldRef);
    return oldRef;
  }

  private static boolean isSimilar(Concrete.ResolvableDefinition newDef, Concrete.ResolvableDefinition oldDef) {
    if (!newDef.getData().isSimilar(oldDef.getData())) return false;

    if (newDef instanceof Concrete.DataDefinition dataDef) {
      if (!(oldDef instanceof Concrete.DataDefinition oldData) || dataDef.getConstructorClauses().size() != oldData.getConstructorClauses().size()) return false;
      List<Concrete.ConstructorClause> clauses = dataDef.getConstructorClauses();
      List<Concrete.ConstructorClause> oldClauses = oldData.getConstructorClauses();
      for (int i = 0; i < clauses.size(); i++) {
        List<Concrete.Constructor> constructors = clauses.get(i).getConstructors();
        List<Concrete.Constructor> oldConstructors = oldClauses.get(i).getConstructors();
        if (constructors.size() != oldConstructors.size()) return false;
        for (int j = 0; j < constructors.size(); j++) {
          Concrete.Constructor constructor = constructors.get(j);
          Concrete.Constructor oldConstructor = oldConstructors.get(j);
          if (!constructor.getData().getRefName().equals(oldConstructor.getData().getRefName()) || !constructor.getData().isSimilar(oldConstructor.getData())) return false;
        }
      }
    } else if (newDef instanceof Concrete.ClassDefinition classDef) {
      if (!(oldDef instanceof Concrete.ClassDefinition oldClass) || classDef.getElements().size() != oldClass.getElements().size()) return false;
      List<Concrete.ClassElement> elements = classDef.getElements();
      List<Concrete.ClassElement> oldElements = oldClass.getElements();
      for (int i = 0; i < elements.size(); i++) {
        Concrete.ClassElement element = elements.get(i);
        Concrete.ClassElement oldElement = oldElements.get(i);
        if (!element.getClass().equals(oldElement.getClass())) return false;
        if (element instanceof Concrete.ClassField field) {
          FieldReferableImpl oldField = ((Concrete.ClassField) oldElement).getData();
          if (!field.getData().getRefName().equals(oldField.getRefName()) || !field.getData().isSimilar(oldField)) return false;
        }
      }
    }

    return true;
  }

  /**
   * Replaces the referables of a subtree accepted by {@link #findReusable} with their previous versions.
   */
  private ConcreteGroup reuseGroup(ConcreteGroup group, TCDefReferable parent, Map<ConcreteGroup, LocatedReferable> reusable, Map<TCDefReferable, TCDefReferable> replaced) {
    LocatedReferable oldRef = reusable.get(group);
    Concrete.ResolvableDefinition newDef = group.definition();
    if (newDef != null) {
      Concrete.ResolvableDefinition oldDef = myResolvedDefinitions.get(newDef.getData().getRefLongName()).definition();
      if (newDef instanceof Concrete.DataDefinition dataDef) {
        List<Concrete.ConstructorClause> clauses = dataDef.getConstructorClauses();
        List<Concrete.ConstructorClause> oldClauses = ((Concrete.DataDefinition) oldDef).getConstructorClauses();
        for (int i = 0; i < clauses.size(); i++) {
          List<Concrete.Constructor> constructors = clauses.get(i).getConstructors();
          List<Concrete.Constructor> oldConstructors = oldClauses.get(i).getConstructors();
          for (int j = 0; j < constructors.size(); j++) {
            Concrete.Constructor constructor = constructors.get(j);
            InternalReferable conRef = oldConstructors.get(j).getData();
            conRef.setData(constructor.getData().getData());
            constructor.setReferable(conRef);
          }
        }
      } else if (newDef instanceof Concrete.ClassDefinition classDef) {
        List<Concrete.ClassElement> elements = classDef.getElements();
        List<Concrete.ClassElement> oldElements = ((Concrete.ClassDefinition) oldDef).getElements();
        for (int i = 0; i < elements.size(); i++) {
          if (elements.get(i) instanceof Concrete.ClassField field) {
            FieldReferableImpl fieldRef = ((Concrete.ClassField) oldElements.get(i)).getData();
            if (field.getData().equals(classDef.getClassifyingField())) {
              classDef.setClassifyingField(fieldRef);
            }
            fieldRef.setData(field.getData().getData());
            field.setReferable(fieldRef);
          }
        }
      }

      TCDefReferable ref = (TCDefReferable) oldRef;
      replaced.put(newDef.getData(), ref);
      ref.setData(newDef.getData().getData());
      newDef.setReferable(ref);
    } else if (oldRef instanceof TCDefReferable ref && group.referable() instanceof TCDefReferable newRef) {
      ref.setData(newRef.getData());
    }

    List<ConcreteStatement> statements = new ArrayList<>(group.statements().size());
    for (ConcreteStatement statement : group.statements()) {
      statements.add(new ConcreteStatement(statement.group() == null ? null : reuseGroup(statement.group(), newDef instanceof Concrete.Definition ? newDef.getData() : null, reusable, replaced), statement.command()));
    }

    List<ConcreteGroup> dynamicGroups = new ArrayList<>(group.dynamicGroups().size());
    for (ConcreteGroup dynamicGroup : group.dynamicGroups()) {
      dynamicGroups.add(reuseGroup(dynamicGroup, null, reusable, replaced));
    }

    List<ParameterReferable> externalParameters = new ArrayList<>(group.externalParameters().size());
    for (ParameterReferable parameter : group.externalParameters()) {
      externalParameters.add(new ParameterReferable(parent, parameter.getIndex(), parameter.getOriginalReferable(), parameter.getAbstractBody()));
    }

    return new ConcreteGroup(group.description(), oldRef, newDef, statements, dynamicGroups, externalParameters);
  }

  private static void replaceCoClauseFunctionReference(Concrete.CoClauseFunctionReference coclauseRef, Map<TCDefReferable, TCDefReferable> coclauseMap) {
    TCDefReferable newRef = coclauseMap.get(coclauseRef.functionReference);
    if (newRef != null) coclauseRef.functionReference = newRef;
    if (coclauseRef.implementation instanceof Concrete.ReferenceExpression refExpr && refExpr.getReferent() instanceof TCDefReferable defRef) {
      TCDefReferable newDefRef = coclauseMap.get(defRef);
      if (newRef != null) coclauseRef.implementation = new Concrete.ReferenceExpression(refExpr.getData(), newDefRef, refExpr.getLevels());
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
  }
}
