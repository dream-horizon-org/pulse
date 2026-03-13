import { useState, useMemo, useCallback } from "react";
import { v4 as uuidv4 } from "uuid";
import type {
  FilterCondition,
  FilterGroup,
  FilterCategory,
  FilterFieldDefinition,
  FilterOperator,
} from "../../../../services/sessionReplay/filterConfig";
import {
  DEFAULT_FIRST_CATEGORY,
  DEFAULT_FIRST_FIELD,
  DEFAULT_OPERATOR,
} from "./constants";
import type { AdaptedSchema } from "./adapters";

export interface UseAdvancedFilterStateParams {
  initialFilters: FilterGroup | undefined;
  effectiveSchema: AdaptedSchema | null;
}

export interface UseAdvancedFilterStateResult {
  filterGroup: FilterGroup;
  setOperator: (op: "AND" | "OR") => void;
  addCondition: () => void;
  updateCondition: (id: string, updates: Partial<FilterCondition>) => void;
  removeCondition: (id: string) => void;
  handleCategoryChange: (id: string, category: FilterCategory) => void;
  handleFieldChange: (id: string, fieldKey: string) => void;
  handleClear: () => void;
  getFieldsByCategory: (category: FilterCategory) => FilterFieldDefinition[];
  getFieldDefinition: (fieldKey: string) => FilterFieldDefinition | null;
  categoryOptions: Array<{ value: string; label: string }>;
}

export function useAdvancedFilterState({
  initialFilters,
  effectiveSchema,
}: UseAdvancedFilterStateParams): UseAdvancedFilterStateResult {
  const [filterGroup, setFilterGroup] = useState<FilterGroup>(
    initialFilters ?? {
      id: uuidv4(),
      operator: "AND",
      conditions: [],
    },
  );

  const getFieldsByCategory = useCallback(
    (category: FilterCategory): FilterFieldDefinition[] => {
      if (!effectiveSchema) return [];
      const categoryData = effectiveSchema.categories.find(
        (c) => c.key === category,
      );
      return categoryData?.fields ?? [];
    },
    [effectiveSchema],
  );

  const getFieldDefinition = useCallback(
    (fieldKey: string): FilterFieldDefinition | null => {
      if (!effectiveSchema) return null;
      for (const category of effectiveSchema.categories) {
        const field = category.fields.find((f) => f.key === fieldKey);
        if (field) return field;
      }
      return null;
    },
    [effectiveSchema],
  );

  const categoryOptions = useMemo(() => {
    if (!effectiveSchema) return [];
    return effectiveSchema.categories.map((cat) => ({
      value: cat.key,
      label: cat.label,
    }));
  }, [effectiveSchema]);

  const setOperator = useCallback((op: "AND" | "OR") => {
    setFilterGroup((prev) => ({ ...prev, operator: op }));
  }, []);

  const addCondition = useCallback(() => {
    const firstCategory = effectiveSchema?.categories[0];
    const firstField = firstCategory?.fields[0];
    const newCondition: FilterCondition = {
      id: uuidv4(),
      category: (firstCategory?.key ??
        DEFAULT_FIRST_CATEGORY) as FilterCategory,
      field: firstField?.key ?? DEFAULT_FIRST_FIELD,
      operator: (firstField?.operators[0] ??
        DEFAULT_OPERATOR) as FilterOperator,
      value: firstField?.type === "boolean" ? true : "",
    };
    setFilterGroup((prev) => ({
      ...prev,
      conditions: [...prev.conditions, newCondition],
    }));
  }, [effectiveSchema]);

  const updateCondition = useCallback(
    (id: string, updates: Partial<FilterCondition>) => {
      setFilterGroup((prev) => ({
        ...prev,
        conditions: prev.conditions.map((c) =>
          c.id === id ? { ...c, ...updates } : c,
        ),
      }));
    },
    [],
  );

  const removeCondition = useCallback((id: string) => {
    setFilterGroup((prev) => ({
      ...prev,
      conditions: prev.conditions.filter((c) => c.id !== id),
    }));
  }, []);

  const handleCategoryChange = useCallback(
    (id: string, category: FilterCategory) => {
      const categoryFields = getFieldsByCategory(category);
      const firstField = categoryFields[0];
      if (firstField) {
        updateCondition(id, {
          category,
          field: firstField.key,
          operator: firstField.operators[0],
          value: firstField.type === "boolean" ? true : "",
        });
      }
    },
    [getFieldsByCategory, updateCondition],
  );

  const handleFieldChange = useCallback(
    (id: string, fieldKey: string) => {
      const fieldDef = getFieldDefinition(fieldKey);
      if (fieldDef) {
        updateCondition(id, {
          field: fieldKey,
          operator: fieldDef.operators[0],
          value: fieldDef.type === "boolean" ? true : "",
        });
      }
    },
    [getFieldDefinition, updateCondition],
  );

  const handleClear = useCallback(() => {
    setFilterGroup({
      id: uuidv4(),
      operator: "AND",
      conditions: [],
    });
  }, []);

  return {
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
  };
}
