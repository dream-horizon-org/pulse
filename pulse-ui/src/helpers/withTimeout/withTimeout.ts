import { COMMON_CONSTANTS, REQUEST_TIMEOUT } from "../../constants";

export const withTimeout = async <T>(
  opertion: () => Promise<T>,
  timeout: number = REQUEST_TIMEOUT,
) => {
  const timeoutPromise = new Promise<T>((resolve, reject) => {
    setTimeout(() => {
      reject(new Error(COMMON_CONSTANTS.REQUEST_TIMEOUT_MESSAGE));
    }, timeout);
  });

  return Promise.race([opertion(), timeoutPromise]);
};
