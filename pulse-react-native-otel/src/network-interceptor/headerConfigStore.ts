export type NetworkHeaderConfig = {
  requestHeaders?: string[];
  responseHeaders?: string[];
};

let headerConfig: NetworkHeaderConfig = {
  requestHeaders: [],
  responseHeaders: [],
};

export function getHeaderConfig(): NetworkHeaderConfig {
  return headerConfig;
}

export function setHeaderConfig(config?: NetworkHeaderConfig): void {
  if (config) {
    headerConfig = {
      requestHeaders: config.requestHeaders ?? [],
      responseHeaders: config.responseHeaders ?? [],
    };
  } else {
    headerConfig = {
      requestHeaders: [],
      responseHeaders: [],
    };
  }
}
