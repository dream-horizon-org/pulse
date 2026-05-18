/**
 * Fetches tool definitions from built pulse-mcp via MCP (initialize + tools/list)
 * and writes evals/promptfoo/tools.generated.yaml for Promptfoo Gemini tools.
 *
 * Requires: dist/index.js (yarn build), PULSE_BASE_URL, PULSE_API_KEY
 */
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import { stringify as stringifyYaml } from "yaml";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.join(__dirname, "..");
const serverEntry = path.join(repoRoot, "dist", "index.js");
const outFile = path.join(repoRoot, "evals", "promptfoo", "tools.generated.yaml");

function fail(msg) {
  process.stderr.write(`${msg}\n`);
  process.exit(1);
}

function normalizeParameters(inputSchema) {
  if (inputSchema && typeof inputSchema === "object" && !Array.isArray(inputSchema)) {
    return inputSchema;
  }
  return { type: "object", properties: {} };
}

async function main() {
  if (!fs.existsSync(serverEntry)) {
    fail(`Missing ${serverEntry}. Run yarn build first.`);
  }

  const baseUrl = process.env.PULSE_BASE_URL;
  const apiKey = process.env.PULSE_API_KEY;
  if (!baseUrl?.trim()) fail("PULSE_BASE_URL is required.");
  if (!apiKey?.trim()) fail("PULSE_API_KEY is required.");

  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [serverEntry],
    cwd: repoRoot,
    stderr: "inherit",
    env: {
      ...process.env,
      PULSE_BASE_URL: baseUrl.trim(),
      PULSE_API_KEY: apiKey.trim(),
    },
  });

  const client = new Client(
    { name: "pulse-mcp-promptfoo-codegen", version: "0.1.0" },
    { capabilities: {} },
  );

  try {
    await client.connect(transport);
    const { tools } = await client.listTools();
    tools.sort((a, b) => a.name.localeCompare(b.name));

    const openaiTools = tools.map((t) => ({
      type: "function",
      function: {
        name: t.name,
        description: typeof t.description === "string" ? t.description : "",
        parameters: normalizeParameters(t.inputSchema),
      },
    }));

    const yamlDoc = `# GENERATED FILE — do not edit.
# Regenerate: yarn generate:promptfoo-tools (after yarn build)

`;

    const body = stringifyYaml(openaiTools, {
      lineWidth: 0,
      defaultStringType: "QUOTE_DOUBLE",
      defaultKeyType: "PLAIN",
    });

    fs.mkdirSync(path.dirname(outFile), { recursive: true });
    fs.writeFileSync(outFile, yamlDoc + body, "utf8");

    process.stderr.write(
      `Wrote ${openaiTools.length} tools to evals/promptfoo/tools.generated.yaml\n`,
    );
  } finally {
    await transport.close();
  }
}

try {
  await main();
} catch (err) {
  const msg =
    err instanceof Error
      ? `${err.name}: ${err.message}`
      : typeof err === "string"
        ? err
        : JSON.stringify(err);
  process.stderr.write(`generate-promptfoo-tools failed: ${msg}\n`);
  process.exit(1);
}
