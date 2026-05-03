import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { PulseLogLevel } from "../pulse-log-level";
import { PulseWebLogger } from "../pulse-web-logger";

describe("PulseWebLogger", () => {
  beforeEach(() => {
    PulseWebLogger.resetForTesting();
    vi.spyOn(console, "debug").mockImplementation(() => {});
    vi.spyOn(console, "log").mockImplementation(() => {});
    vi.spyOn(console, "warn").mockImplementation(() => {});
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    PulseWebLogger.resetForTesting();
  });

  it("NONE suppresses verbose, debug, info, warn, and error (Android-style)", () => {
    PulseWebLogger.setLevel(PulseLogLevel.NONE);
    PulseWebLogger.verbose("v");
    PulseWebLogger.debug("d");
    PulseWebLogger.info("i");
    PulseWebLogger.warn("w");
    PulseWebLogger.error("e");
    expect(console.debug).not.toHaveBeenCalled();
    expect(console.log).not.toHaveBeenCalled();
    expect(console.warn).not.toHaveBeenCalled();
    expect(console.error).not.toHaveBeenCalled();
  });

  it("DEBUG emits debug and info (not verbose)", () => {
    PulseWebLogger.setLevel(PulseLogLevel.DEBUG);
    PulseWebLogger.verbose("v");
    PulseWebLogger.debug("d");
    PulseWebLogger.info("i");
    expect(console.debug).toHaveBeenCalledTimes(1);
    expect(console.debug).toHaveBeenCalledWith(expect.stringContaining("d"));
    expect(console.log).toHaveBeenCalledTimes(1);
    expect(console.log).toHaveBeenCalledWith(expect.stringContaining("i"));
  });

  it("ERROR emits only error", () => {
    PulseWebLogger.setLevel(PulseLogLevel.ERROR);
    PulseWebLogger.warn("w");
    PulseWebLogger.error("e");
    expect(console.warn).not.toHaveBeenCalled();
    expect(console.error).toHaveBeenCalledTimes(1);
  });

  it("VERBOSE emits verbose", () => {
    PulseWebLogger.setLevel(PulseLogLevel.VERBOSE);
    PulseWebLogger.verbose("v");
    expect(console.debug).toHaveBeenCalled();
  });
});
