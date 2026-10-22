package org.arend.frontend.cli.daemon;

import org.junit.Test;

import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Where there are no POSIX permissions -- Windows -- a file is private only if its ACL grants nobody
 * but its owner.
 */
public class PrivateFilesTest {
  private static final UserPrincipal OWNER = () -> "owner";
  private static final UserPrincipal OTHER = () -> "other";

  private static AclEntry allow(UserPrincipal principal, Set<AclEntryPermission> permissions, AclEntryFlag... flags) {
    AclEntry.Builder builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(principal).setPermissions(permissions);
    if (flags.length > 0) builder.setFlags(flags);
    return builder.build();
  }

  @Test
  public void theOwnersAloneIsPrivate() {
    List<AclEntry> acl = List.of(allow(OWNER, EnumSet.allOf(AclEntryPermission.class)));
    assertTrue(PrivateFiles.aclIsPrivate(acl, OWNER, false));
    assertTrue(PrivateFiles.aclIsPrivate(acl, OWNER, true));
  }

  @Test
  public void aFileAnybodyElseMayReadIsNot() {
    List<AclEntry> acl = List.of(allow(OWNER, EnumSet.allOf(AclEntryPermission.class)),
        allow(OTHER, EnumSet.of(AclEntryPermission.READ_DATA, AclEntryPermission.READ_ATTRIBUTES)));
    assertFalse(PrivateFiles.aclIsPrivate(acl, OWNER, false));
  }

  @Test
  public void aDirectoryAnybodyElseMayAddToIsNot() {
    List<AclEntry> acl = List.of(allow(OWNER, EnumSet.allOf(AclEntryPermission.class)),
        allow(OTHER, EnumSet.of(AclEntryPermission.ADD_FILE)));
    assertFalse(PrivateFiles.aclIsPrivate(acl, OWNER, true));
  }

  /** Listing a directory gives away only the names in it; what it passes on to its files is what they get. */
  @Test
  public void aDirectoryAnybodyElseMayListIsPrivateUnlessItsFilesInheritReading() {
    Set<AclEntryPermission> list = EnumSet.of(AclEntryPermission.LIST_DIRECTORY, AclEntryPermission.READ_ATTRIBUTES);
    assertTrue(PrivateFiles.aclIsPrivate(List.of(allow(OTHER, list)), OWNER, true));
    assertFalse(PrivateFiles.aclIsPrivate(List.of(allow(OTHER, list, AclEntryFlag.FILE_INHERIT)), OWNER, true));
  }

  @Test
  public void whatIsDeniedIsNoGrant() {
    AclEntry deny = AclEntry.newBuilder().setType(AclEntryType.DENY).setPrincipal(OTHER)
        .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
    assertTrue(PrivateFiles.aclIsPrivate(List.of(deny), OWNER, false));
  }
}
