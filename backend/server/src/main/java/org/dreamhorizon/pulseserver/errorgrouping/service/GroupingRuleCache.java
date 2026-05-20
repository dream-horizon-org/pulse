package org.dreamhorizon.pulseserver.errorgrouping.service;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.errorgrouping.dao.GroupingRuleDao;
import org.dreamhorizon.pulseserver.errorgrouping.dao.GroupingRuleRow;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.MaskRule;

/**
 * Caffeine-backed {@link AsyncLoadingCache} keyed by {@code projectId} that
 * materializes per-project {@link GroupingRules} bundles (regexes pre-compiled)
 * from MySQL via {@link GroupingRuleDao}.
 *
 * <p>Mirrors the builder shape used by {@code SourceMapCache} so cache
 * housekeeping (refresh / expiry / executor) follows a single convention.
 * Refresh window: 5 min. Hard expiry: 30 min. Max entries: 1000.</p>
 *
 * <p>The cached value is intentionally the <strong>DB-only</strong> view of the
 * rules. The {@code bundleId} fallback is merged per-request by
 * {@link GroupingRuleService#getRules} without mutating the cached instance.</p>
 */
@Slf4j
@Singleton
public class GroupingRuleCache {

  private static final int MAX_ENTRIES = 1000;
  private static final Duration REFRESH_AFTER_WRITE = Duration.ofMinutes(5);
  private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMinutes(30);

  private final GroupingRuleDao dao;
  private final AsyncLoadingCache<String, GroupingRules> cache;

  @Inject
  public GroupingRuleCache(Vertx vertx, GroupingRuleDao dao) {
    this.dao = dao;
    Context ctx = vertx.getOrCreateContext();
    Objects.requireNonNull(ctx, "GroupingRuleCache must be created on a Vert.x context thread");

    this.cache = Caffeine.newBuilder()
        .maximumSize(MAX_ENTRIES)
        .refreshAfterWrite(REFRESH_AFTER_WRITE)
        .expireAfterWrite(EXPIRE_AFTER_WRITE)
        .executor(cmd -> ctx.runOnContext(v -> cmd.run()))
        .recordStats()
        .buildAsync(this::loadAndBuild);
  }

  /**
   * Public accessor wrapped in an RxJava {@link Single} for the rest of the
   * codebase. Uses {@code whenComplete} (not {@code Single.fromFuture}) so the
   * event loop is never blocked.
   */
  public Single<GroupingRules> getCached(String projectId) {
    CompletableFuture<GroupingRules> fut = cache.get(projectId);
    return Single.create(emitter -> fut.whenComplete((result, throwable) -> {
      if (throwable != null) {
        emitter.onError(throwable);
      } else {
        emitter.onSuccess(result);
      }
    }));
  }

  /**
   * Caffeine cache loader. Pulls rows from MySQL and folds them into an
   * immutable {@link GroupingRules} bundle, compiling every regex once so the
   * hot path runs match-only.
   */
  private CompletableFuture<GroupingRules> loadAndBuild(String projectId, Executor executor) {
    return dao.loadRulesForProject(projectId)
        .map(rows -> buildRules(projectId, rows))
        .toCompletionStage()
        .toCompletableFuture();
  }

  private GroupingRules buildRules(String projectId, List<GroupingRuleRow> rows) {
    if (rows == null || rows.isEmpty()) {
      log.debug("No grouping rules found for projectId={} — returning empty bundle", projectId);
      return GroupingRules.empty();
    }

    GroupingRules.GroupingRulesBuilder builder = GroupingRules.builder();
    int compiledStripPatterns = 0;
    int compiledMaskRules = 0;
    for (GroupingRuleRow row : rows) {
      if (row.getPattern() == null || row.getPattern().isEmpty()) {
        continue;
      }
      try {
        switch (row.getRuleKind()) {
          case "IN_APP_PACKAGE" -> builder.inAppPrefix(row.getPattern());
          case "THIRD_PARTY_PACKAGE" -> builder.thirdPartyPrefix(row.getPattern());
          case "FRAMEWORK_PACKAGE" -> builder.frameworkPrefix(row.getPattern());
          case "STRIP_PATTERN" -> {
            builder.stripPattern(Pattern.compile(row.getPattern()));
            compiledStripPatterns++;
          }
          case "MASK_REGEX" -> {
            builder.maskRule(MaskRule.of(row.getPattern(), row.getReplacement()));
            compiledMaskRules++;
          }
          default -> log.warn(
              "Unknown grouping_rule.rule_kind={} for projectId={} id={} — skipping",
              row.getRuleKind(), projectId, row.getId());
        }
      } catch (RuntimeException ex) {
        // Bad regex / unexpected payload — skip the row but keep building so a single
        // misconfigured row can't take out the entire project's grouping pipeline.
        log.warn(
            "Failed to materialize grouping rule projectId={} id={} kind={} pattern={} — skipping",
            projectId, row.getId(), row.getRuleKind(), row.getPattern(), ex);
      }
    }
    log.debug(
        "Built GroupingRules for projectId={} from {} rows (stripPatterns={}, maskRules={})",
        projectId, rows.size(), compiledStripPatterns, compiledMaskRules);
    return builder.build();
  }

  /**
   * Test / admin hook to evict a project's cached bundle (e.g. after a CRUD
   * update once those endpoints land).
   */
  public void invalidate(String projectId) {
    cache.synchronous().invalidate(projectId);
  }
}
