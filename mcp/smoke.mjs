// Starts the server over stdio and lists its tools. With credentials set in
// mcp/.env it also reads (never writes) your plan and weekly body weight.
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

const client = new Client({ name: "smoke", version: "0" });
await client.connect(new StdioClientTransport({ command: process.execPath, args: ["--no-warnings", "server.mjs"], cwd: import.meta.dirname }));
const { tools } = await client.listTools();
console.log("tools:", tools.map((t) => t.name).join(", "));
for (const name of ["get_plan", "get_body_weight"]) {
  const result = await client.callTool({ name, arguments: {} });
  const text = result.content[0].text;
  console.log(`${name}:`, result.isError ? `error: ${text}` : `${text.slice(0, 300)}…`);
}
await client.close();
