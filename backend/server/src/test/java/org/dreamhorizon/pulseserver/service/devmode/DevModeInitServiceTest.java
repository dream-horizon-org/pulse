package org.dreamhorizon.pulseserver.service.devmode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyDao;
import org.dreamhorizon.pulseserver.dao.apikey.models.ProjectApiKey;
import org.dreamhorizon.pulseserver.dao.config.models.PulseSdkConfig;
import org.dreamhorizon.pulseserver.dao.usagelimit.models.UsageLimit;
import org.dreamhorizon.pulseserver.resources.notification.models.PlatformEventMappingDto;
import org.dreamhorizon.pulseserver.service.ClickhouseProjectService;
import org.dreamhorizon.pulseserver.service.ProjectService;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.dreamhorizon.pulseserver.util.encryption.EncryptedData;
import org.dreamhorizon.pulseserver.util.encryption.ProjectApiKeyEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DevModeInitService Tests")
class DevModeInitServiceTest {

    @Mock
    private ApplicationConfig applicationConfig;

    @Mock
    private ProjectService projectService;

    @Mock
    private ClickhouseProjectService clickhouseProjectService;

    @Mock
    private ProjectApiKeyDao apiKeyDao;

    @Mock
    private ProjectApiKeyEncryptionUtil encryptionUtil;

    @Mock
    private ConfigService configService;

    @Mock
    private UsageLimitService usageLimitService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MysqlClient mysqlClient;

    @Mock
    private MySQLPool writerPool;

    @Mock
    private SqlConnection sqlConnection;

    private DevModeInitService devModeInitService;

    private static final String DEFAULT_PROJECT_ID = "default-project";
    private static final String DEV_API_KEY = "default-project_devkey01";

    @BeforeEach
    void setUp() {
        devModeInitService = new DevModeInitService(
            applicationConfig,
            projectService,
            clickhouseProjectService,
            apiKeyDao,
            encryptionUtil,
            configService,
            usageLimitService,
            notificationService,
            mysqlClient
        );
        
        // Setup common mock for database connection
        when(mysqlClient.getWriterPool()).thenReturn(writerPool);
        when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
    }

    @Nested
    @DisplayName("initializeDevMode")
    class InitializeDevMode {

        @Test
        @DisplayName("should skip initialization when OAuth is enabled (true)")
        void shouldSkipWhenOAuthEnabled() {
            // Given
            when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(true);

            // When
            devModeInitService.initializeDevMode()
                .test()
                .assertComplete();

            // Then
            verify(projectService, never()).projectExists(any());
            verify(apiKeyDao, never()).getApiKeyByDigest(any());
        }

        @Test
        @DisplayName("should skip initialization when OAuth is null (defaults to enabled)")
        void shouldSkipWhenOAuthNull() {
            // Given
            when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(null);

            // When
            devModeInitService.initializeDevMode()
                .test()
                .assertComplete();

            // Then
            verify(projectService, never()).projectExists(any());
            verify(apiKeyDao, never()).getApiKeyByDigest(any());
        }

        @Test
        @DisplayName("should run full initialization when OAuth is disabled")
        void shouldRunFullInitWhenOAuthDisabled() {
            // Given
            when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
            when(applicationConfig.getDevModeApiKey()).thenReturn(DEV_API_KEY);
            when(projectService.projectExists(DEFAULT_PROJECT_ID)).thenReturn(Single.just(true));
            
            // API key check
            EncryptedData encryptedData = new EncryptedData("encrypted", "salt");
            when(encryptionUtil.encrypt(DEV_API_KEY)).thenReturn(encryptedData);
            when(encryptionUtil.generateDigest(any())).thenReturn("digest123");
            when(apiKeyDao.getApiKeyByDigest(any())).thenReturn(Maybe.empty());
            when(apiKeyDao.createApiKey(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Single.just(createMockApiKey()));
            
            // ClickHouse setup
            when(clickhouseProjectService.setupProjectClickhouseUser(eq(DEFAULT_PROJECT_ID), any()))
                .thenReturn(Completable.complete());
            
            // SDK config
            when(configService.createInitialConfig(any(), eq(DEFAULT_PROJECT_ID), any()))
                .thenReturn(Single.just(createMockSdkConfig()));
            
            // Usage limits
            when(usageLimitService.createInitialLimits(any(), eq(DEFAULT_PROJECT_ID), any()))
                .thenReturn(Single.just(createMockUsageLimit()));
            
            // Notification mappings
            when(notificationService.createDefaultPlatformMappings(DEFAULT_PROJECT_ID))
                .thenReturn(Single.just(List.of(createMockPlatformMapping())));

            // When
            devModeInitService.initializeDevMode()
                .test()
                .assertComplete();

            // Then
            verify(apiKeyDao).createApiKey(any(), any(), any(), any(), any(), any(), any());
            verify(clickhouseProjectService).setupProjectClickhouseUser(DEFAULT_PROJECT_ID, "system");
            verify(configService).createInitialConfig(any(), eq(DEFAULT_PROJECT_ID), eq("system"));
            verify(usageLimitService).createInitialLimits(any(), eq(DEFAULT_PROJECT_ID), eq("system"));
            verify(notificationService).createDefaultPlatformMappings(DEFAULT_PROJECT_ID);
        }
    }

