export type dataDataRequestBodyMakerType = {
  useCaseId?: string;
  startTime?: string;
  endTime?: string;
  appVersion?: string;
  platform?: string;
  osVersion?: string;
  networkProvider?: string;
  deviceModel?: string;
  state?: string;
};

export function graphDataRequestBodyMaker(
  requestBody: dataDataRequestBodyMakerType,
): Partial<dataDataRequestBodyMakerType> {
  if (!requestBody) {
    return {};
  }

  const result = Object.keys(requestBody).reduce((acc, key) => {
    const typedKey = key as keyof dataDataRequestBodyMakerType;
    if (
      typedKey &&
      requestBody[typedKey] !== null &&
      requestBody[typedKey] !== undefined &&
      requestBody[typedKey] !== "" &&
      requestBody[typedKey] !== "All"
    ) {
      acc[typedKey] = requestBody[typedKey];
    }
    return acc;
  }, {} as dataDataRequestBodyMakerType);

  // Rebuild the object without null values
  return result;
}

export const getMode = (filters: Partial<dataDataRequestBodyMakerType>) => {
  if (
    filters.appVersion ||
    filters.deviceModel ||
    filters.networkProvider ||
    filters.osVersion ||
    filters.platform ||
    filters.state
  ) {
    return "Live";
  }

  return "Cache";
};
