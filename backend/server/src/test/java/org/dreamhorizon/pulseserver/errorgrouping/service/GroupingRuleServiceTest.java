package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.regex.Pattern;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.MaskRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupingRuleServiceTest {

  @Mock
  private GroupingRuleCache cache;

  private GroupingRuleService service;

  @BeforeEach
  void setUp() {
    service = new GroupingRuleService(cache);
  }

  private GroupingRules sampleCachedRules() {
    return GroupingRules.builder()
        .inAppPrefix("com.dream11.")
        .inAppPrefix("com.fancode.")
        .thirdPartyPrefix("retrofit2.")
        .frameworkPrefix("android.")
        .stripPattern(Pattern.compile(".*synthetic.*"))
        .maskRule(MaskRule.of("[0-9]+", "<NUM>"))
        .build();
  }

  @Test
  void shouldReturnCachedRulesUnchangedWhenBundleIdIsNull() {
    GroupingRules cached = sampleCachedRules();
    when(cache.getCached("p1")).thenReturn(Single.just(cached));

    GroupingRules result = service.getRules("p1", null).blockingGet();

    // Same reference — no copy was built.
    assertThat(result).isSameAs(cached);
    assertThat(result.getBundleIdFallback()).isNull();
  }

  @Test
  void shouldReturnCachedRulesUnchangedWhenBundleIdIsBlank() {
    GroupingRules cached = sampleCachedRules();
    when(cache.getCached("p1")).thenReturn(Single.just(cached));

    GroupingRules result = service.getRules("p1", "   ").blockingGet();

    assertThat(result).isSameAs(cached);
  }

  @Test
  void shouldReturnCachedRulesUnchangedWhenBundleIdIsEmptyString() {
    GroupingRules cached = sampleCachedRules();
    when(cache.getCached("p1")).thenReturn(Single.just(cached));

    GroupingRules result = service.getRules("p1", "").blockingGet();

    assertThat(result).isSameAs(cached);
  }

  @Test
  void shouldReturnCachedRulesUnchangedWhenBundleIdAlreadyInInAppPrefixes() {
    GroupingRules cached = sampleCachedRules();
    when(cache.getCached("p1")).thenReturn(Single.just(cached));

    GroupingRules result = service.getRules("p1", "com.dream11.").blockingGet();

    // No new builder copy — duplicate prefix would be wasted work.
    assertThat(result).isSameAs(cached);
    assertThat(result.getBundleIdFallback()).isNull();
  }

  @Test
  void shouldAppendBundleIdAsLowestPriorityInAppFallback() {
    GroupingRules cached = sampleCachedRules();
    when(cache.getCached("p1")).thenReturn(Single.just(cached));

    GroupingRules result = service.getRules("p1", "com.new.app").blockingGet();

    // A new bundle was emitted — not the cached reference.
    assertThat(result).isNotSameAs(cached);
    assertThat(result.getBundleIdFallback()).isEqualTo("com.new.app");
    // All other fields preserved 1:1.
    assertThat(result.getInAppPrefixes()).containsExactlyElementsOf(cached.getInAppPrefixes());
    assertThat(result.getThirdPartyPrefixes()).containsExactlyElementsOf(cached.getThirdPartyPrefixes());
    assertThat(result.getFrameworkPrefixes()).containsExactlyElementsOf(cached.getFrameworkPrefixes());
    assertThat(result.getStripPatterns()).containsExactlyElementsOf(cached.getStripPatterns());
    assertThat(result.getMaskRules()).containsExactlyElementsOf(cached.getMaskRules());
  }

  @Test
  void shouldNotMutateCachedRulesWhenMergingBundleId() {
    GroupingRules cached = sampleCachedRules();
    when(cache.getCached("p1")).thenReturn(Single.just(cached));

    List<String> inAppBefore = List.copyOf(cached.getInAppPrefixes());
    List<String> thirdPartyBefore = List.copyOf(cached.getThirdPartyPrefixes());
    List<String> frameworkBefore = List.copyOf(cached.getFrameworkPrefixes());
    int stripCountBefore = cached.getStripPatterns().size();
    int maskCountBefore = cached.getMaskRules().size();
    String fallbackBefore = cached.getBundleIdFallback();

    // Two calls with different bundleIds; cached must not be mutated either time.
    service.getRules("p1", "com.aaa.app").blockingGet();
    service.getRules("p1", "com.bbb.app").blockingGet();

    assertThat(cached.getInAppPrefixes()).containsExactlyElementsOf(inAppBefore);
    assertThat(cached.getThirdPartyPrefixes()).containsExactlyElementsOf(thirdPartyBefore);
    assertThat(cached.getFrameworkPrefixes()).containsExactlyElementsOf(frameworkBefore);
    assertThat(cached.getStripPatterns()).hasSize(stripCountBefore);
    assertThat(cached.getMaskRules()).hasSize(maskCountBefore);
    assertThat(cached.getBundleIdFallback()).isEqualTo(fallbackBefore);
  }

  @Test
  void shouldPropagateCacheErrorThrough() {
    RuntimeException err = new RuntimeException("cache miss explode");
    when(cache.getCached("p1")).thenReturn(Single.error(err));

    service.getRules("p1", "com.example.app").test()
        .assertError(err);
  }

  @Test
  void shouldCallCacheWithExactProjectIdEachInvocation() {
    GroupingRules cached = sampleCachedRules();
    when(cache.getCached("p1")).thenReturn(Single.just(cached));

    service.getRules("p1", null).blockingGet();
    service.getRules("p1", "com.new.app").blockingGet();

    verify(cache, times(2)).getCached("p1");
  }

  @Test
  void shouldEmitNewBuilderEvenWhenInAppPrefixesAreEmpty() {
    // Edge case: empty rules + new bundleId → still produces a copy with fallback set.
    GroupingRules empty = GroupingRules.empty();
    when(cache.getCached("p-empty")).thenReturn(Single.just(empty));

    GroupingRules result = service.getRules("p-empty", "com.solo.app").blockingGet();

    assertThat(result).isNotSameAs(empty);
    assertThat(result.getBundleIdFallback()).isEqualTo("com.solo.app");
    assertThat(result.getInAppPrefixes()).isEmpty();
  }
}
