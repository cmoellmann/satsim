package org.satsim.mcp;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import org.satsim.cag.CommandAuthorizationGate;
import org.satsim.cag.FileConfirmationChannel;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Entry point: MCP server over stdio (the MCP client spawns this process),
 * simulator attached via the ICD §8.1/§8.2 web API. Stdout is the MCP
 * channel — all diagnostics go to stderr.
 */
public final class GatewayMain {

  private GatewayMain() {
  }

  public static void main(String[] args) throws Exception {
    GatewayConfig config = GatewayConfig.parse(args);
    ObjectMapper json = new ObjectMapper();
    String icdText = Files.readString(config.icdPath());

    TmLog tmLog = new TmLog(1024);
    try (WebApiLink link = new RestWsLink(config.baseUrl(), json);
        OpsLog opsLog = new OpsLog(config.opsLogPath(), json)) {
      // Connect the frame stream before serving MCP: the §8.2 on-connect
      // time frame seeds the OBT resource.
      link.start(tmLog::accept);
      // The gate clears the confirmation directory as it is built: no
      // confirmation recorded before this session may release anything
      // (ICD §8.4, [SIM-REQ-CAG-005]).
      CommandAuthorizationGate gate = new CommandAuthorizationGate(
          config::allows, new FileConfirmationChannel(config.confirmDirPath()));
      Gateway gateway = new Gateway(config, link, tmLog, new Authority(config), gate,
          opsLog, icdText, json);
      McpSyncServer server = gateway.buildServer(
          new StdioServerTransportProvider(new JacksonMcpJsonMapper(json)));
      System.err.println("satsim-mcp-gateway serving stdio; simulator at "
          + config.baseUrl()
          + System.lineSeparator()
          + "  command authorization gate active; confirmations are recorded in "
          + config.confirmDirPath().toAbsolutePath()
          + " and cannot be recorded through MCP");
      try {
        new CountDownLatch(1).await(); // lives until the MCP client ends the process
      } finally {
        server.closeGracefully();
      }
    }
  }
}
