package org.dreamhorizon.pulseserver.dao.user;

import static org.dreamhorizon.pulseserver.dao.user.UserQueries.*;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Data Access Object for User operations.
 * Handles CRUD operations for user profiles.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class UserDao {
    private final MysqlClient mysqlClient;

    /**
     * Create a new user in the database.
     * @param user User object to create
     * @return Single containing the created user with generated ID
     */
    public Single<User> createUser(User user) {
        MySQLPool pool = mysqlClient.getWriterPool();
        
        // Set default status if not provided
        String status = user.getStatus() != null ? user.getStatus() : "pending";
        
        return pool.preparedQuery(INSERT_USER)
            .rxExecute(
                Tuple.of(
                    user.getUserId(),
                    user.getEmail(),
                    user.getName(),
                    status,
                    user.getFirebaseUid(),
                    user.getIsActive() != null ? user.getIsActive() : Boolean.TRUE))
            .map(result -> {
                log.info("Created user: {} ({}) with status: {}", user.getEmail(), user.getUserId(), status);
                return User.builder()
                    .userId(user.getUserId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .status(status)
                    .isActive(true)
                    .build();
            })
            .doOnError(error -> log.error("Failed to create user: {}", user.getEmail(), error));
    }

    /**
     * Get user by email address (case-insensitive).
     * @param email User email
     * @return Maybe containing the user if found
     */
    public Maybe<User> getUserByEmail(String email) {
        MySQLPool pool = mysqlClient.getReaderPool();
        return pool.preparedQuery(GET_USER_BY_EMAIL)
            .rxExecute(Tuple.of(email))
            .flatMapMaybe(rowSet -> {
                if (rowSet.size() == 0) {
                    log.debug("User not found by email: {}", email);
                    return Maybe.empty();
                }
                return Maybe.just(mapRowToUser(rowSet.iterator().next()));
            })
            .doOnError(error -> log.error("Failed to fetch user by email: {}", email, error));
    }

    /**
     * Get user by user ID.
     * @param userId User ID (format: user-{uuid})
     * @return Maybe containing the user if found
     */
    public Maybe<User> getUserById(String userId) {
        MySQLPool pool = mysqlClient.getReaderPool();
        return pool.preparedQuery(GET_USER_BY_ID)
            .rxExecute(Tuple.of(userId))
            .flatMapMaybe(rowSet -> {
                if (rowSet.size() == 0) {
                    log.debug("User not found by ID: {}", userId);
                    return Maybe.empty();
                }
                return Maybe.just(mapRowToUser(rowSet.iterator().next()));
            })
            .doOnError(error -> log.error("Failed to fetch user by ID: {}", userId, error));
    }

    /**
     * Get multiple users by their IDs (batch fetch).
     * @param userIds List of user IDs
     * @return Single containing list of users (may be empty if none found)
     */
    public Single<List<User>> getUsersByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Single.just(new ArrayList<>());
        }
        
        MySQLPool pool = mysqlClient.getReaderPool();
        
        // Build placeholders for IN clause
        String placeholders = userIds.stream()
            .map(id -> "?")
            .collect(Collectors.joining(", "));
        
        String query = String.format(GET_USERS_BY_IDS, placeholders);
        
        // Create tuple with all user IDs
        Tuple tuple = Tuple.tuple();
        for (String userId : userIds) {
            tuple.addString(userId);
        }
        
        return pool.preparedQuery(query)
            .rxExecute(tuple)
            .map(rowSet -> {
                List<User> users = new ArrayList<>();
                for (Row row : rowSet) {
                    users.add(mapRowToUser(row));
                }
                log.debug("Fetched {} users by IDs (requested {})", users.size(), userIds.size());
                return users;
            })
            .doOnError(error -> log.error("Failed to fetch users by IDs: count={}", userIds.size(), error));
    }

    /**
     * Activate a pending user on first login.
     * Updates status, firebase_uid, name, and last_login_at.
     * 
     * @param userId User ID
     * @param firebaseUid Firebase UID from authentication
     * @param name User's full name
     * @return Completable that completes when activation is successful
     */
    public Completable activateUser(String userId, String firebaseUid, String name) {
        MySQLPool pool = mysqlClient.getWriterPool();
        return pool.preparedQuery(ACTIVATE_USER)
            .rxExecute(Tuple.of(firebaseUid, name, userId))
            .flatMapCompletable(result -> {
                if (result.rowCount() == 0) {
                    return Completable.error(new RuntimeException("User not found: " + userId));
                }
                log.info("Activated user: {} (firebaseUid={})", userId, firebaseUid);
                return Completable.complete();
            })
            .doOnError(error -> log.error("Failed to activate user: {}", userId, error));
    }

    /**
     * Update last login timestamp for a user.
     * @param userId User ID
     * @return Completable that completes when update is successful
     */
    public Completable updateLastLogin(String userId) {
        MySQLPool pool = mysqlClient.getWriterPool();
        return pool.preparedQuery(UPDATE_LAST_LOGIN)
            .rxExecute(Tuple.of(userId))
            .flatMapCompletable(result -> {
                if (result.rowCount() == 0) {
                    log.warn("No user found to update last login: {}", userId);
                }
                log.debug("Updated last login for user: {}", userId);
                return Completable.complete();
            })
            .doOnError(error -> log.error("Failed to update last login: {}", userId, error));
    }

    /**
     * Update user profile information.
     * @param userId User ID
     * @param name Updated name
     * @return Completable that completes when update is successful
     */
    public Completable updateUser(String userId, String name) {
        MySQLPool pool = mysqlClient.getWriterPool();
        return pool.preparedQuery(UPDATE_USER)
            .rxExecute(Tuple.of(name, userId))
            .flatMapCompletable(result -> {
                if (result.rowCount() == 0) {
                    return Completable.error(new RuntimeException("User not found: " + userId));
                }
                log.info("Updated user: {}", userId);
                return Completable.complete();
            })
            .doOnError(error -> log.error("Failed to update user: {}", userId, error));
    }

    /**
     * Deactivate a user account.
     * @param userId User ID
     * @return Completable that completes when deactivation is successful
     */
    public Completable deactivateUser(String userId) {
        MySQLPool pool = mysqlClient.getWriterPool();
        return pool.preparedQuery(DEACTIVATE_USER)
            .rxExecute(Tuple.of(userId))
            .flatMapCompletable(result -> {
                if (result.rowCount() == 0) {
                    log.warn("No user found to deactivate: {}", userId);
                }
                log.info("Deactivated user: {}", userId);
                return Completable.complete();
            })
            .doOnError(error -> log.error("Failed to deactivate user: {}", userId, error));
    }

    /**
     * Maps a database row to a User object.
     */
    private User mapRowToUser(Row row) {
        return User.builder()
            .id(row.getLong("id"))
            .userId(row.getString("user_id"))
            .email(row.getString("email"))
            .name(row.getString("name"))
            .status(row.getString("status"))
            .firebaseUid(row.getString("firebase_uid"))
            .lastLoginAt(row.getLocalDateTime("last_login_at") != null ? 
                row.getLocalDateTime("last_login_at").toString() : null)
            .isActive(row.getBoolean("is_active"))
            .createdAt(row.getLocalDateTime("created_at") != null ? 
                row.getLocalDateTime("created_at").toString() : null)
            .updatedAt(row.getLocalDateTime("updated_at") != null ? 
                row.getLocalDateTime("updated_at").toString() : null)
            .build();
    }
}
