package org.arend.frontend.source;

import org.antlr.v4.runtime.*;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.parser.*;
import org.arend.library.SourceLibrary;
import org.arend.module.ModuleLocation;
import org.arend.module.error.ExceptionError;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.ScopeFactory;
import org.arend.source.Source;
import org.arend.source.SourceLoader;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Represents a source that loads a raw module from an {@link InputStream}.
 */
public abstract class StreamRawSource implements Source {
  private final ModulePath myModulePath;
  private final boolean myInTests;
  private ConcreteGroup myGroup;
  private byte myPass = 0;

  protected StreamRawSource(ModulePath modulePath, boolean inTests) {
    myModulePath = modulePath;
    myInTests = inTests;
  }

  @NotNull
  @Override
  public ModulePath getModulePath() {
    return myModulePath;
  }

  /**
   * Gets an input stream from which the source will be loaded.
   *
   * @return an input stream from which the source will be loaded or null if some error occurred.
   */
  @NotNull
  protected abstract InputStream getInputStream() throws IOException;

  @Override
  public @NotNull LoadResult load(SourceLoader sourceLoader) {
    if (myPass == 0) {
      SourceLibrary library = sourceLoader.getLibrary();
      ModulePath modulePath = getModulePath();
      ErrorReporter errorReporter = sourceLoader.getTypecheckingErrorReporter();

      try {
        ModuleLocation module = new ModuleLocation(library, myInTests ? ModuleLocation.LocationKind.TEST : ModuleLocation.LocationKind.SOURCE, modulePath);
        var errorListener = new ReporterErrorListener(errorReporter, module);

        ArendLexer lexer = new ArendLexer(CharStreams.fromStream(getInputStream()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        ArendParser parser = new ArendParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        ArendParser.StatementsContext tree = parser.statements();
        myGroup = new BuildVisitor(module, errorReporter).visitStatements(tree);
        // TODO[server2]: library.groupLoaded(modulePath, myGroup, true, myInTests);

        myPass = 1;
        return LoadResult.CONTINUE;
      } catch (IOException e) {
        errorReporter.report(new ExceptionError(e, "loading", modulePath));
        library.groupLoaded(modulePath, null, true, myInTests);
        return LoadResult.FAIL;
      }
    }

    if (myPass == 1) {
      // TODO[server2]: myGroup.setModuleScopeProvider(sourceLoader.getModuleScopeProvider(myInTests));
      myPass = 2;
      return LoadResult.CONTINUE;
    }

    // TODO[server2]: new DefinitionResolveNameVisitor(ConcreteReferableProvider.INSTANCE, myPass == 2, TypingInfo.EMPTY, sourceLoader.getTypecheckingErrorReporter(), null).resolveGroup(myGroup, myGroup.getGroupScope());
    if (myPass == 2) {
      myPass = 3;
      return LoadResult.CONTINUE;
    }
    sourceLoader.getInstanceProviderSet().collectInstances(myGroup, CachingScope.make(ScopeFactory.parentScopeForGroup(myGroup, sourceLoader.getModuleScopeProvider(myInTests), true)));
    return LoadResult.SUCCESS;
  }
}
