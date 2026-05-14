package org.dreamhorizon.pulseserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.tenant.models.Tenant;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;
import org.dreamhorizon.pulseserver.service.tenant.dto.TenantWithProjectResult;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  private TenantWithProjectResult createTenantWithProjectResult(
      String tenantId, String projectId, String rawApiKey) {
    TenantWithProjectResult r = new TenantWithProjectResult();
    r.setTenantId(tenantId);
    r.setProjectId(projectId);
    r.setRawApiKey(rawApiKey);
    return r;
  }

  @Nested
  class CompleteOnboarding {

    @Test
    void shouldCompleteOnboardingSuccessfully() {
      User user = createUser(USER_ID, EMAIL, NAME, FIREBASE_UID);
      Tenant tenant = createTenant(TENANT_ID, ORG_NAME, 1);
      TenantWithProjectResult tenantWithProject =
          createTenantWithProjectResult(TENANT_ID, PROJECT_ID, API_KEY);

      when(userService.getOrCreateUser(EMAIL, NAME, FIREBASE_UID)).thenReturn(Single.just(user));
      when(openFgaService.getUserTenants(USER_ID)).thenReturn(Single.just(Collections.emptyList()));
      when(tenantService.createTenantWithProject(any(ReqUserInfo.class), eq(ORG_NAME), eq(PROJECT_NAME), any()))
          .thenReturn(Single.just(tenantWithProject));
      when(tenantService.getTenant(TENANT_ID)).thenReturn(Maybe.just(tenant));
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
      assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
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

      verify(tenantService).createTenantWithProject(any(ReqUserInfo.class), eq(ORG_NAME), eq(PROJECT_NAME), any());
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
      verify(tenantService, never()).createTenantWithProject(any(), any(), any(), any());
    }

    @Test
    void shouldFailWhenTenantWithProjectCreationFails() {
      User user = createUser(USER_ID, EMAIL, NAME, FIREBASE_UID);

      when(userService.getOrCreateUser(EMAIL, NAME, FIREBASE_UID)).thenReturn(Single.just(user));
      when(openFgaService.getUserTenants(USER_ID)).thenReturn(Single.just(Collections.emptyList()));
      when(tenantService.createTenantWithProject(any(ReqUserInfo.class), any(), any(), any()))
          .thenReturn(Single.error(new RuntimeException("Tenant creation failed")));

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          onboardingService.completeOnboarding(FIREBASE_UID, EMAIL, NAME, ORG_NAME, PROJECT_NAME, PROJECT_DESC)
              .blockingGet());

      assertThat(ex.getMessage()).contains("Tenant creation failed");
    }

    @Test
    void shouldUseFreeAsDefaultTierWhenTierNotFound() {
      User user = createUser(USER_ID, EMAIL, NAME, FIREBASE_UID);
      Tenant tenant = createTenant(TENANT_ID, ORG_NAME, 99);
      TenantWithProjectResult tenantWithProject =
          createTenantWithProjectResult(TENANT_ID, PROJECT_ID, API_KEY);

      when(userService.getOrCreateUser(EMAIL, NAME, FIREBASE_UID)).thenReturn(Single.just(user));
      when(openFgaService.getUserTenants(USER_ID)).thenReturn(Single.just(Collections.emptyList()));
      when(tenantService.createTenantWithProject(any(ReqUserInfo.class), any(), any(), any()))
          .thenReturn(Single.just(tenantWithProject));
      when(tenantService.getTenant(TENANT_ID)).thenReturn(Maybe.just(tenant));
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
