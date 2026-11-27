package com.harvey13.sftpserver;

import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;
import org.apache.sshd.sftp.server.FileHandle;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** SFTP filesystem adapter: confined to the shared-storage root and read-only. */
public final class ReadOnlySftpFileSystemAccessor implements SftpFileSystemAccessor {
    private static IOException readOnly() {
        return new IOException("SFTP server is read-only.");
    }

    @Override
    public Path resolveLocalFilePath(SftpSubsystemProxy subsystem, Path rootDir, String remotePath) throws IOException {
        Path root = rootDir.toAbsolutePath().normalize();
        String relative = remotePath == null ? "" : remotePath.replaceFirst("^/+", "");
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) throw new IOException("Path outside shared storage.");
        Path current = root;
        for (Path part : root.relativize(resolved)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic links are not accessible.");
        }
        return resolved;
    }

    @Override
    public SeekableByteChannel openFile(
            SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle,
            Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        if (options == null || !options.contains(StandardOpenOption.READ)) throw readOnly();
        for (OpenOption option : options) {
            if (option != StandardOpenOption.READ && option != LinkOption.NOFOLLOW_LINKS) throw readOnly();
        }
        return Files.newByteChannel(file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
    }

    @Override public void createDirectory(SftpSubsystemProxy subsystem, Path path) throws IOException { throw readOnly(); }
    @Override public void removeFile(SftpSubsystemProxy subsystem, Path path, boolean isDirectory) throws IOException { throw readOnly(); }
    @Override public void renameFile(SftpSubsystemProxy subsystem, Path oldPath, Path newPath, Collection<java.nio.file.CopyOption> opts) throws IOException { throw readOnly(); }
    @Override public void copyFile(SftpSubsystemProxy subsystem, Path src, Path dst, Collection<java.nio.file.CopyOption> opts) throws IOException { throw readOnly(); }
    @Override public void createLink(SftpSubsystemProxy subsystem, Path link, Path existing, boolean symLink) throws IOException { throw readOnly(); }
    @Override public void setFileAttribute(SftpSubsystemProxy subsystem, Path file, String view, String attribute, Object value, LinkOption... options) throws IOException { throw readOnly(); }
    @Override public void setFilePermissions(SftpSubsystemProxy subsystem, Path file, Set<PosixFilePermission> perms, LinkOption... options) throws IOException { throw readOnly(); }
    @Override public void setFileAccessControl(SftpSubsystemProxy subsystem, Path file, List<AclEntry> acl, LinkOption... options) throws IOException { throw readOnly(); }
    @Override public void setFileOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options) throws IOException { throw readOnly(); }
    @Override public void setGroupOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options) throws IOException { throw readOnly(); }
}
