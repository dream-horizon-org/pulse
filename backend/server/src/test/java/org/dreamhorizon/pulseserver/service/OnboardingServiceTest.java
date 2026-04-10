package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.project.models.Project;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.dto.ProjectCreationResult;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

  @Mock
  TenantService tenantService;

  @Mock
  ProjectService projectService;

  @Mock
  OpenFgaService openFgaService;

  @Mock
  JwtService jwtService;

  @Mock
  UserService userService;

  @Mock
  TierService tierService;

  OnboardingService onboardingService;

  private static final String FIREBASE_UID = "firebase-123";
  private static final String EMAIL = "user@example.com";
  private static final String NAME = "Test User";
  private static final String ORG_NAME = "Test Org";
  private static final String PROJECT_NAME = "My Project";
  private static final String PROJECT_DESC = "Project description";
  private static final String USER_ID = "user-db-1";
  private static final String TENANT_ID = "tenant-abc";
  private static final String PROJECT_ID = "proj-xyz";
  private static final String API_KEY = "api-key-raw";

  @BeforeEach
  void setUp() {
    onboardingService = new OnboardingService(
        tenantService,
        projectService,
        openFgaService,
        jwtService,
        userService,
        tierService);
  }

  private User createUser(String userId, String email, String name, String firebaseUid) {
    return User.builder()
        .userId(userId)
        .email(email)
        .name(name)
        .firebaseUid(firebaseUid)
        .isActive(true)
        .build();
  }

  private Tenant createTenant(String tenantId, String name, Integer tierId) {
    return Tenant.builder()
        .tenantId(tenantId)
        .name(name)
        .tierId(tierId)
        .isActive(true)
        .build();
  }

  private Project createProject(String projectId, String tenantId, String name) {
    return Project.builder()
        .projectId(projectId)
        .tenantId(tenantId)
        .name(name)
        .isActive(true)
        .build();
  }

  @Nested
  class CompleteOnboarding {

    @Test
    void shouldCompleteOnboardingSuccessfully() {
      User user = createUser(USER_ID, EMAIL, NAME, FIREBASE_UID);
      Tenant tenant = createTenant(TENANT_ID, ORG_NAME, 1);
      Project project = createProject(PROJECT_ID, TENANT_ID, PROJECT_NAME);
      ProjectCreationResult creationResult = ProjectCreationResult.builder()
          .project(project)
          .rawApiKey(API_KEY)
          .build();

      when(userService.getOrCreateUser(EMAIL, NAME, FIREBASE_UID)).thenReturn(Single.just(user));
      when(openFgaService.getUserTenants(USER_ID)).thenReturn(Single.just(Collections.emptyList()));
      when(tenantService.createTenant(any(CreateTenantRequest.class))).thenReturn(Single.just(tenant));
      when(projectService.createProject(any(String.class), eq(PROJECT_NAME), eq(PROJECT_DESC), any(ReqUserInfo.class)))
          .thenReturn(Single.just(creationResult));
      when(openFgaService.assignTenantRole(eq(USER_ID), any(String.class), eq("admin")))
          .thenReturn(Completable.complete());
      when(tierService.getTierNameById(1)).thenReturn(Maybe.just("free"));
      when(jwtService.generateAccessToken(eq(USER_ID), eq(EMAIL), eq(NAME), any(String.class))).thenReturn("access-token");
      when(jwtService.generateRefreshToken(eq(USER_ID), eq(EMAIL), eq(NAME), any(String.class))).thenReturn("refresh-token");

      OnboardingService.OnboardingResult result =
          onboardingService.completeOnboarding(FIREBASE_UID, EMAIL, NAME, ORG_NAME, PROJECT_NAME, PROJECT_DESC)
              .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getUserId()).isEqualTo(USER_ID);
      assertThat(result.getEmail()).isEqualTo(EMAIL);
      assertThat(result.getName()).isEqualTo(NAME);
      assertThat(result.getTenantName()).isEqualTo(ORG_NAME);
      assertThat(result.getTier()).isEqualTo("free");
      assertThat(result.getProjectId()).isEqualTo(PROJECT_ID);
      assertThat(result.getProjectName()).isEqualTo(PROJECT_NAME);
      assertThat(result.getProjectApiKey()).isEqualTo(API_KEY);
      assertThat(result.getAccessToken()).isEqualTo("access-token");
      assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
      assertThat(result.getTokenType()).isEqualTo("Bearer");
      assertThat(result.getExpiresIn()).isEqualTo(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS);
      assertThat(result.getRedirectTo()).isEqualTo("/projects/" + PROJECT_ID);

      ArgumentCaptor<CreateTenantRequest> tenantReqCaptor = ArgumentCaptor.forClass(CreateTenantRequest.class);
      verify(tenantService).createTenant(tenantReqCaptor.capture());
      assertThat(tenantReqCaptor.getValue().getName()).isEqualTo(ORG_NAME);

      ArgumentCaptor<ReqUserInfo> userInfoCaptor = ArgumentCaptor.forClass(ReqUserInfo.class);
      verify(projectService).createProject(
          any(), eq(PROJECT_NAME), eq(PROJECT_DESC), userInfoCaptor.capture());
      assertThat(userInfoCaptor.getValue().getUserId()).isEqualTo(USER_ID);
      assertThat(userInfoCaptor.getValue().getEmail()).isEqualTo(EMAIL);
      assertThat(userInfoCaptor.getValue().getName()).isEqualTo(NAME);
    }

    @Test
    void shouldFailWhenUserAlreadyHasTenants() {
      User user = createUser(USER_ID, EMAIL, NAME, FIREBASE_UID);
      List<String> existingTenants = List.of("tenant-existing");

      when(userService.getOrCreateUser(EMAIL, NAME, FIREBASE_UID)).thenReturn(Single.just(user));
      when(openFgaService.getUserTenants(USER_ID)).thenReturn(Single.just(existingTenants));

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
          onboardingService.completeOnboarding(FIREBASE_UID, EMAIL, NAME, ORG_NAME, PROJECT_NAME, PROJECT_DESC)
              .blockingGet());

      assertThat(ex.getMessage()).contains("already part of an organization");
      verify(tenantService, never()).createTenant(any(CreateTenantRequest.class));
    }

    @Test
    void shouldFailWhenTenantCreationFails() {
      User user = createUser(USER_ID, EMAIL, NAME, FIREBASE_UID);

      when(userService.getOrCreateUser(EMAIL, NAME, FIREBASE_UID)).thenReturn(Single.just(user));
      when(openFgaService.getUserTenants(USER_ID)).thenReturn(Single.just(Collections.emptyList()));
      when(tenantService.createTenant(any(CreateTenantRequest.class)))
          .thenReturn(Single.error(new RuntimeException("Tenant creation failed")));

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          onboardingService.completeOnboarding(FIREBASE_UID, EMAIL, NAME, ORG_NAME, PROJECT_NAME, PROJECT_DESC)
              .blockingGet());

      assertThat(ex.getMessage()).contains("Tenant creation failed");
    }

    @Test
    void shouldUseFreeAsDefaultTier() {
      User user = createUser(USER_ID, EMAIL, NAME, FIREBASE_UID);
      Tenant tenant = createTenant(TENANT_ID, ORG_NAME, 99);
      Project project = createProject(PROJECT_ID, TENANT_ID, PROJECT_NAME);
      ProjectCreationResult creationResult = ProjectCreationResult.builder()
          .project(project)
          .rawApiKey(API_KEY)
          .build();

      when(userService.getOrCreateUser(EMAIL, NAME, FIREBASE_UID)).thenReturn(Single.just(user));
      when(openFgaService.getUserTenants(USER_ID)).thenReturn(Single.just(Collections.emptyList()));
      when(tenantService.createTenant(any(CreateTenantRequest.class))).thenReturn(Single.just(tenant));
      when(projectService.createProject(any(String.class), eq(PROJECT_NAME), eq(PROJECT_DESC), any(ReqUserInfo.class)))
          .thenReturn(Single.just(creationResult));
      when(openFgaService.assignTenantRole(any(), any(), eq("admin"))).thenReturn(Completable.complete());
      when(tierService.getTierNameById(99)).thenReturn(Maybe.empty());
      when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("access-token");
      when(jwtService.generateRefreshToken(any(), any(), any(), any())).thenReturn("refresh-token");

      OnboardingService.OnboardingResult result =
          onboardingService.completeOnboarding(FIREBASE_UID, EMAIL, NAME, ORG_NAME, PROJECT_NAME, PROJECT_DESC)
              .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTier()).isEqualTo("free");
    }
  }
}
