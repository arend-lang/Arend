package org.arend.server;

import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.resolving.typing.GlobalTypingInfo;
import org.arend.naming.scope.Scope;
import org.arend.term.group.ConcreteGroup;

class GroupData {
  private final long myTimestamp;
  private final ConcreteGroup myRawGroup;
  private final Scope myFileScope;
  private GlobalTypingInfo myTypingInfo;

  public GroupData(long timestamp, ConcreteGroup rawGroup, Scope fileScope) {
    myTimestamp = timestamp;
    myRawGroup = rawGroup;
    myFileScope = fileScope;
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
}
