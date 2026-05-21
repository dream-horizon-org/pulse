package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.dreamhorizon.pulseserver.errorgrouping.dao.GroupingRuleDao;
import org.dreamhorizon.pulseserver.errorgrouping.dao.GroupingRuleRow;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.MaskRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupingRuleCacheTest {

  @Mock
  private GroupingRuleDao dao;

  private Vertx vertx;
  private GroupingRuleCache cache;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    cache = new GroupingRuleCache(vertx, dao);
  }

  @AfterEach
  void tearDown() {
    if (vertx != null) {
      vertx.close();
    }
  }

  private GroupingRuleRow row(long id, String kind, String pattern, String replacement, int position) {
    return GroupingRuleRow.builder()
        .id(id)
        .projectId("p1")
        .ruleKind(kind)
        .pattern(pattern)
        .replacement(replacement)
        .position(position)
        .enabled(true)
        .build();
  }

  @Test
  void shouldBuildInAppPrefixesFromInAppPackageRows() {
    when(dao.loadRulesForProject("p1")).thenReturn(Single.just(List.of(
        row(1L, "IN_APP_PACKAGE", "com.dream11.", null, 0),
        row(2L, "IN_APP_PACKAGE", "com.fancode.", null, 1),
        row(3L, "IN_APP_PACKAGE", "com.bowled.", null, 2)
    )));

    GroupingRules rules = cache.getCached("p1").blockingGet();

    assertThat(rules.getInAppPrefixes())
        .containsExactly("com.dream11.", "com.fancode.", "com.bowled.");
    assertThat(rules.getThirdPartyPrefixes()).isEmpty();
    assertThat(rules.getFrameworkPrefixes()).isEmpty();
    assertThat(rules.getStripPatterns()).isEmpty();
    assertThat(rules.getMaskRules()).isEmpty();
  }

  @Test
  void shouldBuildThirdPartyPrefixesFromThirdPartyPackageRows() {
    when(dao.loadRulesForProject("p1")).thenReturn(Single.just(List.of(
        row(1L, "THIRD_PARTY_PACKAGE", "com.squareup.okhttp.", null, 0),
        row(2L, "THIRD_PARTY_PACKAGE", "retrofit2.", null, 1)
    )));

    GroupingRules rules = cache.getCached("p1").blockingGet();

    assertThat(rules.getThirdPartyPrefixes())
        .containsExactly("com.squareup.okhttp.", "retrofit2.");
    assertThat(rules.getInAppPrefixes()).isEmpty();
  }

  @Test
  void shouldBuildFrameworkPrefixesFromFrameworkPackageRows() {
    when(dao.loadRulesForProject("p1")).thenReturn(Single.just(List.of(
        row(1L, "FRAMEWORK_PACKAGE", "android.", null, 0),
        row(2L, "FRAMEWORK_PACKAGE", "androidx.", null, 1)
    )));

    GroupingRules rules = cache.getCached("p1").blockingGet();

    assertThat(rules.getFrameworkPrefixes())
        .containsExactly("android.", "androidx.");
  }

  @Test
  void shouldCompileStripPatternsOnce() {
    when(dao.loadRulesForProject("p1")).thenReturn(Single.just(List.of(
        row(1L, "STRIP_PATTERN", ".*synthetic.*", null, 0),
        row(2L, "STRIP_PATTERN", "lambda\\$", null, 1)
    )));

    GroupingRules rules = cache.getCached("p1").blockingGet();

    assertThat(rules.getStripPatterns()).hasSize(2);
    assertThat(rules.getStripPatterns().get(0).pattern()).isEqualTo(".*synthetic.*");
    assertThat(rules.getStripPatterns().get(1).pattern()).isEqualTo("lambda\\$");
  }

  @Test
  void shouldCompileMaskRulesOnce() {
    when(dao.loadRulesForProject("p1")).thenReturn(Single.just(List.of(
        row(1L, "MASK_REGEX", "0x[a-f0-9]+", "<HEX>", 0),
        row(2L, "MASK_REGEX", "[0-9]+", "<NUM>", 1)
    )));

    GroupingRules rules = cache.getCached("p1").blockingGet();

    assertThat(rules.getMaskRules()).hasSize(2);
    MaskRule first = rules.getMaskRules().get(0);
    assertThat(first.getRegex().pattern()).isEqualTo("0x[a-f0-9]+");
    assertThat(first.getReplacement()).isEqualTo("<HEX>");
    MaskRule second = rules.getMaskRules().get(1);
    assertThat(second.getRegex().pattern()).isEqualTo("[0-9]+");
    assertThat(second.getReplacement()).isEqualTo("<NUM>");
  }

  @Test
  void shouldReturnEmptyRulesWhenDaoReturnsNoRows() {
    when(dao.loadRulesForProject("p-empty")).thenReturn(Single.just(List.of()));

    GroupingRules rules = cache.getCached("p-empty").blockingGet();

    assertThat(rules.getInAppPrefixes()).isEmpty();
    assertThat(rules.getThirdPartyPrefixes()).isEmpty();
    assertThat(rules.getFrameworkPrefixes()).isEmpty();
    assertThat(rules.getStripPatterns()).isEmpty();
    assertThat(rules.getMaskRules()).isEmpty();
    assertThat(rules.getBundleIdFallback()).isNull();
  }

  @Test
  void shouldSkipBadRegexRowAndContinueLoading() {
    // `[` is an unclosed character class → PatternSyntaxException → row skipped.
    when(dao.loadRulesForProject("p1")).thenReturn(Single.just(List.of(
        row(1L, "MASK_REGEX", "[", "<BAD>", 0),
        row(2L, "MASK_REGEX", "[0-9]+", "<NUM>", 1),
        row(3L, "IN_APP_PACKAGE", "com.dream11.", null, 2)
    )));

    GroupingRules rules = cache.getCached("p1").blockingGet();

    // Bad row is skipped, others still loaded.
    assertThat(rules.getMaskRules()).hasSize(1);
    assertThat(rules.getMaskRules().get(0).getRegex().pattern()).isEqualTo("[0-9]+");
    assertThat(rules.getInAppPrefixes()).containsExactly("com.dream11.");
  }

  @Test
  void shouldSkipUnknownRuleKind() {
    when(dao.loadRulesForProject("p1")).thenReturn(Single.just(List.of(
        row(1L, "BANANA", "anything", null, 0),
        row(2L, "IN_APP_PACKAGE", "com.dream11.", null, 1)
    )));

    GroupingRules rules = cache.getCached("p1").blockingGet();

    // Unknown kind is logged + skipped; the IN_APP row still loads.
    assertThat(rules.getInAppPrefixes()).containsExactly("com.dream11.");
    assertThat(rules.getMaskRules()).isEmpty();
    assertThat(rules.getStripPatterns()).isEmpty();
  }

  @Test
  void shouldSkipRowsWithNullOrEmptyPattern() {
    when(dao.loadRulesForProject("p1")).thenReturn(Single.just(List.of(
        row(1L, "IN_APP_PACKAGE", null, null, 0),
        row(2L, "IN_APP_PACKAGE", "", null, 1),
        row(3L, "IN_APP_PACKAGE", "com.dream11.", null, 2)
    )));

    GroupingRules rules = cache.getCached("p1").blockingGet();

    assertThat(rules.getInAppPrefixes()).containsExactly("com.dream11.");
  }

  @Test
  void shouldCachePerProjectIdAndOnlyHitDaoOnce() {
    AtomicInteger callCount = new AtomicInteger(0);
    when(dao.loadRulesForProject("p1")).thenAnswer(inv -> {
      callCount.incrementAndGet();
      return Single.just(List.<GroupingRuleRow>of(
          row(1L, "IN_APP_PACKAGE", "com.dream11.", null, 0)));
    });

    GroupingRules first = cache.getCached("p1").blockingGet();
    GroupingRules second = cache.getCached("p1").blockingGet();

    assertThat(first.getInAppPrefixes()).containsExactly("com.dream11.");
    assertThat(second.getInAppPrefixes()).containsExactly("com.dream11.");
    // Cached after the first miss — DAO should not be hit twice.
    assertThat(callCount.get()).isEqualTo(1);
  }

  @Test
  void shouldKeepProjectsIsolatedInCache() {
    when(dao.loadRulesForProject("p1")).thenReturn(Single.just(List.of(
        row(1L, "IN_APP_PACKAGE", "com.p1.", null, 0))));
    when(dao.loadRulesForProject("p2")).thenReturn(Single.just(List.of(
        row(2L, "IN_APP_PACKAGE", "com.p2.", null, 0))));

    GroupingRules a = cache.getCached("p1").blockingGet();
    GroupingRules b = cache.getCached("p2").blockingGet();

    assertThat(a.getInAppPrefixes()).containsExactly("com.p1.");
    assertThat(b.getInAppPrefixes()).containsExactly("com.p2.");
    verify(dao).loadRulesForProject("p1");
    verify(dao).loadRulesForProject("p2");
  }

  @Test
  void shouldEvictOnInvalidate() {
    when(dao.loadRulesForProject("p1")).thenReturn(Single.just(List.of(
        row(1L, "IN_APP_PACKAGE", "com.dream11.", null, 0))));

    cache.getCached("p1").blockingGet();
    cache.invalidate("p1");
    cache.getCached("p1").blockingGet();

    // After invalidate the cache must reload — DAO is hit twice.
    verify(dao, times(2)).loadRulesForProject("p1");
  }

  @Test
  void shouldPropagateDaoErrorThroughCacheGet() {
    RuntimeException err = new RuntimeException("db down");
    when(dao.loadRulesForProject(anyString())).thenReturn(Single.error(err));

    cache.getCached("p1").test().awaitDone(2, java.util.concurrent.TimeUnit.SECONDS)
        .assertError(throwable ->
            throwable == err || throwable.getCause() == err);
  }
}
