package org.arend.frontend.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.core.context.binding.Binding;
import org.arend.core.expr.Expression;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.CommandContext;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.Referable;
import org.arend.server.ProgressReporter;
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

    List<GoalError> goalErrors = new ArrayList<>();
    org.arend.ext.error.ErrorReporter goalCapture = error -> {
      if (error instanceof GoalError ge) {
        goalErrors.add(ge);
      }
      ctx.errorReporter.report(error);
    };

    ctx.server.getCheckerFor(Collections.singletonList(module))
        .resolveAll(ctx.cancellation, ProgressReporter.empty());

    if (parsed.proj2 != null) {
      ctx.server.getCheckerFor(Collections.singletonList(module))
          .typecheck(Collections.singletonList(new FullName(module, parsed.proj2)),
              goalCapture, ctx.cancellation, ProgressReporter.empty());
    } else {
      ctx.server.getCheckerFor(Collections.singletonList(module))
          .typecheck(ctx.cancellation, ProgressReporter.empty());
    }

    try {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("definition", spec);

      List<Map<String, Object>> goals = new ArrayList<>();
      int goalIndex = 0;
      for (GoalError ge : goalErrors) {
        Map<String, Object> goalMap = new LinkedHashMap<>();
        goalMap.put("id", String.valueOf(goalIndex++));
        goalMap.put("name", ge.goalName != null ? ge.goalName : "");
        goalMap.put("expectedType", ge.expectedType != null ? ge.expectedType.toString() : "");

        List<Map<String, String>> context = new ArrayList<>();
        if (ge.typecheckingContext != null) {
          for (Map.Entry<Referable, Binding> entry : ge.typecheckingContext.localContext().entrySet()) {
            if (entry.getValue().isHidden()) continue;
            String name = entry.getKey() != null ? entry.getKey().getRefName() : "_";
            Expression typeExpr = ge.bindingTypes.get(entry.getValue());
            if (typeExpr == null) typeExpr = entry.getValue().getTypeExpr();
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
}
