package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilterConfigResponse {

    private final List<QuickFilterItem> quick;
    private final List<CategoryItem> advanced;

    @Getter
    @Builder
    public static class QuickFilterItem {
        private final String key;
        private final String displayName;
        private final String description;
    }

    @Getter
    @Builder
    public static class CategoryItem {
        private final String categoryKey;
        private final String displayName;
        private final List<FieldItem> fields;
    }

    @Getter
    @Builder
    public static class FieldItem {
        private final String key;
        private final String displayName;
        private final String dataType;
        private final List<OperatorItem> allowedOperators;
    }

    @Getter
    @Builder
    public static class OperatorItem {
        private final String key;
        private final String label;
        private final String valueType;
    }
}
