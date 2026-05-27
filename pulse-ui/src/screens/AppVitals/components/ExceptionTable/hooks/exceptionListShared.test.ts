import {
  buildExceptionTitleOrVersionSearchFilter,
  flattenExceptionListPages,
  mapExceptionRowsToIssues,
} from "./exceptionListShared";

import type { DataQueryResponse } from "../../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
jest.mock("../../TrendGraphWithData/helpers/trendDataHelpers", () => ({
  buildCommonFilters: jest.fn(() => []),
}));


describe("exceptionListShared", () => {
  describe("buildExceptionTitleOrVersionSearchFilter", () => {
    it("returns null for empty search query", () => {
      expect(buildExceptionTitleOrVersionSearchFilter("")).toBeNull();
      expect(buildExceptionTitleOrVersionSearchFilter("   ")).toBeNull();
      expect(buildExceptionTitleOrVersionSearchFilter(undefined)).toBeNull();
    });

    it("builds ADDITIONAL filter with escaped single quotes", () => {
      const filter = buildExceptionTitleOrVersionSearchFilter("O'Reilly");

      expect(filter).toEqual({
        field: "Additional",
        operator: "ADDITIONAL",
        value: [
          "(Title ILIKE '%O''Reilly%' OR AppVersion ILIKE '%O''Reilly%')",
        ],
      });
    });
  });

  describe("flattenExceptionListPages", () => {
    it("keeps fields from first successful page and ignores errored rows", () => {
      const ok1: DataQueryResponse = {
        fields: ["group_id", "title"],
        rows: [["g1", "Error A"]],
      };
      const errored: DataQueryResponse = {
        fields: ["group_id", "title"],
        rows: [["g2", "Error B"]],
      };
      const ok2: DataQueryResponse = {
        fields: ["group_id", "title"],
        rows: [["g3", "Error C"]],
      };

      const result = flattenExceptionListPages([
        { data: ok1 },
        { data: errored, error: new Error("request failed") },
        { data: ok2 },
      ]);

      expect(result.fields).toEqual(["group_id", "title"]);
      expect(result.rows).toEqual([
        ["g1", "Error A"],
        ["g3", "Error C"],
      ]);
    });
  });

  describe("mapExceptionRowsToIssues", () => {
    it("maps crash rows with timestamp map and rounded numeric values", () => {
      const fields = [
        "group_id",
        "title",
        "error_type",
        "app_versions",
        "occurrences",
        "affected_users",
      ];
      const rows = [
        [
          "gid-1",
          "Crash title",
          "java.lang.RuntimeException",
          "1.0.0, 1.0.1",
          "3.8",
          "1.2",
        ],
      ];
      const timestamps = new Map([
        [
          "gid-1",
          { firstSeen: "2026-05-20 10:00:00", lastSeen: "2026-05-21 10:00:00" },
        ],
      ]);

      const issues = mapExceptionRowsToIssues(
        rows,
        fields,
        "crash",
        timestamps,
      );

      expect(issues).toHaveLength(1);
      expect(issues[0]).toMatchObject({
        id: "gid-1",
        title: "Crash title",
        occurrences: 4,
        affectedUsers: 1,
        firstSeen: "2026-05-20 10:00:00",
        lastSeen: "2026-05-21 10:00:00",
      });
    });
  });
});