    @Nested
    @DisplayName("ensureDefaultProjectHardcodedApiKey")
    class EnsureDefaultProjectHardcodedApiKey {

        @Test
        @DisplayName("should skip API key creation when default-project does not exist")
        void shouldSkipWhenProjectDoesNotExist() {
            // Given
            when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
            when(applicationConfig.getDevModeApiKey()).thenReturn(DEV_API_KEY);
            when(projectService.projectExists(DEFAULT_PROJECT_ID)).thenReturn(Single.just(false));

            // When
            devModeInitService.initializeDevMode()
                .test()
                .assertComplete();

            // Then
            verify(projectService).projectExists(DEFAULT_PROJECT_ID);
            verify(apiKeyDao, never()).getApiKeyByDigest(any());
            verify(apiKeyDao, never()).createApiKey(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should create hardcoded API key when it does not exist")
        void shouldCreateApiKeyWhenNotExists() {
            // Given
            when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
            when(applicationConfig.getDevModeApiKey()).thenReturn(DEV_API_KEY);
            when(projectService.projectExists(DEFAULT_PROJECT_ID)).thenReturn(Single.just(true));
            
            EncryptedData encryptedData = new EncryptedData("encrypted", "salt");
            when(encryptionUtil.encrypt(DEV_API_KEY)).thenReturn(encryptedData);
            when(encryptionUtil.generateDigest(any())).thenReturn("digest123");
            when(apiKeyDao.getApiKeyByDigest("digest123")).thenReturn(Maybe.empty());
            when(apiKeyDao.createApiKey(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Single.just(createMockApiKey()));
            
            // Mock other services to complete the flow
            when(clickhouseProjectService.setupProjectClickhouseUser(any(), any()))
                .thenReturn(Completable.complete());
            when(configService.createInitialConfig(any(), any(), any()))
                .thenReturn(Single.just(createMockSdkConfig()));
            when(usageLimitService.createInitialLimits(any(), any(), any()))
                .thenReturn(Single.just(createMockUsageLimit()));
            when(notificationService.createDefaultPlatformMappings(any()))
                .thenReturn(Single.just(List.of()));

            // When
            devModeInitService.initializeDevMode()
                .test()
                .assertComplete();

            // Then
            verify(apiKeyDao).createApiKey(
                eq(DEFAULT_PROJECT_ID),
                eq("Default Dev Key (Hardcoded)"),
                eq("encrypted"),
                eq("salt"),
                eq("digest123"),
                isNull(),
                eq("system")
            );
        }

        @Test
        @DisplayName("should skip API key creation when hardcoded key already exists")
        void shouldSkipWhenKeyExists() {
            // Given
            when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
            when(applicationConfig.getDevModeApiKey()).thenReturn(DEV_API_KEY);
            when(projectService.projectExists(DEFAULT_PROJECT_ID)).thenReturn(Single.just(true));
            
            EncryptedData encryptedData = new EncryptedData("encrypted", "salt");
            when(encryptionUtil.encrypt(DEV_API_KEY)).thenReturn(encryptedData);
            when(encryptionUtil.generateDigest(any())).thenReturn("digest123");
            when(apiKeyDao.getApiKeyByDigest("digest123")).thenReturn(Maybe.just(createMockApiKey()));
            
            // Mock other services
            when(clickhouseProjectService.setupProjectClickhouseUser(any(), any()))
                .thenReturn(Completable.complete());
            when(configService.createInitialConfig(any(), any(), any()))
                .thenReturn(Single.just(createMockSdkConfig()));
            when(usageLimitService.createInitialLimits(any(), any(), any()))
                .thenReturn(Single.just(createMockUsageLimit()));
            when(notificationService.createDefaultPlatformMappings(any()))
                .thenReturn(Single.just(List.of()));

            // When
            devModeInitService.initializeDevMode()
                .test()
                .assertComplete();

            // Then
            verify(apiKeyDao, never()).createApiKey(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should handle SDK config creation failure gracefully")
        void shouldHandleSdkConfigError() {
            // Given
            setupBasicMocks();
            when(configService.createInitialConfig(any(), any(), any()))
                .thenReturn(Single.error(new RuntimeException("Config creation failed")));

            // When
            devModeInitService.initializeDevMode()
                .test()
                .assertError(RuntimeException.class)
                .assertError(error -> error.getMessage().equals("Config creation failed"));

            // Then
            verify(configService).createInitialConfig(isNull(), eq(DEFAULT_PROJECT_ID), eq("system"));
        }

        @Test
        @DisplayName("should handle notification mapping failures gracefully")
        void shouldHandleNotificationMappingError() {
            // Given
            setupBasicMocks();
            when(configService.createInitialConfig(any(), any(), any()))
                .thenReturn(Single.just(createMockSdkConfig()));
            when(usageLimitService.createInitialLimits(any(), any(), any()))
                .thenReturn(Single.just(createMockUsageLimit()));
            when(notificationService.createDefaultPlatformMappings(any()))
                .thenReturn(Single.error(new RuntimeException("Notification mapping failed")));

            // When - should complete despite notification error (it's non-critical)
            devModeInitService.initializeDevMode()
                .test()
                .assertComplete();

            // Then
            verify(notificationService).createDefaultPlatformMappings(DEFAULT_PROJECT_ID);
        }
    }

    // Helper methods

    private void setupBasicMocks() {
        when(applicationConfig.getGoogleOAuthEnabled()).thenReturn(false);
        when(applicationConfig.getDevModeApiKey()).thenReturn(DEV_API_KEY);
        when(projectService.projectExists(DEFAULT_PROJECT_ID)).thenReturn(Single.just(true));
        
        EncryptedData encryptedData = new EncryptedData("encrypted", "salt");
        when(encryptionUtil.encrypt(DEV_API_KEY)).thenReturn(encryptedData);
        when(encryptionUtil.generateDigest(any())).thenReturn("digest123");
        when(apiKeyDao.getApiKeyByDigest(any())).thenReturn(Maybe.empty());
        when(apiKeyDao.createApiKey(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Single.just(createMockApiKey()));
        
        when(clickhouseProjectService.setupProjectClickhouseUser(any(), any()))
            .thenReturn(Completable.complete());
    }

    private ProjectApiKey createMockApiKey() {
        ProjectApiKey apiKey = new ProjectApiKey();
        apiKey.setProjectApiKeyId(1L);
        apiKey.setProjectId(DEFAULT_PROJECT_ID);
        apiKey.setDisplayName("Default Dev Key (Hardcoded)");
        apiKey.setIsActive(true);
        return apiKey;
    }

    private PulseSdkConfig createMockSdkConfig() {
        PulseSdkConfig config = new PulseSdkConfig();
        config.setProjectId(DEFAULT_PROJECT_ID);
        config.setVersion(1);
        config.setIsActive(true);
        return config;
    }

    private UsageLimit createMockUsageLimit() {
        UsageLimit limit = new UsageLimit();
        limit.setProjectId(DEFAULT_PROJECT_ID);
        return limit;
    }

    private PlatformEventMappingDto createMockPlatformMapping() {
        return PlatformEventMappingDto.builder()
            .projectId(DEFAULT_PROJECT_ID)
            .platformEventName("test_event")
            .build();
    }
}
