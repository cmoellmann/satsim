package org.satsim.cag;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The operator's side of the confirmation channel: records a confirmation for
 * one held telecommand.
 *
 * <pre>
 *   java -cp ops-cag.jar org.satsim.cag.CagConfirm &lt;confirm-dir&gt; &lt;token&gt;
 * </pre>
 *
 * <p>Run by a human, at a terminal, for a token the gate reported. It writes
 * one file named by the token; the gate honours it once, for the command that
 * token was issued for, and only while that command is still held.
 *
 * <p>This is deliberately not an MCP tool — see {@link FileConfirmationChannel}
 * for why that distinction is the point of the increment rather than an
 * implementation detail.
 */
public final class CagConfirm {

  private CagConfirm() {
  }

  /** Command-line entry point. */
  public static void main(String[] args) throws IOException {
    System.exit(run(args, System.out, System.err));
  }

  /**
   * Records the confirmation. Exposed so the validation suite drives the real
   * operator tool rather than reaching into gateway internals to fake a
   * confirmation — the channel under test is the one an operator actually uses.
   *
   * @param args {@code <confirm-dir> <token>}
   * @param out where to report success
   * @param err where to report a usage error
   * @return process exit code: 0 recorded, 2 on a usage error
   */
  public static int run(String[] args, PrintStream out, PrintStream err) throws IOException {
    if (args.length != 2 || args[0].isBlank() || args[1].isBlank()) {
      err.println("usage: CagConfirm <confirm-dir> <token>");
      err.println("  records a confirmation for one telecommand held by the"
          + " Command Authorization Gate");
      return 2;
    }
    Path directory = Path.of(args[0]);
    String token = args[1].trim();
    if (token.contains("/") || token.contains("\\") || token.contains("..")) {
      err.println("refusing a token that is not a plain file name: " + token);
      return 2;
    }
    Files.createDirectories(directory);
    Files.writeString(directory.resolve(token),
        "confirmed " + token + System.lineSeparator(), StandardCharsets.UTF_8);
    out.println("confirmation recorded: " + token);
    out.println("re-submit the telecommand to have the gate forward it once");
    return 0;
  }
}
