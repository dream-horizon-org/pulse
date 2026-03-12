import { useMemo } from "react";
import { Modal, Stack, Divider } from "@mantine/core";
import type { FilterGroup } from "../../../services/sessionReplay/filterConfig";
import type {
  FilterCategory,
  FilterOperator,
} from "../../../services/sessionReplay/filterConfig";
import type { FilterConfigResponse } from "../../../services/sessionReplay/types";
import { useFilterSchema } from "../hooks/useFilterSchema";
import {
  adaptSessionsFilterConfig,
  buildOperatorLabelsFromConfig,
} from "./AdvancedFilterBuilder/adapters";
import type { AdaptedSchema } from "./AdvancedFilterBuilder/adapters";
import { useAdvancedFilterState } from "./AdvancedFilterBuilder/useAdvancedFilterState";
import { AdvancedFilterModalTitle } from "./AdvancedFilterBuilder/ModalTitle";
import { FilterOperatorBar } from "./AdvancedFilterBuilder/FilterOperatorBar";
import { FilterConditionsList } from "./AdvancedFilterBuilder/FilterConditionsList";
import { AdvancedFilterModalFooter } from "./AdvancedFilterBuilder/ModalFooter";
import { SchemaLoadingState } from "./AdvancedFilterBuilder/SchemaLoadingState";
import { SchemaErrorState } from "./AdvancedFilterBuilder/SchemaErrorState";
import { MODAL_STYLES } from "./AdvancedFilterBuilder/constants";

export interface AdvancedFilterBuilderProps {
  opened: boolean;
  onClose: () => void;
  onApply: (filterGroup: FilterGroup) => void;
  initialFilters?: FilterGroup;
  sessionsFilterConfig?: FilterConfigResponse | null;
}

export function AdvancedFilterBuilder({
  opened,
  onClose,
  onApply,
  initialFilters,
  sessionsFilterConfig,
}: AdvancedFilterBuilderProps) {
  const { schema: legacySchema, loading: schemaLoading } = useFilterSchema({
    skip: !!sessionsFilterConfig,
  });

  const effectiveSchema = useMemo((): AdaptedSchema | null => {
    if (sessionsFilterConfig) {
      return adaptSessionsFilterConfig(sessionsFilterConfig);
    }
    if (legacySchema) {
      return {
        categories: legacySchema.categories.map((cat) => ({
          key: cat.key,
          label: cat.label,
          fields: cat.fields.map((f) => ({
            ...f,
            category: f.category as FilterCategory,
            operators: f.operators as FilterOperator[],
          })),
        })),
      };
    }
    return null;
  }, [sessionsFilterConfig, legacySchema]);

  const {
    filterGroup,
    setOperator,
    addCondition,
    updateCondition,
    removeCondition,
    handleCategoryChange,
    handleFieldChange,
    handleClear,
    getFieldsByCategory,
    getFieldDefinition,
    categoryOptions,
  } = useAdvancedFilterState({ initialFilters, effectiveSchema });

  const operatorLabels = useMemo(
    () => buildOperatorLabelsFromConfig(sessionsFilterConfig ?? undefined),
    [sessionsFilterConfig],
  );

  const handleApply = () => {
    onApply(filterGroup);
    onClose();
  };

  const showLoading = !sessionsFilterConfig && schemaLoading;
  const showError = !effectiveSchema && !showLoading;

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={<AdvancedFilterModalTitle />}
      size="xl"
      padding="lg"
      styles={{
        body: {
          maxHeight: MODAL_STYLES.bodyMaxHeight,
          display: "flex",
          flexDirection: "column",
        },
      }}
    >
      <Stack gap="md" style={{ flex: 1, minHeight: 0 }}>
        {showLoading ? (
          <SchemaLoadingState />
        ) : showError ? (
          <SchemaErrorState />
        ) : (
          <>
            <FilterOperatorBar
              operator={filterGroup.operator}
              onOperatorChange={setOperator}
              onAddCondition={addCondition}
            />
            <Divider />
            <FilterConditionsList
              conditions={filterGroup.conditions}
              onUpdate={updateCondition}
              onRemove={removeCondition}
              onCategoryChange={handleCategoryChange}
              onFieldChange={handleFieldChange}
              getFieldsByCategory={getFieldsByCategory}
              getFieldDefinition={getFieldDefinition}
              categoryOptions={categoryOptions}
              operatorLabels={operatorLabels}
            />
          </>
        )}

        <AdvancedFilterModalFooter
          conditionCount={filterGroup.conditions.length}
          onClear={handleClear}
          onCancel={onClose}
          onApply={handleApply}
        />
      </Stack>
    </Modal>
  );
}
