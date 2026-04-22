/**
 * Mock Server Configuration
 *
 * Centralized configuration for the mock server
 */

import { MockConfig } from "./types";

export class MockConfigManager {
  private static instance: MockConfigManager;
  private config: MockConfig;

  private constructor() {
    // Mock server is opt-in only: requires REACT_APP_USE_MOCK_SERVER=true regardless of NODE_ENV.
    // This prevents mocks from silently activating in any environment (dev or otherwise) when
    // the env var is absent or unset.
    const mockServerEnabled = process.env.REACT_APP_USE_MOCK_SERVER === "true";
    const isDevelopment = process.env.NODE_ENV === "development";

    this.config = {
      enabled: mockServerEnabled,
      delay: parseInt(process.env.REACT_APP_MOCK_DELAY || "500"),
      errorRate: parseFloat(process.env.REACT_APP_MOCK_ERROR_RATE || "0.1"),
      enableLogging:
        process.env.REACT_APP_MOCK_LOGGING === "true" || mockServerEnabled,
    };
  }

  static getInstance(): MockConfigManager {
    if (!MockConfigManager.instance) {
      MockConfigManager.instance = new MockConfigManager();
    }
    return MockConfigManager.instance;
  }

  getConfig(): MockConfig {
    return { ...this.config };
  }

  updateConfig(updates: Partial<MockConfig>): void {
    this.config = { ...this.config, ...updates };
  }

  isEnabled(): boolean {
    return this.config.enabled;
  }

  shouldSimulateError(): boolean {
    return Math.random() < this.config.errorRate;
  }

  getDelay(): number {
    return this.config.delay;
  }

  shouldLog(): boolean {
    return this.config.enableLogging;
  }
}
