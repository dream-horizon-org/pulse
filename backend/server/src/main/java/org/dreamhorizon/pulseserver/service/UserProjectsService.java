package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.project.models.Project;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for fetching user's accessible projects.
 * Uses OpenFGA to determine which projects a user can access.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class UserProjectsService {
    
    private final OpenFgaService openFgaService;
    private final ProjectDao projectDao;
    private final org.dreamhorizon.pulseserver.dao.tenant.TenantDao tenantDao;
    
    /**
     * Get all projects accessible to a user within a tenant.
     * 
     * TEMPORARY: Returns ALL projects in the tenant until OpenFGA is fully integrated.
     * TODO: Restore OpenFGA role-based filtering when ready.
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @return UserProjectsResult with project list and redirect hint
     */
    public Single<UserProjectsResult> getUserProjects(String userId, String tenantId) {
        log.info("Fetching projects for user: userId={}, tenantId={}", userId, tenantId);
        log.warn("TEMPORARY: Returning ALL projects in tenant (OpenFGA not integrated)");
        
        // Fetch tenant information first
        return tenantDao.getTenantById(tenantId)
            .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + tenantId)))
            .flatMap(tenant -> 
                // TEMPORARY FIX: Return all projects in the tenant
                // Once OpenFGA is integrated, uncomment the OpenFGA code below and remove this section
                projectDao.getProjectsByTenantId(tenantId)
                    .map(project -> ProjectSummary.builder()
                        .projectId(project.getProjectId())
                        .name(project.getName())
                        .description(project.getDescription())
                        .isActive(project.getIsActive())
                        .role("admin") // Default role until OpenFGA is integrated
                        .build())
                    .toList()
                    .map(projectList -> {
                        // Calculate redirect hint
                        String redirectTo;
                        if (projectList.isEmpty()) {
                            redirectTo = null;
                            log.info("No projects found in tenant");
                        } else if (projectList.size() == 1) {
                            redirectTo = "/projects/" + projectList.get(0).getProjectId();
                            log.info("Single project, redirecting to: {}", redirectTo);
                        } else {
                            redirectTo = "/project-selection";
                            log.info("Multiple projects ({}), redirecting to selection page", projectList.size());
                        }
                        
                        return UserProjectsResult.builder()
                            .tenantId(tenantId)
                            .tenantName(tenant.getName())
                            .projects(projectList)
                            .redirectTo(redirectTo)
                            .build();
                    })
            )
            .doOnError(error -> 
                log.error("Failed to fetch projects: userId={}, tenantId={}", userId, tenantId, error)
            );
        
        /* TODO: Restore this OpenFGA-based implementation when ready:
        
        return openFgaService.getUserProjectsInTenant(userId, tenantId)
            .flatMap(projectIds -> {
                if (projectIds == null || projectIds.isEmpty()) {
                    log.info("User has no projects in tenant: userId={}, tenantId={}", userId, tenantId);
                    return Single.just(UserProjectsResult.builder()
                        .projects(List.of())
                        .redirectTo(null)
                        .build());
                }
                
                // Fetch project details for each project ID
                return Flowable.fromIterable(projectIds)
                    .flatMapSingle(projectId -> 
                        projectDao.getProjectById(projectId)
                            .flatMapSingle(project -> 
                                openFgaService.getUserRoleInProject(userId, projectId)
                                    .map(roleOpt -> ProjectSummary.builder()
                                        .projectId(project.getProjectId())
                                        .name(project.getName())
                                        .description(project.getDescription())
                                        .isActive(project.getIsActive())
                                        .role(roleOpt.orElse("viewer"))
                                        .build())
                            )
                            .switchIfEmpty(Single.defer(() -> {
                                log.warn("Project not found: projectId={}, skipping", projectId);
                                return Single.just((ProjectSummary) null);
                            }))
                            .onErrorResumeNext(error -> {
                                log.warn("Failed to fetch project details: projectId={}, skipping. Error: {}", 
                                    projectId, error.getMessage());
                                return Single.just((ProjectSummary) null);
                            })
                    )
                    .filter(project -> project != null)
                    .toList()
                    .map(projects -> {
                        List<ProjectSummary> projectList = new ArrayList<>();
                        for (Object obj : projects) {
                            if (obj instanceof ProjectSummary) {
                                projectList.add((ProjectSummary) obj);
                            }
                        }
                        
                        String redirectTo;
                        if (projectList.isEmpty()) {
                            redirectTo = null;
                            log.info("No accessible projects found after filtering");
                        } else if (projectList.size() == 1) {
                            redirectTo = "/projects/" + projectList.get(0).getProjectId();
                            log.info("Single project, redirecting to: {}", redirectTo);
                        } else {
                            redirectTo = "/projects/select";
                            log.info("Multiple projects ({}), redirecting to selection page", projectList.size());
                        }
                        
                        return UserProjectsResult.builder()
                            .projects(projectList)
                            .redirectTo(redirectTo)
                            .build();
                    });
            })
            .doOnError(error -> 
                log.error("Failed to fetch projects: userId={}, tenantId={}", userId, tenantId, error)
            );
        */
    }
    
    /**
     * Result of getUserProjects operation.
     */
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
    
    /**
     * Summary of a project with user's role.
     */
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
