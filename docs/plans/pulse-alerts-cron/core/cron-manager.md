# cron-manager

Parent: [pulse-alerts-cron](../index.md) ·
Brief: [component](../../../components/pulse-alerts-cron.md)

## 1. Purpose

Schedule and fan-out per-alert evaluations on a stable cadence. One
Vert.x periodic timer per distinct interval, with all alerts on that
interval ticking together.

## 2. Source

- `services/CronManager.java`
- Models: `models/CronTask.java`, `models/Interval.java`, `models/Group.java`
- DTOs: `dto/response/CronManagerDto.java`,
  `dto/request/{AddCronDto,UpdateCronDto,DeleteCronDto}.java`
- REST: `rest/CronController.java`

## 3. Data structures

- `ConcurrentHashMap<Integer, CopyOnWriteArrayList<CronTask>> cronGroups`
  — interval (seconds) → tasks.
- `ConcurrentHashMap<Integer, Long> timerIds` — interval → Vert.x timer
  id, for cancellation.
- `ConcurrentHashMap<String, Long> customTimerIds` — one-off / ad-hoc
  timers keyed by name.

## 4. Lifecycle

- `addCronTask(id, url, interval, projectId)` — creates group if absent,
  starts timer via `startTimerForInterval(interval)`, returns
  `Single<CronManagerDto>` with success/failure.
- `modifyCronTask(...)` — remove from old interval, add to new.
- `removeCronTask(id, interval)` — removes task; if group empties,
  cancels the timer and drops the entry.
- `startTimerForInterval` — `vertx.setPeriodic(interval * 1000, ...)`.
- `cancelTimerForInterval` — `vertx.cancelTimer(timerId)`.

## 5. Retry + timeout

Constants in `CronManager`:

- `MAX_RETRY_ATTEMPTS = 3`
- `INITIAL_RETRY_DELAY_MS = 1000` (exponential)
- `REQUEST_TIMEOUT_MS = 30_000`

## 6. Concurrency

`vertx.setPeriodic` runs callbacks on the event loop. Long work must
not block the loop — task execution uses `WebClient` async REST, never
blocking IO. `CopyOnWriteArrayList` lets the timer iterate while
add/remove mutates.

## 7. Failure modes

- pulse-server slow → 30s timeout per task, then retry chain.
- Pod restart → in-memory state lost; `DataSyncService` re-pulls
  alerts on boot to rebuild `cronGroups`.
- Clock skew is irrelevant (interval-based, not wall-clock cron).

## 8. Tests

- `src/test/java/.../models/CronTaskTest.java`
- `src/test/java/.../services/BatchSchedulerServiceTest.java`
- Need: a `CronManagerTest` to cover group lifecycle + retry. Coverage
  target 80% on changed files per repo rules.

## 9. Open items

- Add HA: external scheduling state (Redis or MySQL) so a second
  replica can take over without duplicating ticks.
- Metrics: per-interval tick count, per-alert failure count,
  retry-exhausted counter.
