import { RCA_TYPE } from "../screens/CriticalInteractionDetails/components/RootCause/RootCause.constants";

const PULSE_SERVER_ENV_KEY = "REACT_APP_PULSE_SERVER_URL" as const;

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