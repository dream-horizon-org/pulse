export interface ParsedUA {
  browserName: string;
  browserVersion: string;
  osName: string;
  osVersion: string;
  deviceType: "desktop" | "mobile" | "tablet";
}
