#!/usr/bin/env node
/**
 * Builds WEB-SDK Success Metric — Test Coverage.csv from the success-metric sheet
 * layout: topic header row (merged-style) → TC column header → TC rows (no Topic column).
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const scriptsDir = path.dirname(fileURLToPath(import.meta.url));
const docs = path.join(scriptsDir, "..");
const mappingJsonPath = path.join(scriptsDir, "e2e-tc-mapping.json");
const decisionsPath = path.join(scriptsDir, "e2e-coverage-decisions.json");
const successMetricPath = path.join(
  docs,
  "WEB-SDK Ui test List - WEB SDK Sucess metric.csv",
);
const outPath = path.join(docs, "WEB-SDK Success Metric — Test Coverage.csv");

const TC_RE = /^[A-Z][A-Z0-9-]*\d+(?:\.\d+)?$/;
const TOPIC_ROW_RE = /^Topic \d+ —/;

function parseCsv(text) {
  const rows = [];
  let row = [],
    field = "",
    inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else inQuotes = false;
      } else field += c;
    } else if (c === '"') inQuotes = true;
    else if (c === ",") {
      row.push(field);
      field = "";
    } else if (c === "\n" || c === "\r") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      row.push(field);
      field = "";
      if (row.some((x) => x !== "")) rows.push(row);
      row = [];
    } else field += c;
  }
  if (field || row.length) {
    row.push(field);
    if (row.some((x) => x !== "")) rows.push(row);
  }
  return rows;
}

function esc(fields) {
  return fields
    .map((f) => {
      const s = String(f ?? "");
      return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    })
    .join(",");
}

function normalizeTc(raw) {
  const s = (raw || "").trim();
  const m = s.match(/^([A-Z][A-Z0-9]*-\d+(?:\.\d+)?)/);
  return m ? m[1] : s;
}

function padRow(cells, width) {
  const row = [...cells];
  while (row.length < width) row.push("");
  return row.slice(0, width);
}

const tcMapping = JSON.parse(fs.readFileSync(mappingJsonPath, "utf8"));
const decisions = JSON.parse(fs.readFileSync(decisionsPath, "utf8"));
const manualRows = parseCsv(fs.readFileSync(successMetricPath, "utf8"));

const TC_HEADER = [
  "TC",
  "Scenario",
  "How to Trigger",
  "Expected Signal",
  "Status (Android)",
  "Status (iOS)",
  "Comment (success metric)",
  "Coverage",
  "E2E reference",
  "Notes",
];
const COLS = TC_HEADER.length;

function resolveEntry(tc) {
  const base = tcMapping[tc] || {
    scenario: "",
    status: "",
    e2e: "",
    notes: "",
  };
  if (decisions.manual[tc]) {
    return {
      coverage: "Manual",
      e2e: "",
      notes: decisions.manual[tc],
      scenario: base.scenario,
    };
  }
  if (decisions.covered[tc]) {
    return {
      coverage: "Automation",
      e2e: decisions.covered[tc],
      notes: base.notes || "strict ecommerce E2E (e2e:web-sdk-gates)",
      scenario: base.scenario,
    };
  }
  if (base.status === "Manual") {
    return { coverage: "Manual", e2e: "", notes: base.notes, scenario: base.scenario };
  }
  if (base.status === "Covered") {
    return {
      coverage: "Automation",
      e2e: base.e2e,
      notes: base.notes || "strict ecommerce E2E (e2e:web-sdk-gates)",
      scenario: base.scenario,
    };
  }
  return {
    coverage: "Unmapped",
    e2e: base.e2e,
    notes: base.notes,
    scenario: base.scenario,
  };
}

function topicHeaderRow(topicName) {
  return padRow([topicName], COLS);
}

function tcDataRow(r, entry) {
  return padRow(
    [
      normalizeTc(r[0]),
      r[1] || entry.scenario,
      r[2] || "",
      r[3] || "",
      r[4] || "",
      r[5] || "",
      r[6] || "",
      entry.coverage,
      entry.e2e,
      entry.notes,
    ],
    COLS,
  );
}

const seen = new Set();
const stats = { automation: 0, manual: 0, unmapped: 0, topics: 0 };
const out = [];
let currentTopic = null;

const meta = padRow(
  [
    "META",
    `Coverage sheet — topic sections match success-metric layout. Automation = e2e:web-sdk-gates. Generated ${new Date().toISOString().slice(0, 10)}.`,
    "",
    "",
    "",
    "",
    "",
    "",
    "",
    "Source: e2e-coverage-decisions.json",
  ],
  COLS,
);
out.push(esc(meta));
out.push(esc(TC_HEADER));

for (const r of manualRows) {
  const first = (r[0] || "").trim();

  if (TOPIC_ROW_RE.test(first)) {
    currentTopic = first;
    stats.topics++;
    out.push(esc(topicHeaderRow(currentTopic)));
    continue;
  }

  const tc = normalizeTc(r[0]);
  if (!TC_RE.test(tc) || seen.has(tc)) continue;
  seen.add(tc);

  if (!currentTopic) {
    currentTopic = "Uncategorized";
    out.push(esc(topicHeaderRow(currentTopic)));
  }

  const entry = resolveEntry(tc);
  if (entry.coverage === "Automation") stats.automation++;
  else if (entry.coverage === "Manual") stats.manual++;
  else stats.unmapped++;

  out.push(esc(tcDataRow(r, entry)));
}

const topic13Extras = Object.keys(decisions.manual).filter(
  (tc) => tc.startsWith("TOPIC-13") && !seen.has(tc),
);
if (topic13Extras.length > 0) {
  const topic13Name =
    "Topic 13 — Attribute Completeness & Value Assertions";
  out.push(esc(padRow([], COLS)));
  out.push(esc(topicHeaderRow(topic13Name)));
  for (const tc of topic13Extras.sort()) {
    seen.add(tc);
    stats.manual++;
    const base = tcMapping[tc] || { scenario: "", notes: "" };
    const entry = resolveEntry(tc);
    out.push(
      esc(
        padRow(
          [
            tc,
            base.scenario || tc,
            "",
            "",
            "",
            "",
            "",
            entry.coverage,
            entry.e2e,
            entry.notes,
          ],
          COLS,
        ),
      ),
    );
  }
}

fs.writeFileSync(outPath, out.join("\n") + "\n");

console.log({
  output: path.basename(outPath),
  tcRows: seen.size,
  topics: stats.topics + (topic13Extras.length ? 0 : 0),
  ...stats,
});
