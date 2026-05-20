package org.dreamhorizon.pulseserver.errorgrouping.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;

/**
 * Per-request facade over {@link GroupingRuleCache}. Fetches the cached
 * DB-derived {@link GroupingRules} for a project, then merges the event's
 * {@code bundleId} as the lowest-priority IN_APP rule
 * (via {@link GroupingRules#getBundleIdFallback()}) <strong>without mutating
 * the cached object</strong>.
 *
 * <p>{@code FrameClassifier} checks the bundleId fallback after every explicit
 * prefix list, so setting it on a per-request copy is sufficient — the caller
 * never has to manually append to {@code inAppPrefixes}.</p>
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class GroupingRuleService {

  private final GroupingRuleCache cache;

  /**
   * Fetch the per-project rule bundle, merging {@code bundleId} as the
   * lowest-priority IN_APP fallback. Returns the cached bundle unchanged when
   * {@code bundleId} is null/blank or already covered by an explicit IN_APP
   * prefix.
   */
  public Single<GroupingRules> getRules(String projectId, String bundleId) {
    return cache.getCached(projectId)
        .map(rules -> mergeBundleId(rules, bundleId));
  }

  private GroupingRules mergeBundleId(GroupingRules cached, String bundleId) {
    if (bundleId == null || bundleId.isBlank()) {
      return cached;
    }
    // If bundleId is already an explicit IN_APP prefix the fallback would just
    // duplicate work — return cached unchanged.
    for (String prefix : cached.getInAppPrefixes()) {
      if (bundleId.equals(prefix)) {
        return cached;
      }
    }

    return GroupingRules.builder()
        .inAppPrefixes(cached.getInAppPrefixes())
        .thirdPartyPrefixes(cached.getThirdPartyPrefixes())
        .frameworkPrefixes(cached.getFrameworkPrefixes())
        .stripPatterns(cached.getStripPatterns())
        .maskRules(cached.getMaskRules())
        .bundleIdFallback(bundleId)
        .build();
  }
}
