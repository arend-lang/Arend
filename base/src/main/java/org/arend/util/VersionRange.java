package org.arend.util;

public class VersionRange extends Range<Version> {
  public VersionRange(Version lowerBound, Version upperBound) {
    super(lowerBound, upperBound);
  }

  public static VersionRange parseVersionRange(String text) {
    Range<String> stringRange = parseRange(text);
    if (stringRange == null) return null;
    Version from = Version.fromString(stringRange.proj1);
    Version to = Version.fromString(stringRange.proj2);
    return from == null && stringRange.proj1 != null || to == null && stringRange.proj2 != null ? null : new VersionRange(from, to);
  }

  /**
   * The range of a version declaration that is present but that {@link #parseVersionRange} cannot read.
   *
   * <p>Falling back to {@link Range#unbound()} for such a declaration would read a typo as "compatible with
   * every language version", so the check would be skipped for exactly the libraries most likely to be
   * broken -- and a library whose extension does not match the API it claims is what ends in a
   * {@code NoSuchMethodError} in the middle of a typechecking pass. This range admits no version instead,
   * so the library is refused and the reported error quotes the text that could not be read.
   */
  public static VersionRange malformed(String text) {
    return new MalformedVersionRange(text);
  }

  private static final class MalformedVersionRange extends VersionRange {
    private final String myText;

    private MalformedVersionRange(String text) {
      super(null, null);
      myText = text;
    }

    @Override
    public boolean inRange(Version version) {
      return false;
    }

    // Both bounds are null, so the inherited Pair equality would make every malformed range equal to
    // unbound() and to every other malformed range.
    @Override
    public boolean equals(Object other) {
      return other instanceof MalformedVersionRange that && myText.equals(that.myText);
    }

    @Override
    public int hashCode() {
      return myText.hashCode();
    }

    @Override
    public String toString() {
      return "'" + myText + "' (cannot be parsed)";
    }
  }

  @Override
  public boolean inRange(Version version) {
    Version v = new Version(version.major, version.minor, version.patch, "");
    return (proj1 == null || proj1.compareTo(proj1.rest.isEmpty() ? v : version) <= 0) && (proj2 == null || proj2.compareTo(proj2.rest.isEmpty() ? v : version) >= 0);
  }
}
