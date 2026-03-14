package org.dreamhorizon.pulseserver.dao.eventdefinition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EventDefinitionQueriesTest {

  @Nested
  class TestBuildListQuery {

    @Test
    void shouldBuildBaseListQueryWithoutFilters() {
      String query = EventDefinitionQueries.buildListQuery(false, false);

      assertThat(query).contains("SELECT ed.id");
      assertThat(query).contains("FROM event_definitions ed");
      assertThat(query).contains("WHERE ed.project_id = ?");
      assertThat(query).contains("AND ed.is_archived = FALSE");
      assertThat(query).contains("ORDER BY ed.event_name LIMIT ? OFFSET ?");
      assertThat(query).doesNotContain("LIKE CONCAT");
      assertThat(query).doesNotContain("AND ed.category = ?");
    }

    @Test
    void shouldIncludeSearchClauseWhenSearchEnabled() {
      String query = EventDefinitionQueries.buildListQuery(true, false);

      assertThat(query).contains("LIKE CONCAT('%', ?, '%')");
      assertThat(query).contains("ed.event_name LIKE");
      assertThat(query).contains("ed.description LIKE");
      assertThat(query).contains("ed.category LIKE");
      assertThat(query).doesNotContain("AND ed.category = ?");
    }

    @Test
    void shouldIncludeCategoryClauseWhenCategoryEnabled() {
      String query = EventDefinitionQueries.buildListQuery(false, true);

      assertThat(query).contains("AND ed.category = ?");
      assertThat(query).doesNotContain("LIKE CONCAT");
    }

    @Test
    void shouldIncludeBothClausesWhenBothEnabled() {
      String query = EventDefinitionQueries.buildListQuery(true, true);

      assertThat(query).contains("LIKE CONCAT('%', ?, '%')");
      assertThat(query).contains("AND ed.category = ?");
      assertThat(query).contains("ORDER BY ed.event_name LIMIT ? OFFSET ?");
    }

    @Test
    void shouldHaveLimitAndOffsetAtEnd() {
      String query = EventDefinitionQueries.buildListQuery(true, true);

      assertThat(query).endsWith("ORDER BY ed.event_name LIMIT ? OFFSET ?");
    }
  }

  @Nested
  class TestBuildCountQuery {

    @Test
    void shouldBuildBaseCountQueryWithoutFilters() {
      String query = EventDefinitionQueries.buildCountQuery(false, false);

      assertThat(query).contains("SELECT COUNT(*) AS total_count");
      assertThat(query).contains("FROM event_definitions ed");
      assertThat(query).contains("WHERE ed.project_id = ?");
      assertThat(query).contains("AND ed.is_archived = FALSE");
      assertThat(query).doesNotContain("LIKE");
      assertThat(query).doesNotContain("AND ed.category = ?");
      assertThat(query).doesNotContain("LIMIT");
      assertThat(query).doesNotContain("OFFSET");
    }

    @Test
    void shouldIncludeSearchClauseInCountQuery() {
      String query = EventDefinitionQueries.buildCountQuery(true, false);

      assertThat(query).contains("LIKE CONCAT('%', ?, '%')");
      assertThat(query).doesNotContain("AND ed.category = ?");
    }

    @Test
    void shouldIncludeCategoryClauseInCountQuery() {
      String query = EventDefinitionQueries.buildCountQuery(false, true);

      assertThat(query).contains("AND ed.category = ?");
      assertThat(query).doesNotContain("LIKE");
    }

    @Test
    void shouldIncludeBothClausesInCountQuery() {
      String query = EventDefinitionQueries.buildCountQuery(true, true);

      assertThat(query).contains("LIKE CONCAT('%', ?, '%')");
      assertThat(query).contains("AND ed.category = ?");
    }

    @Test
    void shouldNotContainLimitOrOffset() {
      String query = EventDefinitionQueries.buildCountQuery(true, true);

      assertThat(query).doesNotContain("LIMIT");
      assertThat(query).doesNotContain("OFFSET");
    }
  }

  @Nested
  class TestGetAttributesForEventsBatch {

    @Test
    void shouldBuildQueryForSingleEvent() {
      String query = EventDefinitionQueries.getAttributesForEventsBatch(1);

      assertThat(query).contains("WHERE ead.event_definition_id IN (?)");
      assertThat(query).contains("AND ead.is_archived = FALSE");
    }

    @Test
    void shouldBuildQueryForMultipleEvents() {
      String query = EventDefinitionQueries.getAttributesForEventsBatch(3);

      assertThat(query).contains("IN (?, ?, ?)");
    }

    @Test
    void shouldBuildQueryForLargeCount() {
      String query = EventDefinitionQueries.getAttributesForEventsBatch(5);

      assertThat(query).contains("IN (?, ?, ?, ?, ?)");
      assertThat(query).contains("SELECT ead.id");
      assertThat(query).contains("FROM event_attribute_definitions ead");
    }

    @Test
    void shouldSelectAllRequiredColumns() {
      String query = EventDefinitionQueries.getAttributesForEventsBatch(1);

      assertThat(query).contains("ead.id");
      assertThat(query).contains("ead.event_definition_id");
      assertThat(query).contains("ead.attribute_name");
      assertThat(query).contains("ead.description");
      assertThat(query).contains("ead.data_type");
      assertThat(query).contains("ead.is_required");
      assertThat(query).contains("ead.is_archived");
    }
  }

  @Nested
  class TestStaticQueries {

    @Test
    void shouldHaveValidInsertEventDefinition() {
      assertThat(EventDefinitionQueries.INSERT_EVENT_DEFINITION)
          .contains("INSERT INTO event_definitions");
      assertThat(EventDefinitionQueries.INSERT_EVENT_DEFINITION)
          .contains("project_id, event_name, display_name, description, category, created_by, updated_by");
    }

    @Test
    void shouldHaveValidUpsertEventDefinition() {
      assertThat(EventDefinitionQueries.UPSERT_EVENT_DEFINITION)
          .contains("INSERT INTO event_definitions");
      assertThat(EventDefinitionQueries.UPSERT_EVENT_DEFINITION)
          .contains("ON DUPLICATE KEY UPDATE");
    }

    @Test
    void shouldHaveValidUpdateEventDefinition() {
      assertThat(EventDefinitionQueries.UPDATE_EVENT_DEFINITION)
          .contains("UPDATE event_definitions SET");
      assertThat(EventDefinitionQueries.UPDATE_EVENT_DEFINITION)
          .contains("WHERE id = ? AND project_id = ?");
    }

    @Test
    void shouldHaveValidArchiveQuery() {
      assertThat(EventDefinitionQueries.ARCHIVE_EVENT_DEFINITION)
          .contains("SET is_archived = TRUE");
      assertThat(EventDefinitionQueries.ARCHIVE_EVENT_DEFINITION)
          .contains("WHERE id = ? AND project_id = ?");
    }

    @Test
    void shouldHaveValidGetAttributesQuery() {
      assertThat(EventDefinitionQueries.GET_ATTRIBUTES_FOR_EVENT)
          .contains("WHERE ead.event_definition_id = ?");
      assertThat(EventDefinitionQueries.GET_ATTRIBUTES_FOR_EVENT)
          .contains("AND ead.is_archived = FALSE");
    }

    @Test
    void shouldHaveValidGetByIdQuery() {
      assertThat(EventDefinitionQueries.GET_EVENT_DEFINITION_BY_ID)
          .contains("WHERE ed.id = ? AND ed.project_id = ?");
    }

    @Test
    void shouldHaveValidCategoriesQuery() {
      assertThat(EventDefinitionQueries.GET_DISTINCT_CATEGORIES)
          .contains("SELECT DISTINCT category");
      assertThat(EventDefinitionQueries.GET_DISTINCT_CATEGORIES)
          .contains("WHERE project_id = ?");
      assertThat(EventDefinitionQueries.GET_DISTINCT_CATEGORIES)
          .contains("AND category IS NOT NULL");
    }

    @Test
    void shouldHaveValidUpsertAttributeQuery() {
      assertThat(EventDefinitionQueries.UPSERT_ATTRIBUTE)
          .contains("INSERT INTO event_attribute_definitions");
      assertThat(EventDefinitionQueries.UPSERT_ATTRIBUTE)
          .contains("ON DUPLICATE KEY UPDATE");
      assertThat(EventDefinitionQueries.UPSERT_ATTRIBUTE)
          .contains("is_archived = FALSE");
    }
  }
}
