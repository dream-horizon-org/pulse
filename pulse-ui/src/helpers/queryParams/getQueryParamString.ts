export const getQueryParamString = <T>(params: T | null = null): string => {
  return params ? `?${new URLSearchParams(params)}` : "";
};

export const removeUndefinedValues = (obj: Record<string, any>) => {
  return Object.keys(obj).reduce(
    (acc, key) => {
      if (obj[key] !== undefined) {
        acc[key] = obj[key];
      }
      return acc;
    },
    {} as Record<string, any>,
  );
};

export const removeUndefinedOrNullValues = (obj: Record<string, any>) => {
  return Object.keys(obj).reduce(
    (acc, key) => {
      if (obj[key] !== undefined && obj[key] !== null) {
        acc[key] = obj[key];
      }
      return acc;
    },
    {} as Record<string, any>,
  );
};

export const removeEmptyValues = (obj: Record<string, any>) => {
  return Object.keys(obj).reduce(
    (acc, key) => {
      if (obj[key] !== undefined && obj[key] !== null && obj[key] !== "") {
        acc[key] = obj[key];
      }
      return acc;
    },
    {} as Record<string, any>,
  );
};
