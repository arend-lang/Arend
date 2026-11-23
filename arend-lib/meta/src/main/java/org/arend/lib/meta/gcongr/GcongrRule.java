package org.arend.lib.meta.gcongr;

import org.arend.ext.core.context.CoreBinding;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.definition.*;
import org.arend.ext.core.expr.*;
import org.arend.lib.meta.positivity.PositivityMeta;
import org.arend.lib.meta.positivity.Relation;
import org.arend.lib.meta.positivity.Strictness;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A monotonicity lemma used by {@code gcongr}.
 * Its conclusion is {@code f x₁ … xₙ R f y₁ … yₙ}, where {@code R} is {@code <} or {@code <=}.
 * A position {@code i} is <i>varying</i> if {@code xᵢ} and {@code yᵢ} are distinct parameters.
 * A <i>main</i> premise relates {@code xᵢ} and {@code yᵢ} for some varying position {@code i} (possibly under binders, possibly in reverse order);
 * it is proved recursively by {@code gcongr}.
 * Other explicit premises are <i>side</i> conditions; they are proved by {@code positivity} or by hypotheses.
 * Parameters that occur in the conclusion are solved by unification.
 *
 * @param head          the head symbol of both sides of the conclusion.
 * @param instanceClass if the first parameter is an instance of a class (e.g., {@code \this} parameter of a dynamic lemma), this class.
 * @param parameters    the remaining parameters of the lemma.
 * @param mainCount     the number of main premises; lemmas with fewer main premises are more specific.
 */
public record GcongrRule(CoreDefinition definition, Strictness strictness, Object head, @Nullable CoreClassDefinition instanceClass, List<Parameter> parameters, int mainCount) {
  public enum Kind { UNIFY, MAIN, SIDE }

  public record Parameter(boolean explicit, Kind kind) {}

  public static @Nullable GcongrRule make(CoreDefinition definition, PositivityMeta meta) {
    CoreExpression type;
    CoreClassDefinition instanceClass = null;
    List<CoreParameter> parameters = new ArrayList<>();
    if (definition instanceof CoreClassField field) {
      type = field.getResultType();
      instanceClass = field.getParentClass();
    } else if (definition instanceof CoreFunctionDefinition function) {
      type = function.getResultType();
      CoreParameter param = function.getParameters();
      if (param.hasNext() && !param.isExplicit() && "this".equals(param.getBinding().getName()) && param.getType() instanceof CoreClassCallExpression classCall) {
        instanceClass = classCall.getDefinition();
        param = param.getNext();
      }
      addParameters(param, parameters);
    } else {
      return null;
    }
    if (type == null) return null;

    while (Relation.parseSyntactically(type, meta, null) == null && type instanceof CorePiExpression piType) {
      addParameters(piType.getParameters(), parameters);
      type = piType.getCodomain();
    }
    Relation relation = Relation.parseSyntactically(type, meta, null);
    if (relation == null || relation.kind() == Strictness.NONZERO) return null;

    Spine left = Spine.make(relation.left()), right = Spine.make(relation.right());
    if (left == null || right == null || !left.head().equals(right.head()) || left.args().size() != right.args().size()) return null;

    Set<CoreBinding> bindings = new HashSet<>();
    for (CoreParameter parameter : parameters) {
      bindings.add(parameter.getBinding());
    }
    List<CoreBinding[]> varying = new ArrayList<>();
    for (int i = 0; i < left.args().size(); i++) {
      if (Relation.deref(left.args().get(i)) instanceof CoreReferenceExpression ref1 && Relation.deref(right.args().get(i)) instanceof CoreReferenceExpression ref2 &&
          ref1.getBinding() != ref2.getBinding() && bindings.contains(ref1.getBinding()) && bindings.contains(ref2.getBinding())) {
        varying.add(new CoreBinding[] { ref1.getBinding(), ref2.getBinding() });
      }
    }

    Set<CoreBinding> conclusionBindings = freeBindings(type, bindings);
    List<Parameter> result = new ArrayList<>(parameters.size());
    int mainCount = 0;
    for (CoreParameter parameter : parameters) {
      Kind kind;
      if (!parameter.isExplicit() || conclusionBindings.contains(parameter.getBinding())) {
        kind = Kind.UNIFY;
      } else {
        kind = isMain(parameter.getType(), varying, bindings, meta) ? Kind.MAIN : Kind.SIDE;
        if (kind == Kind.MAIN) mainCount++;
      }
      result.add(new Parameter(parameter.isExplicit(), kind));
    }
    return mainCount == 0 ? null : new GcongrRule(definition, relation.kind(), left.head(), instanceClass, result, mainCount);
  }

  private static void addParameters(CoreParameter param, List<CoreParameter> parameters) {
    for (; param.hasNext(); param = param.getNext()) {
      parameters.add(param);
    }
  }

  private static boolean isMain(CoreExpression type, List<CoreBinding[]> varying, Set<CoreBinding> bindings, PositivityMeta meta) {
    type = Relation.deref(type);
    while (Relation.parseSyntactically(type, meta, null) == null && type instanceof CorePiExpression piType) {
      type = Relation.deref(piType.getCodomain());
    }
    Relation relation = Relation.parseSyntactically(type, meta, null);
    if (relation == null || relation.kind() == Strictness.NONZERO) return false;
    Set<CoreBinding> leftBindings = freeBindings(relation.left(), bindings);
    Set<CoreBinding> rightBindings = freeBindings(relation.right(), bindings);
    for (CoreBinding[] pair : varying) {
      if (leftBindings.contains(pair[0]) && rightBindings.contains(pair[1]) || leftBindings.contains(pair[1]) && rightBindings.contains(pair[0])) {
        return true;
      }
    }
    return false;
  }

  private static Set<CoreBinding> freeBindings(CoreExpression expr, Set<CoreBinding> bindings) {
    Set<CoreBinding> result = new HashSet<>();
    expr.processSubexpression(e -> {
      if (e instanceof CoreReferenceExpression ref && bindings.contains(ref.getBinding())) {
        result.add(ref.getBinding());
      }
      return CoreExpression.FindAction.CONTINUE;
    });
    return result;
  }

  /**
   * An application {@code head args}, where {@code args} include parameters of the head definition and arguments of applications.
   */
  public record Spine(Object head, List<CoreExpression> args) {
    public static @Nullable Spine make(CoreExpression expr) {
      List<CoreExpression> args = new ArrayList<>();
      while (true) {
        expr = Relation.deref(expr);
        if (expr instanceof CoreAppExpression app) {
          args.add(app.getArgument());
          expr = app.getFunction();
        } else {
          break;
        }
      }
      Collections.reverse(args);
      List<CoreExpression> result = new ArrayList<>();
      if (expr instanceof CoreFieldCallExpression fieldCall) {
        result.add(fieldCall.getArgument());
      } else if (expr instanceof CoreDefCallExpression defCall) {
        if (defCall instanceof CoreConCallExpression conCall) {
          result.addAll(conCall.getDataTypeArguments());
        }
        result.addAll(defCall.getDefCallArguments());
      } else {
        return null;
      }
      result.addAll(args);
      return new Spine(((CoreDefCallExpression) expr).getDefinition(), result);
    }
  }
}