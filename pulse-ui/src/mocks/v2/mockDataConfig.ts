/**
 * Mock Data Configuration V2
 *
 * Configuration for data ranges, Indian regions, devices, and networks
 */

export interface MockDataConfigV2 {
  regions: string[];
  devices: string[];
  osVersions: string[];
  networkProviders: string[];
  appVersions: string[];
  platforms: string[];
  connectionTypes: string[];

  // Value ranges
  ranges: {
    apdex: { min: number; max: number };
    successCount: { min: number; max: number };
    errorCount: { min: number; max: number };
    frozenFrame: { min: number; max: number };
    anr: { min: number; max: number };
    crash: { min: number; max: number };
    userExcellent: { min: number; max: number };
    userGood: { min: number; max: number };
    userAverage: { min: number; max: number };
    userPoor: { min: number; max: number };
    latencyP50: { min: number; max: number };
    latencyP95: { min: number; max: number };
    latencyP99: { min: number; max: number };
    dailyUsers: { min: number; max: number };
  };
}

export const mockDataConfig: MockDataConfigV2 = {
  // Indian regions (states)
  regions: [
    "Maharashtra", // Mumbai, Pune
    "Karnataka", // Bangalore
    "Delhi", // NCR region
    "Tamil Nadu", // Chennai
    "Uttar Pradesh", // Lucknow, Noida
    "West Bengal", // Kolkata
    "Gujarat", // Ahmedabad
    "Rajasthan", // Jaipur
  ],

  // Popular Indian devices
  devices: [
    "Samsung Galaxy S21",
    "iPhone 13 Pro",
    "Redmi Note 11", // Xiaomi - very popular in India
    "OnePlus Nord 2", // OnePlus - Indian market focus
    "Realme 9 Pro", // Realme - major Indian brand
    "Vivo V23", // Vivo - popular in India
    "Oppo Reno 7", // Oppo - major player
    "iPhone 14",
    "Samsung Galaxy A53",
    "Poco X4 Pro", // Xiaomi sub-brand
  ],

  // OS versions
  osVersions: [
    "Android 13",
    "Android 12",
    "Android 11",
    "iOS 16",
    "iOS 15",
    "iOS 14",
  ],

  // Indian network providers
  networkProviders: [
    "Jio", // Reliance Jio - largest
    "Airtel", // Bharti Airtel
    "Vi (Vodafone Idea)", // Vodafone Idea merger
    "BSNL", // State-owned
    "Aircel", // Legacy provider
    "Other", // Small regional providers
  ],

  // App versions
  appVersions: ["1.0.0", "1.1.0", "1.2.0", "2.0.0", "2.1.0"],

  // Platforms
  platforms: ["Android", "iOS"],

  // Connection types
  connectionTypes: ["WiFi", "4G", "5G", "3G"],

  // Value ranges for metrics
  ranges: {
    apdex: { min: 0.8, max: 0.95 },
    successCount: { min: 80, max: 150 },
    errorCount: { min: 10, max: 50 },
    frozenFrame: { min: 5, max: 25 },
    anr: { min: 3, max: 15 },
    crash: { min: 2, max: 12 },
    userExcellent: { min: 20, max: 50 },
    userGood: { min: 50, max: 100 },
    userAverage: { min: 0, max: 20 },
    userPoor: { min: 10, max: 40 },
    latencyP50: { min: 200, max: 800 },
    latencyP95: { min: 800, max: 2000 },
    latencyP99: { min: 1500, max: 4000 },
    dailyUsers: { min: 100000, max: 150000 },
  },
};
