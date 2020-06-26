package org.arend.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.arend.ArendLanguage;
import org.arend.lexer.ArendDocLexerAdapter;
import org.arend.psi.ArendTokenType;
import org.arend.psi.doc.ArendDocComment;
import org.jetbrains.annotations.NotNull;

public class ParserMixin {
  public static final ArendTokenType DOC_START = new ArendTokenType("-- |");
  public static final ArendTokenType DOC_IGNORED = new ArendTokenType("DOC_IGNORED");
  public static final ArendTokenType DOC_TEXT = new ArendTokenType("DOC_TEXT");
  public static final ArendTokenType DOC_CODE = new ArendTokenType("DOC_CODE");

  public static final ILazyParseableElementType DOC_COMMENT = new ILazyParseableElementType("DOC_COMMENT", ArendLanguage.INSTANCE) {
    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      return new ArendDocParser().parse(this, PsiBuilderFactory.getInstance().createBuilder(chameleon.getTreeParent().getPsi().getProject(), chameleon, new ArendDocLexerAdapter(), ArendLanguage.INSTANCE, chameleon.getText())).getFirstChildNode();
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new ArendDocComment(text);
    }
  };

  private final static int MAX_RECURSION_LEVEL = 10000;

  static boolean recursion_guard_(PsiBuilder builder, int level, String funcName) {
    if (level > MAX_RECURSION_LEVEL) {
      builder.mark().error("Maximum recursion level (" + MAX_RECURSION_LEVEL + ") reached in '" + funcName + "'");
      return false;
    }
    return true;
  }
}
