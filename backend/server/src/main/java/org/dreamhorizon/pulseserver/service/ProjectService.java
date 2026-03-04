package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dto.ProjectCreationResult;
import org.dreamhorizon.pulseserver.dto.ProjectDetailsDto;
import org.dreamhorizon.pulseserver.dto.ProjectSummaryDto;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.resources.notification.models.RecipientsDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SendNotificationRequestDto;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.UploadConfigDetailService;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.dreamhorizon.pulseserver.util.SecureRandomUtil;

@Slf4j
@Singleton
public class ProjectService {

  private static final int PROJECT_ID_RANDOM_LENGTH = 8;

  private static final String DEFAULT_PROJECT_ID = "default-project";
  private static final String PROJECT_CREATED_EVENT = "project_created";

  private final MysqlClient mysqlClient;
  private final ProjectDao projectDao;
  private final OpenFgaService openFgaService;
  private final ClickhouseProjectService clickhouseProjectService;
  private final ProjectApiKeyService projectApiKeyService;
  private final ConfigService configService;
  private final UsageLimitService usageLimitService;
  private final UploadConfigDetailService uploadConfigDetailService;
  private final NotificationService notificationService;

  @Inject
  public ProjectService(
      MysqlClient mysqlClient,
      ProjectDao projectDao,
      OpenFgaService openFgaService,
      ClickhouseProjectService clickhouseProjectService,
      ProjectApiKeyService projectApiKeyService,
      ConfigService configService,
      UsageLimitService usageLimitService,
      UploadConfigDetailService uploadConfigDetailService,
      NotificationService notificationService) {
    this.mysqlClient = mysqlClient;
    this.projectDao = projectDao;
    this.openFgaService = openFgaService;
    this.clickhouseProjectService = clickhouseProjectService;
    this.projectApiKeyService = projectApiKeyService;
    this.configService = configService;
    this.usageLimitService = usageLimitService;
    this.uploadConfigDetailService = uploadConfigDetailService;
    this.notificationService = notificationService;
  }

  public Single<ProjectCreationResult> createProject(String tenantId, String name, String description, ReqUserInfo userInfo) {
    String createdBy = userInfo.getUserId();
    log.info("Creating project: tenantId={}, name={}, createdBy={}", tenantId, name, createdBy);

    // 1. Pre-transaction: Generate projectId
    String projectId = generateProjectId(name);

    Project project = Project.builder()
        .projectId(projectId)
        .tenantId(tenantId)
        .name(name)
        .description(description)
        .isActive(true)
        .createdBy(createdBy)
        .build();

    // 2. Execute transaction with service methods
    return executeTransaction(project, createdBy)
        // 3. Blocking: OpenFGA operations
        .flatMap(result ->
            assignRolesAndLink(result.project, createdBy, tenantId)
                .andThen(Single.just(result))
        )
        // 4. Fire-and-forget: Async tasks (including email notification)
        .doOnSuccess(result -> {
          executeAsyncTasks(result, projectId, tenantId, userInfo).subscribe();
        })
        .map(result -> ProjectCreationResult.builder()
            .project(result.project)
            .rawApiKey(result.rawApiKey)
            .build())
        .doOnSuccess(creationResult ->
            log.info("Project created successfully: projectId={}", creationResult.getProject().getProjectId())
        )
        .doOnError(error ->
            log.error("Failed to create project: tenantId={}, name={}", tenantId, name, error)
        );
  }

