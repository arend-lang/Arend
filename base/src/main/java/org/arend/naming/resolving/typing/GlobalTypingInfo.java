package org.arend.naming.resolving.typing;

import org.arend.naming.reference.ClassReferable;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.arend.typechecking.dfs.DFS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class GlobalTypingInfo implements TypingInfo {
  private final TypingInfo myParent;
  private final Map<Referable, ReferableInfo> myBodyInfo;
  private final Map<Referable, ReferableInfo> myTypeInfo;

  private GlobalTypingInfo(TypingInfo parent, Map<Referable, ReferableInfo> bodyInfo, Map<Referable, ReferableInfo> typeInfo) {
    myParent = parent;
    myBodyInfo = bodyInfo;
    myTypeInfo = typeInfo;
  }

  @Override
  public @Nullable ReferableInfo getBodyInfo(Referable referable) {
    ReferableInfo info = myBodyInfo.get(referable);
    return info != null ? info : myParent != null ? myParent.getBodyInfo(referable) : null;
  }

  @Override
  public @Nullable ReferableInfo getTypeInfo(Referable referable) {
    ReferableInfo info = myTypeInfo.get(referable);
    return info != null ? info : myParent != null ? myParent.getTypeInfo(referable) : null;
  }

  public static class Builder {
    private record FinalInfo(Referable referable, int arguments) {}
    private record MyInfo(int parameters, Referable referable, int arguments) {}

    private final Map<Referable, MyInfo> myBodyMap = new HashMap<>();
    private final Map<Referable, MyInfo> myTypeMap = new HashMap<>();

    private MyInfo makeInfo(int parameters, Referable referable, int arguments) {
      return new MyInfo(parameters, referable, referable instanceof GlobalReferable global && global.getKind() == GlobalReferable.Kind.CLASS ? 0 : arguments);
    }

    public void addReferableType(Referable referable, int parameters, Referable typeReferable, int arguments) {
      myTypeMap.put(referable, makeInfo(parameters, typeReferable, arguments));
    }

    public void addReferableBody(Referable referable, int parameters, Referable bodyReferable, int arguments) {
      myBodyMap.put(referable, makeInfo(parameters, bodyReferable, arguments));
    }

    public @NotNull GlobalTypingInfo build(@Nullable TypingInfo parent) {
      Map<Referable, FinalInfo> result = new HashMap<>();
      new DFS<Referable, FinalInfo>() {
        @Override
        protected FinalInfo forDependencies(Referable referable) {
          MyInfo info = myBodyMap.get(referable);
          FinalInfo resultInfo;
          if (info == null) {
            resultInfo = new FinalInfo(referable, 0);
          } else {
            FinalInfo bodyResult = visit(info.referable);
            if (bodyResult == null || bodyResult.arguments < info.arguments) return null;
            resultInfo = new FinalInfo(bodyResult.referable, bodyResult.arguments - info.arguments + info.parameters);
          }
          result.put(referable, resultInfo);
          return resultInfo;
        }

        @Override
        protected FinalInfo getVisitedValue(Referable unit, boolean cycle) {
          return result.get(unit);
        }
      }.visit(myBodyMap.keySet());

      Map<Referable, ReferableInfo> bodyInfo = new HashMap<>();
      for (Map.Entry<Referable, FinalInfo> entry : result.entrySet()) {
        if (entry.getValue().referable instanceof ClassReferable classRef) {
          bodyInfo.put(entry.getKey(), new ReferableInfo(entry.getValue().arguments, classRef));
        }
      }

      Map<Referable, ReferableInfo> typeInfo = new HashMap<>();
      for (Map.Entry<Referable, MyInfo> entry : myTypeMap.entrySet()) {
        if (entry.getValue().referable instanceof ClassReferable classRef) {
          typeInfo.put(entry.getKey(), new ReferableInfo(entry.getValue().parameters, classRef));
        } else {
          ReferableInfo bodyRefInfo = bodyInfo.get(entry.getValue().referable);
          if (bodyRefInfo != null && entry.getValue().arguments == bodyRefInfo.getParameters()) {
            typeInfo.put(entry.getKey(), new ReferableInfo(entry.getValue().parameters, bodyRefInfo.getClassReferable()));
          }
        }
      }

      return new GlobalTypingInfo(parent, bodyInfo, typeInfo);
    }
  }
}
