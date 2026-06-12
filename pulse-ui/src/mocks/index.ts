/**
 * Mock Server Module
 *
 * This module provides mock responses for all API endpoints.
 * Can be easily removed by setting REACT_APP_USE_MOCK_SERVER=false
 * and removing the mocks directory.
 */

export { MockServer } from "./MockServer";
export { MockResponseGenerator } from "./MockResponseGenerator";
export { MockDataStore } from "./MockDataStore";
export { MockConfigManager } from "./MockConfig";

// Re-export all mock response types
export * from "./types";
export * from "./responses";
