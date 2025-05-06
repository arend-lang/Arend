package org.arend;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.frontend.PositionComparator;
import org.arend.frontend.library.PreludeFileLibrary;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.EmptyScope;
import org.arend.naming.scope.Scope;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer;
import org.arend.typechecking.instance.provider.InstanceScopeProvider;
import org.arend.typechecking.order.listener.TypecheckingOrderingListener;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.visitor.ArendCheckerFactory;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Before;

import java.util.*;

import static org.arend.ext.prettyprinting.doc.DocFactory.text;
import static org.arend.ext.prettyprinting.doc.DocFactory.vList;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class ArendTestCase {
  protected ArendServer server;
  protected final List<GeneralError> errorList = new ArrayList<>();
  protected final ListErrorReporter errorReporter = new ListErrorReporter(errorList);
  protected final TypecheckingOrderingListener typechecking = new TypecheckingOrderingListener(ArendCheckerFactory.DEFAULT, InstanceScopeProvider.EMPTY, ConcreteProvider.EMPTY /* TODO[server2] */, errorReporter, PositionComparator.INSTANCE, ref -> null);

  @Before
  public void initializeServer() {
    errorList.clear();
    server = new ArendServerImpl(ArendServerRequester.TRIVIAL, false, false, null);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION, Objects.requireNonNull(PreludeFileLibrary.getSource().loadGroup(DummyErrorReporter.INSTANCE)));
  }

  public TCDefReferable get(Scope scope, String path) {
    Referable referable = Scope.resolveName(scope, Arrays.asList(path.split("\\.")));
    return referable instanceof TCDefReferable ? (TCDefReferable) referable : null;
  }

  public TCDefReferable getDef(ConcreteGroup group, String path) {
    loop:
    for (String name : path.split("\\.")) {
      for (ConcreteStatement statement : group.statements()) {
        if (statement.group() != null && statement.group().referable().getRefName().equals(name)) {
          group = statement.group();
          continue loop;
        }
      }
      return null;
    }
    return group.referable() instanceof TCDefReferable ref ? ref : null;
  }

  @SafeVarargs
  protected final void assertThatErrorsAre(Matcher<? super GeneralError>... matchers) {
    assertThat(errorList, Matchers.contains(matchers));
  }


  protected static Matcher<? super Collection<? extends GeneralError>> containsErrors(final int n) {
    return new TypeSafeDiagnosingMatcher<>() {
      @Override
      protected boolean matchesSafely(Collection<? extends GeneralError> errors, Description description) {
        if (errors.isEmpty()) {
          description.appendText("there were no errors");
        } else {
          List<Doc> docs = new ArrayList<>(errors.size() + 1);
          docs.add(text("there were errors:"));
          for (GeneralError error : errors) {
            docs.add(error.getDoc(new PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE)));
          }
          description.appendText(vList(docs).toString());
        }
        return n < 0 ? !errors.isEmpty() : errors.size() == n;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("expected number of errors: ").appendValue(n);
      }
    };
  }
}
