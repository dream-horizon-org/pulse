package org.dreamhorizon.pulseserver.dao.user;

/**
 * SQL queries for User DAO operations.
 */
public class UserQueries {
    
    public static final String INSERT_USER = 
        "INSERT INTO users (user_id, email, name, status, firebase_uid, is_active) " +
        "VALUES (?, ?, ?, ?, ?, ?)";
    
    public static final String GET_USER_BY_EMAIL = 
        "SELECT id, user_id, email, name, status, firebase_uid, last_login_at, is_active, created_at, updated_at " +
        "FROM users WHERE LOWER(email) = LOWER(?)";
    
    public static final String GET_USER_BY_ID = 
        "SELECT id, user_id, email, name, status, firebase_uid, last_login_at, is_active, created_at, updated_at " +
        "FROM users WHERE user_id = ?";
    
    public static final String GET_USERS_BY_IDS = 
        "SELECT id, user_id, email, name, status, firebase_uid, last_login_at, is_active, created_at, updated_at " +
        "FROM users WHERE user_id IN (%s)";
    
    public static final String UPDATE_USER = 
        "UPDATE users SET name = ? WHERE user_id = ?";
    
    public static final String ACTIVATE_USER = 
        "UPDATE users SET status = 'active', firebase_uid = ?, name = ?, last_login_at = NOW() " +
        "WHERE user_id = ?";
    
    public static final String UPDATE_LAST_LOGIN = 
        "UPDATE users SET last_login_at = NOW() WHERE user_id = ?";
    
    public static final String DEACTIVATE_USER = 
        "UPDATE users SET is_active = FALSE WHERE user_id = ?";
    
    public static final String REACTIVATE_USER = 
        "UPDATE users SET is_active = TRUE WHERE user_id = ?";
}
