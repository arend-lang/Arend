package org.arend.naming;

import org.arend.ArendTestCase;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.frontend.parser.*;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.library.MemoryLibrary;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ConcreteCompareVisitor;
import org.arend.term.group.*;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;

public abstract class ParserTestCase extends ArendTestCase {
  protected final static ModuleLocation MODULE = new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.SOURCE, new ModulePath("$TestCase$"));
  protected static final LocatedReferable MODULE_REF = new FullModuleReferable(MODULE);

  private ArendParser _parse(String text, ListErrorReporter errorReporter) {
    return CommonCliRepl.createParser(text, MODULE, errorReporter);
  }


  Concrete.Expression parseExpr(String text, int errors) {
    ListErrorReporter errorReporter = new ListErrorReporter();
    ArendParser.ExprContext ctx = _parse(text, errorReporter).expr();
    Concrete.Expression expr = errorReporter.getErrorList().isEmpty() ? new BuildVisitor(MODULE, errorReporter).visitExpr(ctx) : null;
    assertThat(errorReporter.getErrorList(), containsErrors(errors));
    return expr;
  }

  protected Concrete.Expression parseExpr(String text) {
    return parseExpr(text, 0);
  }

  ConcreteGroup parseDef(String text, int errors) {
    ListErrorReporter errorReporter = new ListErrorReporter();
    ArendParser.DefinitionContext ctx = _parse(text, errorReporter).definition();
    List<ConcreteStatement> statements = new ArrayList<>(1);
    ConcreteGroup fileGroup = new ConcreteGroup(DocFactory.nullDoc(), new FullModuleReferable(MODULE), null, statements, Collections.emptyList(), Collections.emptyList());
    ConcreteGroup definition = errorReporter.getErrorList().isEmpty() ? new BuildVisitor(MODULE, errorReporter).visitDefinition(AccessModifier.PUBLIC, ctx, fileGroup, null) : null;
    if (definition != null) {
      statements.add(new ConcreteStatement(definition, null, null, null));
    }
    assertThat(errorReporter.getErrorList(), containsErrors(errors));
    return definition;
  }

  protected ConcreteGroup parseDef(String text) {
    return parseDef(text, 0);
  }

  @SafeVarargs
  protected final ConcreteGroup parseModule(String text, int errors, Matcher<? super GeneralError>... matchers) {
    ListErrorReporter errorReporter = new ListErrorReporter();
    ArendParser.StatementsContext tree = _parse(text, errorReporter).statements();
    ConcreteGroup group = errorReporter.getErrorList().isEmpty() ? new BuildVisitor(MODULE, errorReporter).visitStatements(tree) : null;
    assertThat(errorReporter.getErrorList(), containsErrors(errors));
    if (matchers.length > 0) {
      assertThat(errorReporter.getErrorList(), Matchers.contains(matchers));
    }
    return group;
  }

  protected ConcreteGroup parseModule(String text) {
    return parseModule(text, 0);
  }


  protected static boolean compareAbstract(Concrete.Expression expr1, Concrete.Expression expr2) {
    return new ConcreteCompareVisitor().compare(expr1, expr2);
  }
}