  private Single<TransactionResult> executeTransaction(Project project, String createdBy) {
    return mysqlClient.getWriterPool().rxGetConnection()
        .flatMap(conn -> conn.rxBegin()
            .flatMap(tx -> {
              log.debug("Transaction started for project: {}", project.getProjectId());

              return projectDao.createProject(conn, project)
                  .flatMap(createdProject ->
                      clickhouseProjectService.saveCredentials(conn, project.getProjectId())
                          .map(chCreds -> new TransactionResult(createdProject, chCreds, null))
                  )
                  .flatMap(result ->
                      projectApiKeyService.createDefaultApiKey(conn, project.getProjectId(), createdBy)
                          .map(apiKeyInfo -> new TransactionResult(result.project, result.chCredentials, apiKeyInfo.getRawApiKey()))
                  )
                  .flatMap(result ->
                      usageLimitService.createInitialLimits(conn, project.getProjectId(), "admin")
                          .map(limit -> result)
                  )
                  .flatMap(result ->
                      configService.createInitialConfig(conn, project.getProjectId(), createdBy)
                          .map(sdkConfig -> result)
                  )
                  .flatMap(result ->
                      tx.rxCommit()
                          .doOnComplete(() -> log.debug("Transaction committed for project: {}", project.getProjectId()))
                          .toSingleDefault(result)
                  )
                  .onErrorResumeNext(err -> {
                    log.error("Transaction failed for project: {}, rolling back", project.getProjectId(), err);
                    return tx.rxRollback()
                        .andThen(Single.error(err));
                  });
            })
            .doFinally(conn::close)
        );
  }

  private Completable assignRolesAndLink(Project project, String createdBy, String tenantId) {
    log.debug("Assigning roles and linking project: {} to tenant: {}", project.getProjectId(), tenantId);
    return openFgaService.assignProjectRole(createdBy, project.getProjectId(), "admin")
        .andThen(openFgaService.linkProjectToTenant(project.getProjectId(), tenantId))
        .doOnComplete(() -> log.debug("OpenFGA operations completed for project: {}", project.getProjectId()))
        .doOnError(err -> log.error("OpenFGA operations failed for project: {}", project.getProjectId(), err));
  }

  private Completable executeAsyncTasks(TransactionResult result, String projectId, String tenantId, ReqUserInfo userInfo) {
    var chCreds = result.chCredentials;
    log.debug("Executing async tasks for project: {}", projectId);

    return Completable.mergeArrayDelayError(
        // ClickHouse user creation
        clickhouseProjectService.createClickhouseUserAndPolicies(projectId, chCreds.getUsername(), chCreds.getPlainPassword())
            .doOnComplete(() -> log.info("ClickHouse user created for project: {}", projectId))
            .doOnError(err -> log.warn("ClickHouse user creation failed for project: {}, will retry on first query", projectId, err))
            .onErrorComplete(),

        // S3 upload of SDK config
        uploadConfigDetailService.pushInteractionDetailsToObjectStore(projectId)
            .ignoreElement()
            .doOnComplete(() -> log.info("SDK config uploaded to S3 for project: {}", projectId))
            .doOnError(err -> log.warn("S3 upload failed for project: {}, will serve from DB", projectId, err))
            .onErrorComplete(),

        // Send project creation email notification
        sendProjectCreatedEmail(result, projectId, tenantId, userInfo)
            .doOnComplete(() -> log.info("Project creation email sent for project: {}", projectId))
            .doOnError(err -> log.warn("Failed to send project creation email for project: {}", projectId, err))
            .onErrorComplete()
    );
  }

  private Completable sendProjectCreatedEmail(TransactionResult result, String projectId, String tenantId, ReqUserInfo userInfo) {
    if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
      log.warn("Cannot send project creation email: user email not available for project: {}", projectId);
      return Completable.complete();
    }

    // Determine display name: prefer name, fallback to email
    String displayName = (userInfo.getName() != null && !userInfo.getName().isBlank())
        ? userInfo.getName()
        : userInfo.getEmail();

    // Build template parameters
    Map<String, Object> params = Map.of(
        "createdBy", displayName,
        "projectName", result.project.getName(),
        "apiKey", result.rawApiKey != null ? result.rawApiKey : "[API key not available]"
    );

    // Build recipient
    RecipientsDto recipients = RecipientsDto.builder()
        .emails(List.of(userInfo.getEmail()))
        .build();

    // Build notification request (uses default-project for template lookup)
    SendNotificationRequestDto notificationRequest = SendNotificationRequestDto.builder()
        .eventName(PROJECT_CREATED_EVENT)
        .channelTypes(List.of(ChannelType.EMAIL))
        .recipients(recipients)
        .params(params)
        .build();

