package org.arend.server.impl;

import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.resolving.typing.GlobalTypingInfo;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.LexicalScope;
import org.arend.naming.scope.Scope;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.instance.provider.InstanceProvider;

import java.util.List;

class GroupData {
  private final long myTimestamp;
  private final ConcreteGroup myRawGroup;
  private final Scope myFileScope;
  private GlobalTypingInfo myTypingInfo;
  private List<DefinitionData> myResolvedDefinitions;

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

  public GroupData(long timestamp, ConcreteGroup rawGroup) {
    this(timestamp, rawGroup, null);
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

  public List<DefinitionData> getResolvedDefinitions() {
    return myResolvedDefinitions;
  }

  public void setResolvedDefinitions(List<DefinitionData> definitions) {
    myResolvedDefinitions = definitions;
  }

  public void clearResolved() {
    myTypingInfo = null;
  }
}
