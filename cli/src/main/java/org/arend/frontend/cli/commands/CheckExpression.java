package org.arend.frontend.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.Scope;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.DefinitionData;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.error.local.GoalError;
import org.arend.typechecking.result.TypecheckingResult;
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.arend.typechecking.visitor.SyntacticDesugarVisitor;

import java.util.*;

/**
 * {@code -ce MODULE:DEF GOAL_ID EXPRESSION} — parse, resolve, and typecheck
 * an expression against a goal's expected type. Returns structured JSON.
 */
public final class CheckExpression {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private CheckExpression() {}

  public static boolean run(CommandContext ctx, String[] args) {
    if (args == null || args.length < 3) {
      System.err.println("[ERROR] -ce requires: MODULE:DEF GOAL_ID EXPRESSION");
      return false;
    }

    String spec = args[0];
    String goalId = args[1];
    String exprText = args[2];

    Pair<ModulePath, LongName> parsed = ctx.parseFullName(spec);
    if (parsed == null) return false;

    ModuleLocation module = ctx.server.findModule(parsed.proj1, null, false, false);
    if (module == null) {
      ctx.systemErrErrorReporter.report(new ModuleNotFoundError(parsed.proj1));
      return false;
    }

    List<GoalError> goalErrors = new ArrayList<>();
    org.arend.ext.error.ErrorReporter capture = error -> {
      if (error instanceof GoalError ge) {
        goalErrors.add(ge);
      }
    };

    ctx.server.getCheckerFor(Collections.singletonList(module))
        .resolveAll(ctx.cancellation, ProgressReporter.empty());

    if (parsed.proj2 != null) {
      ctx.server.getCheckerFor(Collections.singletonList(module))
          .typecheck(Collections.singletonList(new FullName(module, parsed.proj2)),
              capture, ctx.cancellation, ProgressReporter.empty());
    } else {
      ctx.server.getCheckerFor(Collections.singletonList(module))
          .typecheck(ctx.cancellation, ProgressReporter.empty());
    }

    int targetGoal;
    try {
      targetGoal = Integer.parseInt(goalId);
    } catch (NumberFormatException e) {
      System.err.println("[ERROR] Invalid goal ID: " + goalId);
      return false;
    }

    if (targetGoal < 0 || targetGoal >= goalErrors.size()) {
      System.err.println("[ERROR] Goal ID " + goalId + " out of range (found " + goalErrors.size() + " goals)");
      return false;
    }

    GoalError goal = goalErrors.get(targetGoal);

    List<String> checkErrors = new ArrayList<>();
    boolean success = false;
    String normalizedExpr = "";

    try {
      // Parse using ANTLR (same pattern as CommonCliRepl)
      var parser = CommonCliRepl.createParser(exprText, module, ctx.systemErrErrorReporter);
      var buildVisitor = new org.arend.frontend.parser.BuildVisitor(module, ctx.systemErrErrorReporter);
      Concrete.Expression concreteExpr = buildVisitor.visitExpr(parser.expr());

      if (concreteExpr == null) {
        checkErrors.add("Failed to parse expression: " + exprText);
      } else {
        // Resolve names in scope
        Scope scope = null;
        if (parsed.proj2 != null) {
          for (DefinitionData data : ctx.server.getResolvedDefinitions(module)) {
            if (data.definition().getData().getRefLongName().equals(parsed.proj2)) {
              scope = ctx.server.getReferableScope(data.definition().getData());
              break;
            }
          }
        }

        if (scope != null) {
          TypingInfo typingInfo = ctx.server instanceof ArendServerImpl si
              ? si.getTypingInfo() : TypingInfo.EMPTY;
          concreteExpr = SyntacticDesugarVisitor.desugar(
              concreteExpr.accept(
                  new ExpressionResolveNameVisitor(scope, new ArrayList<>(), typingInfo,
                      ctx.systemErrErrorReporter, null, null),
                  null),
              ctx.systemErrErrorReporter);
        }

        // Typecheck against expected type
        if (goal.typecheckingContext != null && goal.expectedType != null) {
          List<GeneralError> tcErrors = new ArrayList<>();
          CheckTypeVisitor checker = CheckTypeVisitor.loadTypecheckingContext(
              goal.typecheckingContext, tcErrors::add);

          TypecheckingResult result = checker.checkExpr(concreteExpr, goal.expectedType);
          if (result != null) {
            success = true;
            normalizedExpr = result.expression.toString();
          } else {
            for (GeneralError e : tcErrors) {
              checkErrors.add(e.toString());
            }
            if (checkErrors.isEmpty()) {
              checkErrors.add("Typechecking failed");
            }
          }
        } else {
          checkErrors.add("No typechecking context available for this goal");
        }
      }
    } catch (Exception e) {
      checkErrors.add("Exception: " + e.getMessage());
    }

    try {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("success", success);
      result.put("normalizedExpr", normalizedExpr);
      result.put("errors", checkErrors);
      System.out.println(MAPPER.writeValueAsString(result));
      return true;
    } catch (Exception e) {
      System.err.println("[ERROR] Failed to serialize result: " + e.getMessage());
      return false;
    }
  }
}
