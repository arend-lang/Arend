package org.arend.frontend.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.Scope;
import org.arend.naming.scope.local.ListScope;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.error.local.GoalError;
import org.arend.typechecking.visitor.SyntacticDesugarVisitor;

import java.util.*;

/**
 * {@code -ce MODULE:DEF GOAL_ID EXPRESSION} — substitute expression into the
 * goal position, re-typecheck via the server, and report whether it succeeded.
 * Uses the full server typecheck path so metas (rewrite, simp, etc.) work.
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

    // Phase 1: reset + typecheck to find the target goal
    ConcreteGroup group = ctx.server.getRawGroup(module);
    if (group != null && parsed.proj2 != null) {
      resetDefinition(group, parsed.proj2);
    }

    List<GoalError> goalErrors = new ArrayList<>();
    org.arend.ext.error.ErrorReporter capture = error -> {
      if (error instanceof GoalError ge) goalErrors.add(ge);
    };
    ctx.server.addErrorReporter(capture);

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

    // Phase 2: parse the replacement expression and substitute it into the goal
    List<String> checkErrors = new ArrayList<>();
    boolean success = false;
    String normalizedExpr = "";

    try {
      List<String> parseErrors = new ArrayList<>();
      org.arend.ext.error.ErrorReporter parseCapture = error -> {
        if (error instanceof org.arend.frontend.parser.ParserError) {
          parseErrors.add(error.toString());
        }
      };
      var parser = CommonCliRepl.createParser(exprText, module, parseCapture);
      var buildVisitor = new org.arend.frontend.parser.BuildVisitor(module, parseCapture);
      Concrete.Expression replacementExpr = buildVisitor.visitExpr(parser.expr());

      if (!parseErrors.isEmpty()) {
        checkErrors.addAll(parseErrors);
        replacementExpr = null;
      } else if (parser.getCurrentToken().getType() != org.antlr.v4.runtime.Token.EOF) {
        String remaining = exprText.substring(parser.getCurrentToken().getStartIndex());
        checkErrors.add("Parse error: unexpected input starting at '" + remaining + "'");
        replacementExpr = null;
      }

      if (replacementExpr == null) {
        if (checkErrors.isEmpty()) checkErrors.add("Failed to parse expression: " + exprText);
      } else {
        // Resolve names in the parsed expression using module scope + locals
        Scope scope = null;
        Concrete.GeneralDefinition targetDef = null;
        for (DefinitionData data : ctx.server.getResolvedDefinitions(module)) {
          if (parsed.proj2 == null || data.definition().getData().getRefLongName().equals(parsed.proj2)) {
            targetDef = data.definition();
            scope = ctx.server.getReferableScope(data.definition().getData());
            break;
          }
        }

        if (scope != null) {
          if (goal.typecheckingContext != null) {
            List<ArendRef> locals = new ArrayList<>();
            for (Referable ref : goal.typecheckingContext.localContext().keySet()) {
              if (ref != null) locals.add(ref);
            }
            if (!locals.isEmpty()) {
              scope = new ListScope(scope, locals);
            }
          }
          TypingInfo typingInfo = ctx.server instanceof ArendServerImpl si
              ? si.getTypingInfo() : TypingInfo.EMPTY;
          replacementExpr = SyntacticDesugarVisitor.desugar(
              replacementExpr.accept(
                  new ExpressionResolveNameVisitor(scope, new ArrayList<>(), typingInfo,
                      ctx.systemErrErrorReporter, null, null),
                  null),
              ctx.systemErrErrorReporter);
        }

        // Substitute the goal and re-typecheck
        if (targetDef instanceof Concrete.FunctionDefinition funcDef) {
          Concrete.Expression origTermBody = null;
          Map<Concrete.FunctionClause, Concrete.Expression> origClauseBodies = null;

          if (funcDef.getBody() instanceof Concrete.TermFunctionBody termBody) {
            origTermBody = termBody.getTerm();
            if (origTermBody != null) {
              termBody.setTerm(substituteGoalByIndex(origTermBody, targetGoal, replacementExpr));
            } else {
              checkErrors.add("Function has no body expression");
            }
          } else if (funcDef.getBody() instanceof Concrete.ElimFunctionBody elimBody) {
            origClauseBodies = new LinkedHashMap<>();
            int[] counter = {0};
            for (Concrete.FunctionClause clause : elimBody.getClauses()) {
              if (clause.expression != null) {
                origClauseBodies.put(clause, clause.expression);
                clause.expression = substituteGoalByIndex(clause.expression, counter, targetGoal, replacementExpr);
              }
            }
          } else {
            checkErrors.add("Unsupported function body type");
          }

          if (checkErrors.isEmpty()) {
            funcDef.getData().setTypechecked(null);

            List<GeneralError> tcErrors = new ArrayList<>();
            org.arend.ext.error.ErrorReporter errCapture = error -> {
              if (error instanceof GoalError) return;
              if (error.level == GeneralError.Level.ERROR) {
                tcErrors.add(error);
              }
            };
            ctx.server.addErrorReporter(errCapture);

            ctx.server.getCheckerFor(Collections.singletonList(module))
                .typecheck(Collections.singletonList(new FullName(module, parsed.proj2)),
                    errCapture, ctx.cancellation, ProgressReporter.empty());

            if (tcErrors.isEmpty()) {
              success = true;
              normalizedExpr = exprText;
            } else {
              for (GeneralError e : tcErrors) {
                checkErrors.add(e.toString());
              }
            }

            // Restore original bodies
            if (origTermBody != null && funcDef.getBody() instanceof Concrete.TermFunctionBody tb) {
              tb.setTerm(origTermBody);
            }
            if (origClauseBodies != null) {
              for (var entry : origClauseBodies.entrySet()) {
                entry.getKey().expression = entry.getValue();
              }
            }
          }
        } else {
          checkErrors.add("Target is not a function definition");
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

  private static Concrete.Expression substituteGoalByIndex(
      Concrete.Expression body,
      int goalIndex,
      Concrete.Expression replacement) {
    return substituteGoalByIndex(body, new int[]{0}, goalIndex, replacement);
  }

  private static Concrete.Expression substituteGoalByIndex(
      Concrete.Expression body,
      int[] counter,
      int goalIndex,
      Concrete.Expression replacement) {
    if (body instanceof Concrete.GoalExpression) {
      return counter[0]++ == goalIndex ? replacement : body;
    }
    return body.accept(new org.arend.term.concrete.BaseConcreteExpressionVisitor<Void>() {
      @Override
      public Concrete.Expression visitGoal(Concrete.GoalExpression expr, Void params) {
        return counter[0]++ == goalIndex ? replacement : expr;
      }
    }, null);
  }

  private static boolean resetDefinition(ConcreteGroup group, LongName targetName) {
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
}
