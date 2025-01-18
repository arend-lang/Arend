package org.arend.ext.prettyprinting.doc;

public class BaseDocVisitor<P> implements DocVisitor<P, Void> {
  @Override
  public Void visitVList(VListDoc doc, P params) {
    for (Doc line : doc.getDocs()) {
      line.accept(this, params);
    }
    return null;
  }

  @Override
  public Void visitHList(HListDoc doc, P params) {
    for (Doc line : doc.getDocs()) {
      line.accept(this, params);
    }
    return null;
  }

  @Override
  public Void visitText(TextDoc doc, P params) {
    return null;
  }

  @Override
  public Void visitHang(HangDoc doc, P params) {
    doc.getTop().accept(this, params);
    doc.getBottom().accept(this, params);
    return null;
  }

  @Override
  public Void visitReference(ReferenceDoc doc, P params) {
    return null;
  }

  @Override
  public Void visitCaching(CachingDoc doc, P params) {
    return null;
  }

  @Override
  public Void visitTermLine(TermLineDoc doc, P params) {
    return null;
  }

  @Override
  public Void visitPattern(PatternDoc doc, P params) {
    return null;
  }
}
