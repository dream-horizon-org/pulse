package org.dreamhorizon.pulseserver.service.devmode;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyDao;
import org.dreamhorizon.pulseserver.service.ClickhouseProjectService;
import org.dreamhorizon.pulseserver.service.ProjectService;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.dreamhorizon.pulseserver.util.encryption.EncryptedData;
import org.dreamhorizon.pulseserver.util.encryption.ProjectApiKeyEncryptionUtil;

/**
 * Service for initializing development mode resources.
 * Ensures default-project has necessary resources when GOOGLE_OAUTH_ENABLED=false.
 * 
 * This service is called on application startup to ensure dev mode 
 * is properly configured with:
 * - Hardcoded API key for default-project (configured via DEV_MODE_API_KEY env var)
 * - ClickHouse credentials and user/policies for default-project
 * - Initial SDK config (required for /v1/configs/active/ endpoint)
 * - Usage limits (required for project operation)
 * - Default notification platform mappings
 */
@Slf4j
@Singleton
public class DevModeInitService {
    
    private final ApplicationConfig applicationConfig;
    private final ProjectService projectService;
    private final ClickhouseProjectService clickhouseProjectService;
    private final ProjectApiKeyDao apiKeyDao;
    private final ProjectApiKeyEncryptionUtil encryptionUtil;
    private final ConfigService configService;
    private final UsageLimitService usageLimitService;
    private final NotificationService notificationService;
    private final MysqlClient mysqlClient;
    
    private static final String DEFAULT_PROJECT_ID = "default-project";
    private static final String SYSTEM_USER = "system";
    private static final String DEV_KEY_DISPLAY_NAME = "Default Dev Key (Hardcoded)";
    
    @Inject
    public DevModeInitService(
        ApplicationConfig applicationConfig,
        ProjectService projectService,
        ClickhouseProjectService clickhouseProjectService,
        ProjectApiKeyDao apiKeyDao,
        ProjectApiKeyEncryptionUtil encryptionUtil,
        ConfigService configService,
        UsageLimitService usageLimitService,
        NotificationService notificationService,
        MysqlClient mysqlClient
    ) {
        this.applicationConfig = applicationConfig;
        this.projectService = projectService;
        this.clickhouseProjectService = clickhouseProjectService;
        this.apiKeyDao = apiKeyDao;
        this.encryptionUtil = encryptionUtil;
        this.configService = configService;
        this.usageLimitService = usageLimitService;
        this.notificationService = notificationService;
        this.mysqlClient = mysqlClient;
    }
    
    /**
     * Initialize development mode resources if OAuth is disabled.
     * Ensures default-project has all necessary components:
     * 1. Hardcoded API key
     * 2. ClickHouse credentials and user
     * 3. Initial SDK config
     * 4. Usage limits
     * 5. Default notification platform mappings
     */
    public Completable initializeDevMode() {
        // Only run in dev mode (when Google OAuth is disabled)
        Boolean oauthEnabled = applicationConfig.getGoogleOAuthEnabled();
        if (oauthEnabled == null || oauthEnabled) {
            log.debug("Dev mode initialization skipped (OAuth is enabled)");
            return Completable.complete();
        }
        
        String devApiKey = applicationConfig.getDevModeApiKey();
        log.info("Dev mode detected: Initializing development resources...");
        log.info("Dev mode API key: {}", devApiKey);
        
        return ensureDefaultProjectHardcodedApiKey(devApiKey)
            .andThen(ensureDefaultProjectClickhouseUser())
            .andThen(ensureInitialSdkConfig())
            .andThen(ensureUsageLimits())
            .andThen(ensureNotificationMappings())
            .doOnComplete(() -> log.info("Dev mode initialization completed successfully"))
            .doOnError(error -> log.error("Dev mode initialization failed", error));
    }
    
    /**
     * Ensures default-project has the hardcoded dev mode API key.
     * If the key doesn't exist, creates it with the predefined value.
     */
    private Completable ensureDefaultProjectHardcodedApiKey(String devApiKey) {
        return projectService.projectExists(DEFAULT_PROJECT_ID)
            .flatMapCompletable(exists -> {
                if (!exists) {
                    log.warn("default-project does not exist - skipping API key creation");
                    return Completable.complete();
                }
                
                // Check if the hardcoded key already exists by verifying with its digest + salt
                // We need to check all keys since digest includes salt
                EncryptedData testEncrypted = encryptionUtil.encrypt(devApiKey);
                String testDigest = encryptionUtil.generateDigest(devApiKey + testEncrypted.getSalt());
                
                return apiKeyDao.getApiKeyByDigest(testDigest)
                    .isEmpty()
                    .flatMapCompletable(keyNotFound -> {
                        if (keyNotFound) {
                            log.info("Hardcoded dev API key not found - creating it");
                            return createHardcodedDevApiKey(devApiKey);
                        } else {
                            log.debug("Hardcoded dev API key already exists for default-project");
                            return Completable.complete();
                        }
                    });
            });
    }
    
