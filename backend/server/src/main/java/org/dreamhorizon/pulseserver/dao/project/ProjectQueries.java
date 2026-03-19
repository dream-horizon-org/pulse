package org.dreamhorizon.pulseserver.dao.project;

public class ProjectQueries {

  private static final String PROJECT_COLUMNS =
      "id, project_id, tenant_id, name, description, is_active, created_by, created_at, updated_at";

  public static final String INSERT_PROJECT =
      "INSERT INTO projects (project_id, tenant_id, name, description, is_active, created_by) "
          + "VALUES (?, ?, ?, ?, ?, ?)";

  public static final String GET_PROJECT_BY_PROJECT_ID =
      "SELECT " + PROJECT_COLUMNS + " FROM projects WHERE project_id = ?";

  public static final String GET_PROJECT_BY_ID =
      "SELECT " + PROJECT_COLUMNS + " FROM projects WHERE id = ?";

  public static final String GET_PROJECTS_BY_TENANT_ID =
      "SELECT " + PROJECT_COLUMNS + " FROM projects WHERE tenant_id = ? AND is_active = TRUE";

  public static final String GET_ALL_PROJECTS_BY_TENANT_ID =
      "SELECT " + PROJECT_COLUMNS + " FROM projects WHERE tenant_id = ?";

  public static final String UPDATE_PROJECT =
      "UPDATE projects SET name = ?, description = ?, updated_at = CURRENT_TIMESTAMP WHERE project_id = ?";

  public static final String DEACTIVATE_PROJECT =
      "UPDATE projects SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE project_id = ?";

  public static final String ACTIVATE_PROJECT =
      "UPDATE projects SET is_active = TRUE, updated_at = CURRENT_TIMESTAMP WHERE project_id = ?";

  public static final String DELETE_PROJECT =
      "DELETE FROM projects WHERE project_id = ?";

  public static final String CHECK_PROJECT_EXISTS =
      "SELECT COUNT(*) as count FROM projects WHERE project_id = ?";

  public static final String CHECK_PROJECT_EXISTS_FOR_TENANT =
      "SELECT COUNT(*) as count FROM projects WHERE project_id = ? AND tenant_id = ?";

  public static final String GET_PROJECT_IDS_BY_TENANT_ID =
      "SELECT project_id FROM projects WHERE tenant_id = ? AND is_active = TRUE";

  public static final String COUNT_PROJECTS_BY_TENANT_ID =
      "SELECT COUNT(*) as count FROM projects WHERE tenant_id = ? AND is_active = TRUE";

  public static final String GET_ACTIVE_PROJECT_COUNT = 
      "SELECT COUNT(*) as count FROM projects WHERE tenant_id = ? AND is_active = TRUE";
}

