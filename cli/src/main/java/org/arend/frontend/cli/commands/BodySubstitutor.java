package org.arend.frontend.cli.commands;

import org.arend.ext.error.GeneralError;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.reference.ArendRef;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.naming.reference.Referable;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.Scope;
import org.arend.naming.scope.local.ListScope;
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.visitor.SyntacticDesugarVisitor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Temporary in-memory substitution of a function definition's body.
 *
 * <p>Used by proof-state-aware commands (currently {@code -te}) that need the
 * definition to carry the caller's current proof body while they find goals — so
 * goal indices and goal contexts (branch-local bindings) match the caller's proof
 * state instead of the on-disk body. The original body is always put back by
 * {@link #restore()}, which callers must invoke (typically in {@code finally});
 * on a warm daemon context a missed restore leaks the attempted body into all
 * subsequent commands.
 *
 * <p>Parsing/resolution mirrors {@link ApplyStep}; kept separate so the behaviour
 * and error output of {@code -as} stay untouched.
 */
final class BodySubstitutor {
  private final Concrete.FunctionDefinition funcDef;
  private final Concrete.FunctionBody origBody;
  private final Concrete.Expression origTerm;
  private boolean substituted;

  private BodySubstitutor(Concrete.FunctionDefinition funcDef) {
    this.funcDef = funcDef;
    this.origBody = funcDef.getBody();
    // TermFunctionBody is mutated in place by setTerm, so snapshot the term itself;
    // restoring origBody alone would be a no-op.
    this.origTerm = origBody instanceof Concrete.TermFunctionBody tb ? tb.getTerm() : null;
  }

  /**
   * Parses {@code bodyText} (plain expression or top-level {@code \elim} body),
   * resolves it in the definition's scope, and substitutes it into {@code funcDef}.
   *
   * @return the substitutor on success ({@link #restore()} must be called), or
   *         {@code null} after printing the error to stderr.
   */
  static BodySubstitutor substitute(CommandContext ctx, ModuleLocation module,
                                    Concrete.FunctionDefinition funcDef, String bodyText) {
    BodySubstitutor s = new BodySubstitutor(funcDef);

    boolean isElimBody = bodyText.stripLeading().startsWith("\\elim");
    List<String> parseErrors = new ArrayList<>();
    org.arend.ext.error.ErrorReporter parseCapture = error -> {
      if (error instanceof org.arend.frontend.parser.ParserError) {
        parseErrors.add(error.toString());
      }
    };

    Concrete.Expression bodyExpr = null;
    Concrete.ElimFunctionBody elimBody = null;

    if (isElimBody) {
      var parser = CommonCliRepl.createParser(bodyText, module, parseCapture);
      var buildVisitor = new BuildVisitor(module, parseCapture);
      var bodyCtx = parser.functionBody();
      if (parseErrors.isEmpty()
          && parser.getCurrentToken().getType() == org.antlr.v4.runtime.Token.EOF
          && bodyCtx instanceof org.arend.frontend.parser.ArendParser.WithElimContext elimCtx) {
        List<Concrete.ReferenceExpression> elimRefs = buildVisitor.visitElim(elimCtx.elim());
        var clausesCtx = elimCtx.clauses();
        List<Concrete.FunctionClause> clauses = new ArrayList<>();
        var clauseList = clausesCtx instanceof org.arend.frontend.parser.ArendParser.ClausesWithBracesContext cb
            ? cb.clause()
            : ((org.arend.frontend.parser.ArendParser.ClausesWithoutBracesContext) clausesCtx).clause();
        for (var clauseCtx : clauseList) {
          clauses.add(buildVisitor.visitClause(clauseCtx));
        }
        elimBody = new Concrete.ElimFunctionBody(null, elimRefs, clauses);
      }
    } else {
      var parser = CommonCliRepl.createParser(bodyText, module, parseCapture);
      var buildVisitor = new BuildVisitor(module, parseCapture);
      bodyExpr = buildVisitor.visitExpr(parser.expr());
      if (!parseErrors.isEmpty()
          || parser.getCurrentToken().getType() != org.antlr.v4.runtime.Token.EOF) {
        bodyExpr = null;
      }
    }

    if (bodyExpr == null && elimBody == null) {
      System.err.println("[ERROR] Failed to parse proof body" +
          (parseErrors.isEmpty() ? "" : ": " + parseErrors.get(0)));
      return null;
    }

    // Resolve in the definition's scope extended with its parameters (as in ApplyStep).
    Scope scope = ctx.server.getReferableScope(funcDef.getData());
    List<ArendRef> params = new ArrayList<>();
    for (Concrete.Parameter param : funcDef.getParameters()) {
      for (Referable ref : param.getReferableList()) {
        if (ref != null) params.add(ref);
      }
    }
    if (!params.isEmpty()) {
      scope = new ListScope(scope, params);
    }

    TypingInfo typingInfo = ctx.server instanceof ArendServerImpl si
        ? si.getTypingInfo() : TypingInfo.EMPTY;
    List<String> resolveErrors = new ArrayList<>();
    org.arend.ext.error.ErrorReporter resolveCapture = error -> {
      if (error.level == GeneralError.Level.ERROR) {
        resolveErrors.add(error.toString());
      }
    };

    if (bodyExpr != null) {
      bodyExpr = SyntacticDesugarVisitor.desugar(
          bodyExpr.accept(
              new ExpressionResolveNameVisitor(scope, new ArrayList<>(), typingInfo,
                  resolveCapture, null, null),
              null),
          resolveCapture);
    } else {
      var exprVisitor = new ExpressionResolveNameVisitor(scope, new ArrayList<>(), typingInfo,
          resolveCapture, null, null);
      for (int i = 0; i < elimBody.getEliminatedReferences().size(); i++) {
        Concrete.Expression resolved = elimBody.getEliminatedReferences().get(i).accept(exprVisitor, null);
        if (resolved instanceof Concrete.ReferenceExpression resolvedRef) {
          elimBody.getEliminatedReferences().set(i, resolvedRef);
        }
      }
      exprVisitor.visitClauses(elimBody.getClauses(), null);
      for (Concrete.FunctionClause clause : elimBody.getClauses()) {
        if (clause.expression != null) {
          clause.expression = SyntacticDesugarVisitor.desugar(clause.expression, resolveCapture);
        }
      }
    }

    if (!resolveErrors.isEmpty()) {
      System.err.println("[ERROR] Failed to resolve proof body: " + resolveErrors.get(0));
      return null;
    }

    if (bodyExpr != null) {
      if (s.origBody instanceof Concrete.TermFunctionBody tb) {
        tb.setTerm(bodyExpr);
        s.substituted = true;
      } else {
        System.err.println("[ERROR] Unsupported function body type for expression");
        return null;
      }
    } else {
      setBody(funcDef, elimBody);
      s.substituted = true;
    }
    return s;
  }

  /** Puts the original body back and drops the typechecked state of the substituted one. */
  void restore() {
    if (!substituted) return;
    if (origBody instanceof Concrete.TermFunctionBody tb && origTerm != null) {
      tb.setTerm(origTerm);
    } else {
      setBody(funcDef, origBody);
    }
    funcDef.getData().setTypechecked(null);
  }

  private static void setBody(Concrete.FunctionDefinition funcDef, Concrete.FunctionBody body) {
    try {
      Field bodyField = Concrete.BaseFunctionDefinition.class.getDeclaredField("myBody");
      bodyField.setAccessible(true);
      bodyField.set(funcDef, body);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to set function body", e);
    }
  }
}
