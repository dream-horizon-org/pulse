import axios from "axios";
import fs from "fs";
import os from "os";
import path from "path";

export interface Credentials {
  accessToken: string;
  refreshToken: string;
}

const CREDS_DIR = path.join(os.homedir(), ".pulse-mcp");
const CREDS_FILE = path.join(CREDS_DIR, "credentials.json");

export function loadCredentials(): Credentials {
  if (fs.existsSync(CREDS_FILE)) {
    const raw = fs.readFileSync(CREDS_FILE, "utf8");
    const creds = JSON.parse(raw) as Credentials;
    if (creds.accessToken && creds.refreshToken) return creds;
  }

  throw new Error(
    "No Pulse credentials in ~/.pulse-mcp/credentials.json (missing or invalid). " +
      "Startup should exchange PULSE_API_KEY first; check server stderr for exchange errors.",
  );
}

export function saveCredentials(creds: Credentials): void {
  fs.mkdirSync(CREDS_DIR, { recursive: true });
  fs.writeFileSync(CREDS_FILE, JSON.stringify(creds, null, 2), { mode: 0o600 });
}

export async function exchangeApiKeyForTokens(
  baseUrl: string,
  apiKey: string,
): Promise<Credentials> {
  const resp = await axios.post<{
    data: { accessToken: string; refreshToken: string };
  }>(`${baseUrl}/v1/auth/api-key/exchange`, { apiKey });
  return {
    accessToken: resp.data.data.accessToken,
    refreshToken: resp.data.data.refreshToken,
  };
}
