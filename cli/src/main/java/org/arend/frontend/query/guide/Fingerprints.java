package org.arend.frontend.query.guide;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hashing and text normalization shared by the guide checker. */
final class Fingerprints {
  private Fingerprints() {}

  /** Hex digits kept of a definition fingerprint: 48 bits, ample for one library. */
  static final int HASH_LENGTH = 12;

  /** Hex digits kept of a mention fingerprint, which only has to tell "changed" from "same". */
  static final int MENTION_HASH_LENGTH = 8;

  /** The first {@code hexLength} hex digits of the SHA-256 of {@code text}. */
  static String hash(String text, int hexLength) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(hexLength);
    for (int i = 0; sb.length() < hexLength; i++) {
      sb.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16));
      if (sb.length() < hexLength) sb.append(Character.forDigit(bytes[i] & 0xF, 16));
    }
    return sb.toString();
  }

  /** Collapses every run of whitespace to one space and trims, so layout never matters. */
  static String normalize(String s) {
    StringBuilder out = new StringBuilder(s.length());
    boolean space = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        if (!space) {
          out.append(' ');
          space = true;
        }
      } else {
        out.append(c);
        space = false;
      }
    }
    int end = out.length();
    if (end > 0 && out.charAt(end - 1) == ' ') end--;
    return out.substring(0, end);
  }
}
