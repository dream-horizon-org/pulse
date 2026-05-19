#!/usr/bin/env node
/**
 * Resolves a repo-relative file path to an instrumentation id using
 * .cursor/skills/web-otel-spec-implementation-audit/audit-index.json
 * resolve_file_to_instrumentation rules (first match wins).
 */
import fs from "node:fs";
import path from "node:path";

const repoRoot = process.argv[2];
const filePath = process.argv[3];
if (!repoRoot || !filePath) {
  process.exit(2);
}

const indexPath = path.join(
  repoRoot,
  ".cursor/skills/web-otel-spec-implementation-audit/audit-index.json",
);
const raw = fs.readFileSync(indexPath, "utf8");
const index = JSON.parse(raw);
const rules = index.resolve_file_to_instrumentation || [];

for (const rule of rules) {
  if (rule.match === "prefix") {
    const p = rule.value;
    let ok = false;
    if (p.endsWith("/")) {
      ok = filePath.startsWith(p);
    } else {
      ok = filePath === p || filePath.startsWith(`${p}/`);
    }
    if (ok) {
      process.stdout.write(rule.id);
      process.exit(0);
    }
  } else if (rule.match === "regex") {
    const re = new RegExp(rule.value);
    if (re.test(filePath)) {
      process.stdout.write(rule.id);
      process.exit(0);
    }
  }
}
process.exit(1);
