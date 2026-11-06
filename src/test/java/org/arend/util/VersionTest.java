package org.arend.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Release versions have three components (1.12.0) and plugin revisions a fourth (1.12.0.1);
 * {@code arend.yaml} and the left-hand sides of {@code arend-lib/versions} use two (1.12).
 * The first four-component version, 1.6.0.1, broke {@link Version#fromString} on release day.
 */
public class VersionTest {
  private static Version v(String s) {
    Version version = Version.fromString(s);
    assertNotNull("cannot parse " + s, version);
    return version;
  }

  @Test
  public void roundTrip() {
    assertEquals("1.12", v("1.12").toString());
    assertEquals("1.12", v("1.12.0").toString());
    assertEquals("1.12.0", v("1.12").getLongString());
    assertEquals("1.12.0", v("1.12.0").getLongString());
    assertEquals("1.12.1", v("1.12.1").toString());
    assertEquals("1.12.0.3", v("1.12.0.3").toString());
    assertEquals("1.12.0.3", v("1.12.0.3").getLongString());
    assertEquals(v("1.12"), v("1.12.0"));
  }

  @Test
  public void invalid() {
    assertNull(Version.fromString(null));
    assertNull(Version.fromString("1.x"));
    assertNull(Version.fromString("v1.12.0"));
  }

  @Test
  public void order() {
    assertTrue(v("1.12.0").compareTo(v("1.12.0.1")) < 0);
    assertTrue(v("1.12.0.1").compareTo(v("1.12.1")) < 0);
    assertTrue(v("1.11.0.3").compareTo(v("1.12.0")) < 0);
    assertTrue(v("1.9.0").compareTo(v("1.10.0")) < 0);
    // A revision is a number, not a string: "10" sorts after "9"
    assertTrue(v("1.12.0.9").compareTo(v("1.12.0.10")) < 0);
    assertTrue(v("1.12.0.10").compareTo(v("1.12.0.10.1")) < 0);
    assertEquals(0, v("1.12.0.3").compareTo(v("1.12.0.3")));
  }

  @Test
  public void range() {
    VersionRange exact = VersionRange.parseVersionRange("1.12");
    assertNotNull(exact);
    assertTrue(exact.inRange(v("1.12")));
    assertTrue(exact.inRange(v("1.12.0")));
    assertTrue(exact.inRange(v("1.12.0.3")));
    assertFalse(exact.inRange(v("1.11.0")));
    assertFalse(exact.inRange(v("1.13.0")));

    VersionRange from = VersionRange.parseVersionRange(">= 1.12");
    assertNotNull(from);
    assertTrue(from.inRange(v("1.12.0.1")));
    assertTrue(from.inRange(v("1.13.0")));
    assertFalse(from.inRange(v("1.11.0.3")));
  }
}
