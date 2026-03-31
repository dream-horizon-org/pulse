package org.dreamhorizon.pulseserver.service.session;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.dao.session.SessionListingFilterCategory;
import org.dreamhorizon.pulseserver.dao.session.SessionListingFilterField;
import org.dreamhorizon.pulseserver.dao.session.SessionListingQuickFilter;
import org.dreamhorizon.pulseserver.resources.session.models.FilterConfigResponse;
import org.dreamhorizon.pulseserver.resources.session.models.FilterConfigResponse.CategoryItem;
import org.dreamhorizon.pulseserver.resources.session.models.FilterConfigResponse.FieldItem;
import org.dreamhorizon.pulseserver.resources.session.models.FilterConfigResponse.OperatorItem;
import org.dreamhorizon.pulseserver.resources.session.models.FilterConfigResponse.QuickFilterItem;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class FilterConfigService {

    private final FilterConfigResponse cached;

    @Inject
    public FilterConfigService() {
        this.cached = buildConfig();
    }

    public FilterConfigResponse getFilterConfig() {
        return cached;
    }

    private static FilterConfigResponse buildConfig() {
        List<QuickFilterItem> quick = Arrays.stream(SessionListingQuickFilter.values())
                .map(qf -> QuickFilterItem.builder()
                        .key(qf.name())
                        .displayName(qf.getDisplayName())
                        .description(qf.getDescription())
                        .build())
                .collect(Collectors.toList());

        Map<SessionListingFilterCategory, List<SessionListingFilterField>> fieldsByCategory =
                Arrays.stream(SessionListingFilterField.values())
                .collect(Collectors.groupingBy(SessionListingFilterField::getCategory));

        List<CategoryItem> advanced = Arrays.stream(SessionListingFilterCategory.values())
                .filter(fieldsByCategory::containsKey)
                .map(cat -> CategoryItem.builder()
                        .categoryKey(cat.name())
                        .displayName(cat.getDisplayName())
                        .fields(fieldsByCategory.get(cat).stream()
                                .map(FilterConfigService::toFieldItem)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return FilterConfigResponse.builder()
                .quick(quick)
                .advanced(advanced)
                .build();
    }

    private static FieldItem toFieldItem(SessionListingFilterField field) {
        List<OperatorItem> operators = field.getAllowedOperators().stream()
                .sorted()
                .map(op -> OperatorItem.builder()
                        .key(op.name())
                        .label(op.getDisplayName())
                        .valueType(op.getValueType().name().toLowerCase())
                        .build())
                .collect(Collectors.toList());

        return FieldItem.builder()
                .key(field.name())
                .displayName(field.getDisplayName())
                .dataType(field.getDataType())
                .allowedOperators(operators)
                .build();
    }
}
