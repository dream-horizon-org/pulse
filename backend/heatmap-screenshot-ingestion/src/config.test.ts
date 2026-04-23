import { describe, expect, it } from "vitest";

import { resolveRedisUrlFromEnv } from "./config";

describe("resolveRedisUrlFromEnv", () => {
  it("prefers REDIS_URL when set", () => {
    expect(
      resolveRedisUrlFromEnv({
        REDIS_URL: "redis://custom:6380",
        REDIS_HOST: "ignored",
        REDIS_PORT: "9999",
      }),
    ).toBe("redis://custom:6380");
  });

  it("builds from REDIS_HOST and REDIS_PORT", () => {
    expect(
      resolveRedisUrlFromEnv({
        REDIS_HOST: "host.docker.internal",
        REDIS_PORT: "6379",
      }),
    ).toBe("redis://host.docker.internal:6379");
  });

  it("returns empty when no URL and no host", () => {
    expect(resolveRedisUrlFromEnv({})).toBe("");
  });
});
