package org.satsim.cag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the file-backed confirmation channel and the operator CLI
 * that writes to it (untraced, engineering hygiene per SDP §5).
 */
class FileConfirmationChannelTest {

  @TempDir
  Path dir;

  @Test
  void createsItsDirectoryAndStartsEmpty() {
    Path nested = dir.resolve("does").resolve("not").resolve("exist");
    FileConfirmationChannel channel = new FileConfirmationChannel(nested);
    assertTrue(Files.isDirectory(nested));
    assertEquals(Set.of(), channel.recorded());
  }

  @Test
  void roundTripsThroughTheOperatorCli() throws Exception {
    FileConfirmationChannel channel = new FileConfirmationChannel(dir);
    assertEquals(0, confirm("H1-abcdef12"));
    assertEquals(Set.of("H1-abcdef12"), channel.recorded());

    channel.discard("H1-abcdef12");
    assertEquals(Set.of(), channel.recorded());
    // Idempotent: discarding twice is not an error.
    channel.discard("H1-abcdef12");
  }

  @Test
  void clearRemovesEveryRecordedConfirmation() throws Exception {
    FileConfirmationChannel channel = new FileConfirmationChannel(dir);
    confirm("H1-aaaaaaaa");
    confirm("H2-bbbbbbbb");
    assertEquals(2, channel.recorded().size());

    channel.clear();
    assertEquals(Set.of(), channel.recorded());
  }

  @Test
  void ignoresSubdirectories() throws Exception {
    Files.createDirectory(dir.resolve("not-a-token"));
    confirm("H1-cccccccc");
    assertEquals(Set.of("H1-cccccccc"), new FileConfirmationChannel(dir).recorded());
  }

  @Test
  void cliRejectsUsageErrorsAndPathTraversal() throws Exception {
    assertEquals(2, confirm());
    assertEquals(2, confirm(dir.toString(), " "));
    assertEquals(2, confirm(dir.toString(), "../escape"));
    assertEquals(2, confirm(dir.toString(), "sub/token"));
    assertEquals(Set.of(), new FileConfirmationChannel(dir).recorded());
  }

  @Test
  void cliWritesAReadableRecord() throws Exception {
    confirm("H7-12345678");
    assertTrue(Files.readString(dir.resolve("H7-12345678"), StandardCharsets.UTF_8)
        .contains("H7-12345678"));
  }

  @Test
  void cliReportsWhatTheOperatorMustDoNext() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream stream = new PrintStream(out, true, StandardCharsets.UTF_8);
    assertEquals(0, CagConfirm.run(new String[] {dir.toString(), "H1-eeeeeeee"}, stream, stream));
    String printed = out.toString(StandardCharsets.UTF_8);
    assertTrue(printed.contains("H1-eeeeeeee"), printed);
    assertTrue(printed.contains("re-submit"), printed);
    assertFalse(printed.isBlank());
  }

  private int confirm(String... args) throws Exception {
    PrintStream sink = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
    String[] full = args.length == 1 ? new String[] {dir.toString(), args[0]} : args;
    return CagConfirm.run(full, sink, sink);
  }
}
