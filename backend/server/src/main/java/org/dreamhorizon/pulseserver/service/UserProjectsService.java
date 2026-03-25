package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;

/**
 * Service for fetching user's accessible projects.
 * When OpenFGA is enabled: tenant admins see all tenant projects; other users only see projects
 * where they hold an explicit project role. When OpenFGA is disabled, legacy behavior returns
 * all tenant projects (local dev).
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class UserProjectsService {

  private static final String ORG_ADMIN_ROLE = "organization_admin";

  private final OpenFgaService openFgaService;
  private final ProjectDao projectDao;
  private final org.dreamhorizon.pulseserver.dao.tenant.TenantDao tenantDao;

  private static final int FREE_TIER_ID = 1;

  /**
   * Get projects visible to the user within a tenant.
   *
   * @param userId user ID from JWT
   * @param tenantId tenant scope
   */
  public Single<UserProjectsResult> getUserProjects(String userId, String tenantId) {
    log.info("Fetching projects for user: userId={}, tenantId={}", userId, tenantId);

    return tenantDao.getTenantById(tenantId)
        .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + tenantId)))
        .flatMap(tenant -> {
          boolean isFreeTier = tenant.getTierId() != null && tenant.getTierId() == FREE_TIER_ID;
          if (!openFgaService.isEnabled()) {
            log.info("OpenFGA disabled: returning all tenant projects (legacy dev behavior)");
            return legacyAllTenantProjects(tenant, tenantId, isFreeTier);
          }
          return openFgaService.isTenantAdmin(userId, tenantId)
              .flatMap(isAdmin -> {
                if (Boolean.TRUE.equals(isAdmin)) {
                  log.info("User is tenant admin: listing all projects in tenant");
                  return projectsForTenantAdmin(userId, tenantId, tenant, isFreeTier);
                }
                log.info("User is not tenant admin: listing explicit project memberships only");
                return projectsForTenantMember(userId, tenantId, tenant, isFreeTier);
              });
        })
        .doOnError(error ->
            log.error("Failed to fetch projects: userId={}, tenantId={}", userId, tenantId, error));
  }

  /** OpenFGA off: all DB projects in tenant (previous behavior). */
  private Single<UserProjectsResult> legacyAllTenantProjects(
      Tenant tenant, String tenantId, boolean isFreeTier) {
    return projectDao.getProjectsByTenantId(tenantId)
        .map(project -> ProjectSummary.builder()
            .projectId(project.getProjectId())
            .name(project.getName())
            .description(project.getDescription())
            .isActive(project.getIsActive())
            .role("admin")
            .build())
        .toList()
        .map(list -> buildResult(tenantId, tenant, list, isFreeTier));
  }

  private Single<UserProjectsResult> projectsForTenantAdmin(
      String userId, String tenantId, Tenant tenant, boolean isFreeTier) {
    return projectDao.getProjectsByTenantId(tenantId)
        .toList()
        .flatMap(projects -> {
          if (projects.isEmpty()) {
            return Single.just(buildResult(tenantId, tenant, List.of(), isFreeTier));
          }
          return Observable.fromIterable(projects)
              .concatMapSingle(p -> openFgaService.getUserRoleInProject(userId, p.getProjectId())
                  .map(roleOpt -> ProjectSummary.builder()
                      .projectId(p.getProjectId())
                      .name(p.getName())
                      .description(p.getDescription())
                      .isActive(p.getIsActive())
                      .role(roleOpt.orElse(ORG_ADMIN_ROLE))
                      .build()))
              .toList()
              .map(list -> buildResult(tenantId, tenant, list, isFreeTier));
        });
  }

  private Single<UserProjectsResult> projectsForTenantMember(
      String userId, String tenantId, Tenant tenant, boolean isFreeTier) {
    return openFgaService.getUserProjects(userId)
        .flatMap(projectIds -> {
          if (projectIds == null || projectIds.isEmpty()) {
            return Single.just(buildResult(tenantId, tenant, List.of(), isFreeTier));
          }
          return Flowable.fromIterable(new LinkedHashSet<>(projectIds))
              .concatMapMaybe(pid -> projectDao.getProjectByProjectId(pid)
                  .filter(p -> tenantId.equals(p.getTenantId())))
              .concatMapSingle(p -> openFgaService.getUserRoleInProject(userId, p.getProjectId())
                  .map(roleOpt -> ProjectSummary.builder()
                      .projectId(p.getProjectId())
                      .name(p.getName())
                      .description(p.getDescription())
                      .isActive(p.getIsActive())
                      .role(roleOpt.orElse("viewer"))
                      .build()))
              .toList()
              .map(list -> buildResult(tenantId, tenant, list, isFreeTier));
        });
  }

  private UserProjectsResult buildResult(
      String tenantId, Tenant tenant, List<ProjectSummary> projectList, boolean isFreeTier) {
    List<ProjectSummary> filtered;
    if (isFreeTier && !projectList.isEmpty()) {
      filtered = List.of(projectList.get(0));
      log.info("FREE tier: returning first of {} project(s)", projectList.size());
    } else {
      filtered = new ArrayList<>(projectList);
    }
    String redirectTo;
    if (filtered.isEmpty()) {
      redirectTo = null;
    } else if (filtered.size() == 1) {
      redirectTo = "/projects/" + filtered.get(0).getProjectId();
    } else {
      redirectTo = "/project-selection";
    }
    return UserProjectsResult.builder()
        .tenantId(tenantId)
        .tenantName(tenant.getName())
        .projects(filtered)
        .redirectTo(redirectTo)
        .build();
  }

  @lombok.Data
  @lombok.Builder
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  public static class UserProjectsResult {
    private String tenantId;
    private String tenantName;
    private List<ProjectSummary> projects;
    private String redirectTo;
  }

  @lombok.Data
  @lombok.Builder
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  public static class ProjectSummary {
    private String projectId;
    private String name;
    private String description;
    private Boolean isActive;
    private String role;
  }
}