    return notificationService.sendNotification(DEFAULT_PROJECT_ID, notificationRequest)
        .ignoreElement();
  }

  // ==================== HELPER METHODS ====================

  private String generateProjectId(String projectName) {
    String sanitized = projectName.replaceAll("\\s+", "-");
    return sanitized + "-" + SecureRandomUtil.generateAlphanumeric(PROJECT_ID_RANDOM_LENGTH);
  }

  // ==================== NESTED CLASSES ====================

  /**
   * Holds results from the transaction that are needed for async tasks and response.
   */
  private static class TransactionResult {
    final Project project;
    final ClickhouseProjectService.CredentialsResult chCredentials;
    final String rawApiKey;

    TransactionResult(Project project, ClickhouseProjectService.CredentialsResult chCredentials, String rawApiKey) {
      this.project = project;
      this.chCredentials = chCredentials;
      this.rawApiKey = rawApiKey;
    }
  }

  // ==================== EXISTING METHODS (unchanged) ====================

  public Single<List<ProjectSummaryDto>> getProjectsForUser(String userId, String tenantId) {
    return projectDao.getProjectsByTenantId(tenantId)
        .flatMapSingle(project ->
            openFgaService.getUserRoleInProject(userId, project.getProjectId())
                .map(roleOpt -> ProjectSummaryDto.builder()
                    .projectId(project.getProjectId())
                    .name(project.getName())
                    .description(project.getDescription())
                    .role(roleOpt.orElse("none"))
                    .isActive(project.getIsActive())
                    .createdAt(project.getCreatedAt())
                    .build())
        )
        .toList()
        .doOnSuccess(projects ->
            log.debug("Retrieved {} projects for user {} in tenant {}",
                projects.size(), userId, tenantId)
        )
        .doOnError(error ->
            log.error("Failed to get projects for user: userId={}, tenantId={}",
                userId, tenantId, error)
        );
  }

  public Single<ProjectDetailsDto> getProjectDetails(String userId, String projectId) {
    return projectDao.getProjectByProjectId(projectId)
        .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId)))
        .flatMap(project ->
            openFgaService.getUserRoleInProject(userId, projectId)
                .flatMap(roleOpt -> {
                  if (roleOpt.isEmpty()) {
                    return Single.error(new RuntimeException(
                        "Access denied: User has no access to project " + projectId));
                  }

                  String role = roleOpt.get();
                  boolean isAdmin = "admin".equals(role);

                  return Single.just(ProjectDetailsDto.builder()
                      .projectId(project.getProjectId())
                      .tenantId(project.getTenantId())
                      .name(project.getName())
                      .description(project.getDescription())
                      .isActive(project.getIsActive())
                      .createdBy(project.getCreatedBy())
                      .createdAt(project.getCreatedAt())
                      .updatedAt(project.getUpdatedAt())
                      .userRole(role)
                      .build());
                })
        )
        .doOnSuccess(details ->
            log.debug("Retrieved project details: projectId={}, userRole={}",
                projectId, details.getUserRole())
        )
        .doOnError(error ->
            log.error("Failed to get project details: projectId={}, userId={}",
                projectId, userId, error)
        );
  }

  public Single<Project> getProjectById(String projectId) {
    return projectDao.getProjectByProjectId(projectId)
        .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId)));
  }

  @Deprecated
  public Single<Project> getProjectByApiKey(String apiKey) {
    return Single.error(new RuntimeException("API key lookup not implemented - use ProjectApiKeyService instead"));
  }

  public Single<Project> updateProject(String projectId, String name, String description) {
    return projectDao.getProjectByProjectId(projectId)
        .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId)))
        .flatMap(existing -> {
          Project updated = Project.builder()
              .projectId(projectId)
              .name(name)
              .description(description)
              .build();
          return projectDao.updateProject(updated);
        })
        .doOnSuccess(project ->
            log.info("Project updated: projectId={}", projectId)
        );
  }

  public Single<Void> deactivateProject(String projectId) {
    return projectDao.deactivateProject(projectId)
        .andThen(Single.just((Void) null))
        .doOnSuccess(v ->
            log.info("Project deactivated: projectId={}", projectId)
        );
  }

  public Single<Integer> getActiveProjectCount(String tenantId) {
    return projectDao.getActiveProjectCount(tenantId);
  }
}
