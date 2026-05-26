import { TextInput, ActionIcon } from "@mantine/core";
import { IconSearch, IconX } from "@tabler/icons-react";
import { EXCEPTION_LIST_SEARCH_PLACEHOLDER } from "./exceptionList.constants";
import classes from "../../AppVitals.module.css";

export interface ExceptionListSearchBarProps {
  value: string;
  onChange: (value: string) => void;
}

export function ExceptionListSearchBar({
  value,
  onChange,
}: ExceptionListSearchBarProps) {
  return (
    <TextInput
      className={classes.exceptionListSearch}
      placeholder={EXCEPTION_LIST_SEARCH_PLACEHOLDER}
      value={value}
      onChange={(e) => onChange(e.currentTarget.value)}
      leftSection={<IconSearch size={16} stroke={1.5} />}
      rightSection={
        value ? (
          <ActionIcon
            variant="subtle"
            color="gray"
            size="sm"
            aria-label="Clear search"
            onClick={() => onChange("")}
          >
            <IconX size={14} />
          </ActionIcon>
        ) : null
      }
      data-test="exception-list-search"
    />
  );
}
