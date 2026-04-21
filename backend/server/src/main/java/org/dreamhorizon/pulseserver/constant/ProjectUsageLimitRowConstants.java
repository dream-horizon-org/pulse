package org.dreamhorizon.pulseserver.constant;

import lombok.experimental.UtilityClass;

/**
 * Result-set column labels for project usage limit queries (aliases must match SQL in {@code ProjectUsageLimitQueries}).
 */
@UtilityClass
public class ProjectUsageLimitRowConstants {

  public  static final String THRESHOLDS_NOTIFIED = "thresholds_notified";
  public  static final String PROJECT_NAME = "project_name";
  public  static final String NOTIFICATION_CREATED_AT = "notification_created_at";
  public  static final String PROJECT_ID = "project_id";
  public  static final String ID = "id";
  public  static final String CREATED_AT = "created_at";
  public  static final String UPDATED_AT = "updated_at";
  public static final String NOTIFICATION_PROJECT_USAGE_LIMIT_ID = "notification_project_usage_limit_id";
  public static final String NOTIFICATION_ROW_ACTIVE = "notification_row_active";  
}