    /**
     * Creates the hardcoded dev mode API key for default-project.
     */
    private Completable createHardcodedDevApiKey(String devApiKey) {
        // Encrypt the hardcoded key
        EncryptedData encrypted = encryptionUtil.encrypt(devApiKey);
        String digest = encryptionUtil.generateDigest(devApiKey + encrypted.getSalt());
        
        return apiKeyDao.createApiKey(
            DEFAULT_PROJECT_ID,
            DEV_KEY_DISPLAY_NAME,
            encrypted.getEncryptedValue(),
            encrypted.getSalt(),
            digest,
            null, // Never expires
            SYSTEM_USER
        )
        .doOnSuccess(apiKey -> 
            log.info("Created hardcoded dev API key for default-project: keyId={}, key={}", 
                apiKey.getProjectApiKeyId(), devApiKey)
        )
        .ignoreElement();
    }
    
    /**
     * Ensures default-project has ClickHouse credentials and user.
     * If not found, sets up ClickHouse user and policies.
     */
    private Completable ensureDefaultProjectClickhouseUser() {
        return projectService.projectExists(DEFAULT_PROJECT_ID)
            .flatMapCompletable(exists -> {
                if (!exists) {
                    log.warn("default-project does not exist - skipping ClickHouse setup");
                    return Completable.complete();
                }
                
                // Try to setup ClickHouse user - setupProjectClickhouseUser is idempotent
                // and will handle cases where credentials already exist
                return clickhouseProjectService.setupProjectClickhouseUser(DEFAULT_PROJECT_ID, SYSTEM_USER)
                    .doOnComplete(() -> log.info("ClickHouse credentials ensured for default-project"))
                    .onErrorComplete(error -> {
                        // If it fails because user already exists, that's fine
                        if (error.getMessage() != null && 
                            (error.getMessage().contains("already exists") || 
                             error.getMessage().contains("Duplicate entry"))) {
                            log.debug("ClickHouse credentials already exist for default-project");
                            return true;
                        }
                        log.warn("Failed to ensure ClickHouse credentials for default-project: {}", 
                            error.getMessage(), error);
                        return true; // Don't fail dev mode init if ClickHouse setup fails
                    });
            });
    }
    
    /**
     * Ensures default-project has initial SDK config.
     * This is required for /v1/configs/active/ endpoint to work.
     */
    private Completable ensureInitialSdkConfig() {
        return projectService.projectExists(DEFAULT_PROJECT_ID)
            .flatMapCompletable(exists -> {
                if (!exists) {
                    log.warn("default-project does not exist - skipping SDK config creation");
                    return Completable.complete();
                }
                
                // ConfigService.createInitialConfig requires a database connection
                // We need to wrap it in a transaction
                return mysqlClient.getWriterPool().rxGetConnection()
                    .flatMapCompletable(conn -> 
                        configService.createInitialConfig(conn, DEFAULT_PROJECT_ID, SYSTEM_USER)
                            .doOnSuccess(config -> log.info("Initial SDK config ensured for default-project: version={}", config.getVersion()))
                            .ignoreElement()
                            .doFinally(conn::close)
                    )
                    .onErrorComplete(error -> {
                        if (error.getMessage() != null && error.getMessage().contains("already exists")) {
                            log.debug("SDK config already exists for default-project");
                            return true;
                        }
                        log.warn("Failed to ensure SDK config for default-project: {}", error.getMessage(), error);
                        return false; // Don't silently swallow non-duplicate errors
                    });
            });
    }
    
    /**
     * Ensures default-project has usage limits configured.
     * This is required for project operation and limits enforcement.
     */
    private Completable ensureUsageLimits() {
        return projectService.projectExists(DEFAULT_PROJECT_ID)
            .flatMapCompletable(exists -> {
                if (!exists) {
                    log.warn("default-project does not exist - skipping usage limits creation");
                    return Completable.complete();
                }
                
                // UsageLimitService.createInitialLimits requires a database connection
                return mysqlClient.getWriterPool().rxGetConnection()
                    .flatMapCompletable(conn ->
                        usageLimitService.createInitialLimits(conn, DEFAULT_PROJECT_ID, SYSTEM_USER)
                            .doOnSuccess(limit -> log.info("Usage limits ensured for default-project"))
                            .ignoreElement()
                            .doFinally(conn::close)
                    )
                    .onErrorComplete(error -> {
                        if (error.getMessage() != null && error.getMessage().contains("already exists")) {
                            log.debug("Usage limits already exist for default-project");
                            return true;
                        }
                        log.warn("Failed to ensure usage limits for default-project: {}", error.getMessage(), error);
                        return false;
                    });
            });
    }
    
    /**
     * Ensures default-project has default notification platform mappings.
     * This sets up default channel-event mappings for notifications.
     */
    private Completable ensureNotificationMappings() {
        return projectService.projectExists(DEFAULT_PROJECT_ID)
            .flatMapCompletable(exists -> {
                if (!exists) {
                    log.warn("default-project does not exist - skipping notification mappings creation");
                    return Completable.complete();
                }
                
                return notificationService.createDefaultPlatformMappings(DEFAULT_PROJECT_ID)
                    .doOnSuccess(mappings -> log.info("Default notification mappings ensured for default-project: {} mappings", mappings.size()))
                    .ignoreElement()
                    .onErrorComplete(error -> {
                        // Notification mappings are not critical for dev mode
                        log.warn("Failed to create notification mappings for default-project (non-critical): {}", error.getMessage());
                        return true;
                    });
            });
    }
    
    /**
     * Get the configured dev mode API key (for logging/debugging).
     */
    public String getDevModeApiKey() {
        return applicationConfig.getDevModeApiKey();
    }
}
