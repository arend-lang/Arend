package org.arend.frontend.cli.daemon;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Files only their owner may touch: the short directory a socket falls back to. The checks here are
 * what keep another local user from planting a file a client then trusts.
 *
 * <p>POSIX permissions where the file system has them; elsewhere -- Windows -- the owner, and an ACL
 * granting only the owner: set best effort, and checked when read.
 */
public final class PrivateFiles {
  private PrivateFiles() {}

  private static final Set<PosixFilePermission> GROUP_OR_OTHER = EnumSet.of(
      PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);

  private static volatile UserPrincipal currentUser;

  /** The user this process runs as, as the file system names owners; null when it will not say. */
  static UserPrincipal currentUser() {
    UserPrincipal user = currentUser;
    if (user != null) return user;
    try {
      user = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
    } catch (IOException | UnsupportedOperationException e) {
      try {
        user = Files.getOwner(Paths.get(System.getProperty("user.home")));
      } catch (IOException | UnsupportedOperationException ignored) {
        return null;
      }
    }
    currentUser = user;
    return user;
  }

  /** Whether {@code path} belongs to the user this process runs as; true when the file system cannot tell. */
  static boolean ownedByCurrentUser(Path path) throws IOException {
    UserPrincipal user = currentUser();
    if (user == null) return true;
    try {
      return Files.getOwner(path).equals(user);
    } catch (UnsupportedOperationException e) {
      return true;
    }
  }

  /**
   * Creates {@code dir} and its parents if they are not there, and makes it the owner's alone; one
   * that is there already is made so if it is the user's. Throws when it belongs to somebody else:
   * whatever is put in it is theirs to read and replace.
   */
  public static void ensurePrivateDirectory(Path dir) throws IOException {
    Files.createDirectories(dir);
    if (!ownedByCurrentUser(dir)) {
      throw new IOException(dir + " belongs to another user");
    }
    restrictToOwner(dir, true);
  }

  /** What nobody but its owner may do to a private file: read it, or change or replace it. */
  private static final Set<AclEntryPermission> FILE_ACCESS = EnumSet.of(
      AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA, AclEntryPermission.APPEND_DATA,
      AclEntryPermission.DELETE, AclEntryPermission.WRITE_ACL, AclEntryPermission.WRITE_OWNER);

  /** What nobody but its owner may do to a private directory: put, replace or remove what is in it. */
  private static final Set<AclEntryPermission> DIRECTORY_ACCESS = EnumSet.of(
      AclEntryPermission.ADD_FILE, AclEntryPermission.ADD_SUBDIRECTORY, AclEntryPermission.DELETE_CHILD,
      AclEntryPermission.DELETE, AclEntryPermission.WRITE_ACL, AclEntryPermission.WRITE_OWNER);

  /**
   * Whether {@code path} is the owner's alone: it is the current user's, and nobody else may write
   * it -- or, unless {@code directory}, read it.
   */
  public static boolean isPrivate(Path path, boolean directory) {
    try {
      if (!ownedByCurrentUser(path)) return false;
      PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
      if (posix == null) {
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        return acl == null || aclIsPrivate(acl.getAcl(), acl.getOwner(), directory);
      }
      Set<PosixFilePermission> permissions = posix.readAttributes().permissions();
      return directory
          ? !permissions.contains(PosixFilePermission.GROUP_WRITE) && !permissions.contains(PosixFilePermission.OTHERS_WRITE)
          : permissions.stream().noneMatch(GROUP_OR_OTHER::contains);
    } catch (IOException | UnsupportedOperationException e) {
      return false;
    }
  }

  /**
   * Whether {@code acl} grants nobody but {@code owner} what {@link #isPrivate} keeps to the owner;
   * for a directory, that includes reading the files it passes its entries on to. SYSTEM and
   * Administrators count as anybody: their names are localized and Java will not look up a SID,
   * and the ACL {@link #restrictToOwner} sets has neither.
   */
  static boolean aclIsPrivate(List<AclEntry> acl, UserPrincipal owner, boolean directory) {
    for (AclEntry entry : acl) {
      if (entry.type() != AclEntryType.ALLOW || entry.principal().equals(owner)) continue;
      Set<AclEntryPermission> granted = entry.permissions();
      Set<AclEntryPermission> kept = directory ? DIRECTORY_ACCESS : FILE_ACCESS;
      if (granted.stream().anyMatch(kept::contains)) return false;
      if (directory && entry.flags().contains(AclEntryFlag.FILE_INHERIT) && granted.contains(AclEntryPermission.READ_DATA)) {
        return false;
      }
    }
    return true;
  }

  /** Makes {@code path} the owner's alone, best effort: a file system that has neither permissions nor ACLs keeps its own. */
  public static void restrictToOwner(Path path, boolean directory) {
    try {
      PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
      if (posix != null) {
        posix.setPermissions(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        return;
      }
      AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
      if (acl != null) {
        acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
            .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
      }
    } catch (IOException | UnsupportedOperationException ignored) {
    }
  }
}
