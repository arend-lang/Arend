package org.arend.server;

import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.resolving.typing.GlobalTypingInfo;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.LexicalScope;
import org.arend.naming.scope.Scope;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.instance.provider.InstanceProvider;
import org.arend.typechecking.provider.ConcreteProvider;

import java.util.List;

class GroupData {
  private final long myTimestamp;
  private final ConcreteGroup myRawGroup;
  private final Scope myFileScope;
  private GlobalTypingInfo myTypingInfo;
  private List<DefinitionData> myResolvedDefinitions;
  private ConcreteProvider myConcreteProvider;

  public record DefinitionData(Concrete.Definition definition, InstanceProvider instanceProvider) {}

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
    return myConcreteProvider != null;
  }

  public Concrete.Definition getConcreteDefinition(GlobalReferable referable) {
    if (myConcreteProvider == null) return null;
    Concrete.GeneralDefinition def = myConcreteProvider.getConcrete(referable);
    return def instanceof Concrete.Definition ? (Concrete.Definition) def : null;
  }

  public List<DefinitionData> getResolvedDefinitions() {
    return myResolvedDefinitions;
  }

  public void setResolvedDefinitions(ConcreteProvider concreteProvider, List<DefinitionData> definitions) {
    myConcreteProvider = concreteProvider;
    myResolvedDefinitions = definitions;
  }

  public void clearResolved() {
    myTypingInfo = null;
    myConcreteProvider = null;
    myResolvedDefinitions = null;
  }
}
