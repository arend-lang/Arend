package org.arend.server.impl;

import org.arend.ext.module.LongName;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.ParameterReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.resolving.typing.GlobalTypingInfo;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.LexicalScope;
import org.arend.naming.scope.Scope;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.instance.provider.InstanceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class GroupData {
  private final long myTimestamp;
  private final ConcreteGroup myRawGroup;
  private final Scope myFileScope;
  private GlobalTypingInfo myTypingInfo;
  private Map<LongName, DefinitionData> myResolvedDefinitions;

  public record DefinitionData(Concrete.ResolvableDefinition definition, InstanceProvider instanceProvider) {}

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
      if (definitionData != null && newDef.getData().isSimilar(definitionData.definition.getData())) {
        TCDefReferable ref = definitionData.definition.getData();
        ref.setData(newDef.getData().getData());
        newDef = newDef.copy(ref);
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
    return myTypingInfo != null && myResolvedDefinitions != null;
  }

  public Collection<DefinitionData> getResolvedDefinitions() {
    return myResolvedDefinitions == null ? null : myResolvedDefinitions.values();
  }

  public void updateResolvedDefinitions(Map<LongName, DefinitionData> definitions) {
    myResolvedDefinitions = definitions;
  }

  public void clearResolved() {
    myTypingInfo = null;
  }
}
