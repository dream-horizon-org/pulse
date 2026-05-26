import { RCA_TYPE } from "../screens/CriticalInteractionDetails/components/RootCause/RootCause.constants";
import { getApiBaseUrl, getRcaApiBaseUrl } from "./getApiBaseUrl";

/** CRA types ProcessEnv keys as readonly; tests need a writable view. */
const env = process.env as Record<string, string | undefined>;

describe("getRcaApiBaseUrl", () => {
  const originalPulse = env.REACT_APP_PULSE_SERVER_URL;
  const originalFunnel = env.REACT_APP_FUNNEL_RCA_PULSE_SERVER_URL;

  afterEach(() => {
    env.REACT_APP_PULSE_SERVER_URL = originalPulse;
    env.REACT_APP_FUNNEL_RCA_PULSE_SERVER_URL = originalFunnel;
  });

  it("uses funnel override for FUNNEL rca type", () => {
    env.REACT_APP_PULSE_SERVER_URL = "https://pulse-server.pulse-ux.com";
    env.REACT_APP_FUNNEL_RCA_PULSE_SERVER_URL = "http://localhost:8080";

    expect(getRcaApiBaseUrl(RCA_TYPE.FUNNEL)).toBe("http://localhost:8080");
    expect(getRcaApiBaseUrl(RCA_TYPE.INTERACTION)).toBe(
      "https://pulse-server.pulse-ux.com",
    );
    expect(getApiBaseUrl()).toBe("https://pulse-server.pulse-ux.com");
  });

  it("falls back to main base when funnel override unset", () => {
    env.REACT_APP_PULSE_SERVER_URL = "https://pulse-server.pulse-ux.com";
    env.REACT_APP_FUNNEL_RCA_PULSE_SERVER_URL = "";

    expect(getRcaApiBaseUrl(RCA_TYPE.FUNNEL)).toBe(
      "https://pulse-server.pulse-ux.com",
    );
  });
});
