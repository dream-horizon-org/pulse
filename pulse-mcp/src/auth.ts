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
  // Env vars take precedence
  if (process.env.PULSE_ACCESS_TOKEN && process.env.PULSE_REFRESH_TOKEN) {
    return {
      accessToken: process.env.PULSE_ACCESS_TOKEN,
      refreshToken: process.env.PULSE_REFRESH_TOKEN,
    };
  }

  if (fs.existsSync(CREDS_FILE)) {
    const raw = fs.readFileSync(CREDS_FILE, "utf8");
    const creds = JSON.parse(raw) as Credentials;
    if (creds.accessToken && creds.refreshToken) return creds;
  }

  throw new Error(
    "No Pulse credentials found. Set PULSE_ACCESS_TOKEN and PULSE_REFRESH_TOKEN env vars, " +
      "or run the setup script to authenticate."
  );
}

export function saveCredentials(creds: Credentials): void {
  fs.mkdirSync(CREDS_DIR, { recursive: true });
  fs.writeFileSync(CREDS_FILE, JSON.stringify(creds, null, 2), { mode: 0o600 });
}

export async function refreshAccessToken(
  baseUrl: string,
  refreshToken: string
): Promise<Credentials> {
  const resp = await axios.post<{ data: { accessToken: string; refreshToken: string } }>(
    `${baseUrl}/v1/auth/token/refresh`,
    { refreshToken }
  );
  const creds: Credentials = {
    accessToken: resp.data.data.accessToken,
    refreshToken: resp.data.data.refreshToken ?? refreshToken,
  };
  // Persist refreshed tokens if not using raw env vars
  if (!process.env.PULSE_ACCESS_TOKEN) {
    saveCredentials(creds);
  }
  return creds;
}
