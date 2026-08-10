package org.arend.frontend.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.core.context.binding.Binding;
import org.arend.core.expr.Expression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.CommandContext;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.ProgressReporter;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.error.local.GoalError;

import java.util.*;

public final class FindGoals {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private FindGoals() {}

  public static boolean run(CommandContext ctx, String[] args) {
    if (args == null || args.length == 0) {
      System.err.println("[ERROR] -fg requires MODULE:DEF argument");
      return false;
    }

    String spec = args[0];
    Pair<ModulePath, LongName> parsed = ctx.parseFullName(spec);
    if (parsed == null) return false;

    ModuleLocation module = ctx.server.findModule(parsed.proj1, null, false, false);
    if (module == null) {
      ctx.systemErrErrorReporter.report(new ModuleNotFoundError(parsed.proj1));
      return false;
    }

    List<GoalError> goalErrors = findGoals(module, parsed.proj2, ctx);

    try {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("definition", spec);

      List<Map<String, Object>> goals = new ArrayList<>();
      int goalIndex = 0;
      for (GoalError ge : goalErrors) {
        Map<String, Object> goalMap = new LinkedHashMap<>();
        goalMap.put("id", String.valueOf(goalIndex++));
        goalMap.put("name", ge.goalName != null ? ge.goalName : "");
        goalMap.put("expectedType", ge.expectedType != null ? ge.expectedType.normalize(NormalizationMode.WHNF).toString() : "");

        List<Map<String, String>> context = new ArrayList<>();
        if (ge.typecheckingContext != null) {
          for (Map.Entry<Referable, Binding> entry : ge.typecheckingContext.localContext().entrySet()) {
            if (entry.getValue().isHidden()) continue;
            String name = entry.getKey() != null ? entry.getKey().getRefName() : "_";
            Expression typeExpr = ge.bindingTypes.get(entry.getValue());
            if (typeExpr == null) typeExpr = entry.getValue().getType();
            String type = typeExpr != null ? typeExpr.toString() : "{?}";
            context.add(Map.of("name", name, "type", type));
          }
        }
        goalMap.put("context", context);
        goals.add(goalMap);
      }
      result.put("goals", goals);

      System.out.println(MAPPER.writeValueAsString(result));
      return true;
    } catch (Exception e) {
      System.err.println("[ERROR] Failed to serialize goals: " + e.getMessage());
      return false;
    }
  }

  public static List<GoalError> findGoals(ModuleLocation module, LongName targetName, CommandContext ctx) {
    // Reset typechecked state so re-typecheck produces fresh GoalErrors.
    // We use getRawGroup() because getResolvedDefinitions() returns empty
    // for modules loaded from binary cache.
    ConcreteGroup group = ctx.server.getRawGroup(module);
    if (group != null) {
      if (targetName != null) {
        resetDefinition(group, targetName);
      } else {
        resetAllDefinitions(group);
      }
    }

    List<GoalError> goalErrors = new ArrayList<>();
    org.arend.ext.error.ErrorReporter goalCapture = error -> {
      if (error instanceof GoalError ge) {
        goalErrors.add(ge);
      }
    };

    // Register with server's ErrorService so we receive GoalErrors
    // (the typecheck() errorReporter param only gets "not found" errors;
    // actual typechecking errors flow through the ErrorService).
    // Removed in finally: the daemon reuses this server across commands, and
    // leaked captures would accumulate without bound.
    ctx.server.addErrorReporter(goalCapture);
    try {
      ctx.server.getCheckerFor(Collections.singletonList(module))
        .resolveAll(ctx.cancellation, ProgressReporter.empty());

      if (targetName != null) {
        ctx.server.getCheckerFor(Collections.singletonList(module))
          .typecheck(Collections.singletonList(new FullName(module, targetName)),
            goalCapture, ctx.cancellation, ProgressReporter.empty());
      } else {
        ctx.server.getCheckerFor(Collections.singletonList(module))
          .typecheck(ctx.cancellation, ProgressReporter.empty());
      }
    } finally {
      ctx.server.removeErrorReporter(goalCapture);
    }

    return goalErrors;
  }

  public static boolean resetDefinition(ConcreteGroup group, LongName targetName) {
    if (group.referable() instanceof TCDefReferable tcRef && tcRef.getKind().isTypecheckable()) {
      if (tcRef.getRefLongName().equals(targetName)) {
        tcRef.setTypechecked(null);
        return true;
      }
    }
    for (ConcreteStatement stmt : group.statements()) {
      if (stmt.group() != null && resetDefinition(stmt.group(), targetName)) return true;
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      if (resetDefinition(dyn, targetName)) return true;
    }
    return false;
  }

  public static void resetAllDefinitions(ConcreteGroup group) {
    if (group.referable() instanceof TCDefReferable tcRef && tcRef.getKind().isTypecheckable()) {
      tcRef.setTypechecked(null);
    }
    for (ConcreteStatement stmt : group.statements()) {
      if (stmt.group() != null) resetAllDefinitions(stmt.group());
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      resetAllDefinitions(dyn);
    }
  }
}
