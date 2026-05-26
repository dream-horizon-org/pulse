import { RCA_TYPE } from "../screens/CriticalInteractionDetails/components/RootCause/RootCause.constants";

const PULSE_SERVER_ENV_KEY = "REACT_APP_PULSE_SERVER_URL" as const;
const FUNNEL_RCA_SERVER_ENV_KEY =
  "REACT_APP_FUNNEL_RCA_PULSE_SERVER_URL" as const;

const ERROR_MESSAGES = {
  MISSING_API_BASE_URL: `Missing ${PULSE_SERVER_ENV_KEY}. Set it in your environment (e.g. .env).`,
} as const;

/**
 * Returns the Pulse server base URL from {@link PULSE_SERVER_ENV_KEY}.
 * @throws Error when the variable is unset or blank after trim.
 */
export const getApiBaseUrl = (): string => {
  const rawValue = process.env.REACT_APP_PULSE_SERVER_URL ?? "";
  const trimmedBaseUrl = rawValue.trim();
  const isBaseUrlMissing = trimmedBaseUrl === "";

  if (isBaseUrlMissing) {
    throw new Error(ERROR_MESSAGES.MISSING_API_BASE_URL);
  }

  return trimmedBaseUrl;
};

/**
 * Base URL for funnel drop-off + RCA APIs added on {@code feat/funnel-rca}.
 * Uses {@link FUNNEL_RCA_SERVER_ENV_KEY} when set so list/detail/compute can stay on prod.
 */
export const getFunnelFeatureApiBaseUrl = (): string => {
  const override = (
    process.env.REACT_APP_FUNNEL_RCA_PULSE_SERVER_URL ?? ""
  ).trim();
  return override !== "" ? override : getApiBaseUrl();
};

/**
 * Base URL for async RCA report routes (POST /v1/ai/rca/report, job poll).
 * FUNNEL uses {@link getFunnelFeatureApiBaseUrl} so the rest of the app can stay on prod.
 */
export const getRcaApiBaseUrl = (
  rcaType: string = RCA_TYPE.INTERACTION,
): string => {
  if (rcaType !== RCA_TYPE.FUNNEL) {
    return getApiBaseUrl();
  }
  return getFunnelFeatureApiBaseUrl();
};
