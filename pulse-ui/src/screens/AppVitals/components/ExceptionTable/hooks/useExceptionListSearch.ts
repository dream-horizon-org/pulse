import { useState } from "react";
import { useDebouncedValue } from "@mantine/hooks";
import { EXCEPTION_LIST_SEARCH_DEBOUNCE_MS } from "../exceptionList.constants";

export function useExceptionListSearch() {
  const [searchInput, setSearchInput] = useState("");
  const [debouncedSearch] = useDebouncedValue(
    searchInput.trim(),
    EXCEPTION_LIST_SEARCH_DEBOUNCE_MS,
  );

  return {
    searchInput,
    setSearchInput,
    /** Pass to list/count hooks; undefined when empty */
    searchQuery: debouncedSearch || undefined,
    /** For empty-state copy */
    activeSearchQuery: debouncedSearch || undefined,
  };
}
