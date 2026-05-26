# Shared MySQL fragments

## `mysql-default-project-interactions.sql`

Single canonical seed for **`default-project` critical interactions** (local dev + deploy Docker only — **`backend/db/prod/mysql/mysql-init.sql` does not load this**).

| Block | Contents |
| ----- | -------- |
| `interaction_id` **1–5** | Ecommerce / web demo flows; matches `pulse-web-otel/examples/ecommerce-demo/public/interaction-config.mock.json` |
| **BasicInteraction** + **FullShopping** | Legacy samples (auto ids **6–7**) |
| `interaction_id` **100–116** | INT-P / lottery-demo + SDK auto-events |
| **Next.js demo** (ids **201–554** band + **501–502**, **509**, **541**, **544–545**, **551**, **554**, etc.) | Matches `pulse-web-otel/examples/nextjs-demo/e2e/nextjs-demo.spec.ts` (see file header for ID map) |
| `interaction_id` **600–610** | **pulse-ui** RUM journeys (`Pulse.trackEvent`); see `pulse-ui/src/pulse-web-rum/` |

Single **`INSERT`** batch for INT-P **100–116** continues with Next.js rows through **541**, then **pulse-ui 600–610**; ends with **`ON DUPLICATE KEY UPDATE`** and **`ALTER TABLE interaction AUTO_INCREMENT = 620`**.

### Usage

- **`deploy/db/mysql-init.sql`** and **`backend/db/dev/mysql/mysql-init.sql`** contain  
  `SOURCE /docker-entrypoint-initdb.d/includes/mysql-default-project-interactions.sql;`  
  Do not paste this SQL into those files.
- **`backend/db/prod/mysql/mysql-init.sql`** does **not** run that `SOURCE`; prod DBs are not pre-seeded with these demo interactions.
- **Docker**: mount this file at that path. See `deploy/docker-compose.yml` and `deploy/scripts/start.sh`.
- **Manual `mysql`**: use the same layout for dev/deploy, or run this file in a second step after the main init; adjust `SOURCE` if paths differ on the host.
