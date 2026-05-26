import { getCookies } from "../helpers/cookies";
import { getProjectIdFromPath } from "../helpers/projectContext/projectContext";
import {
  readPulseEventContext,
  withPulseEventContext,
} from "./pulseEventContext";
import { PULSE_RUM_COOKIE_KEYS } from "./pulseRumConstants";

jest.mock("../helpers/cookies");
jest.mock("../helpers/projectContext/projectContext");

const mockGetCookies = getCookies as jest.Mock;
const mockGetProjectIdFromPath = getProjectIdFromPath as jest.Mock;

describe("readPulseEventContext", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    sessionStorage.clear();
    mockGetCookies.mockReturnValue(undefined);
    mockGetProjectIdFromPath.mockReturnValue(null);
    Object.defineProperty(window, "location", {
      value: { pathname: "/" },
      writable: true,
    });
  });

  it("prefers session tenant and project over cookies and path", () => {
    sessionStorage.setItem(
      "pulse_tenant_context",
      JSON.stringify({ tenantId: "tenant-session" }),
    );
    sessionStorage.setItem(
      "pulse_project_context",
      JSON.stringify({ projectId: "project-session" }),
    );
    mockGetCookies.mockImplementation((key: string) =>
      key === PULSE_RUM_COOKIE_KEYS.TENANT_ID ? "tenant-cookie" : undefined,
    );
    mockGetProjectIdFromPath.mockReturnValue("project-path");

    expect(readPulseEventContext()).toEqual({
      tenant_id: "tenant-session",
      project_id: "project-session",
    });
  });

  it("falls back to cookie tenant and path project", () => {
    mockGetCookies.mockImplementation((key: string) =>
      key === PULSE_RUM_COOKIE_KEYS.TENANT_ID ? "tenant-cookie" : undefined,
    );
    mockGetProjectIdFromPath.mockReturnValue("project-from-path");
    Object.defineProperty(window, "location", {
      value: { pathname: "/projects/project-from-path" },
      writable: true,
    });

    expect(readPulseEventContext()).toEqual({
      tenant_id: "tenant-cookie",
      project_id: "project-from-path",
    });
  });

  it("ignores cookie tenant when value is the string undefined", () => {
    mockGetCookies.mockImplementation((key: string) =>
      key === PULSE_RUM_COOKIE_KEYS.TENANT_ID ? "undefined" : undefined,
    );

    expect(readPulseEventContext().tenant_id).toBeUndefined();
  });
});

describe("withPulseEventContext", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    sessionStorage.clear();
    mockGetCookies.mockReturnValue(undefined);
    mockGetProjectIdFromPath.mockReturnValue("default-project");
    Object.defineProperty(window, "location", {
      value: { pathname: "/projects/default-project" },
      writable: true,
    });
  });

  it("merges defaults first and lets explicit attrs override project_id", () => {
    const merged = withPulseEventContext({
      project_id: "explicit-project",
      source: "test",
    });

    expect(merged).toEqual({
      tenant_id: undefined,
      project_id: "explicit-project",
      source: "test",
    });
  });
});
