package org.satsim.cag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link ConfirmationChannel} backed by a directory: one file per recorded
 * confirmation, named by its token. Written by {@link CagConfirm}, run by a
 * human at their own terminal.
 *
 * <p>Why a directory and not an MCP tool: this is the only part of the gate an
 * operator client cannot reach. The MCP surface has no tool that records a
 * confirmation, so a client — however it is configured, whichever client it is
 * — can relay a confirmation request and re-submit the command afterwards, but
 * cannot confirm anything itself (ADR-0007 C5, SCR-010 §5 F-4).
 *
 * <p>The honest limit: an operator host with arbitrary shell execution can run
 * {@link CagConfirm} as easily as a human can. ADR-0007 C8 states this in its
 * first row — containment holds for the tool path, and structural containment
 * against a general-purpose agent on the same machine is not achievable. The
 * claim made here is exactly that and no more.
 *
 * <p>Polled on demand from {@link CommandAuthorizationGate#decide}; no watcher,
 * no thread, no wall-clock read.
 */
public final class FileConfirmationChannel implements ConfirmationChannel {

  private final Path directory;

  /**
   * @param directory the confirmation directory; created if absent
   */
  public FileConfirmationChannel(Path directory) {
    this.directory = directory;
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot create confirmation directory " + directory, e);
    }
  }

  @Override
  public Set<String> recorded() {
    Set<String> tokens = new LinkedHashSet<>();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
      for (Path entry : entries) {
        Path name = entry.getFileName();
        if (name != null && Files.isRegularFile(entry)) {
          tokens.add(name.toString());
        }
      }
    } catch (IOException e) {
      // Fail closed: an unreadable channel records no confirmations, so held
      // commands stay held rather than being released on an I/O error.
      return Set.of();
    }
    return tokens;
  }

  @Override
  public void discard(String token) {
    try {
      Files.deleteIfExists(directory.resolve(token));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot discard confirmation " + token, e);
    }
  }

  @Override
  public void clear() {
    for (String token : recorded()) {
      discard(token);
    }
  }

  /** The directory backing this channel, for the operator-facing hint text. */
  public Path directory() {
    return directory;
  }
}
