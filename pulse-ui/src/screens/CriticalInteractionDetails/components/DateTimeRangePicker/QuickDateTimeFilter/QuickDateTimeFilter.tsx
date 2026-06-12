import { UnstyledButton } from "@mantine/core";
import classes from "./QuickDateTimeFilter.module.css";
import { CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS } from "../../../../../constants";
import { QuickDateTimeFilterProps } from "./QuickDateTimeFilter.interface";

export function QuickDateTimeFilter({
  handleChangeFilter,
  active,
  defaultQuickTimeOptions = CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS,
}: QuickDateTimeFilterProps) {
  const controls = defaultQuickTimeOptions.map((item, index) => (
    <UnstyledButton
      key={item.value}
      className={`${classes.control} ${active === index ? classes.indicator : ""}`}
      onClick={() => handleChangeFilter(index)}
    >
      <span className={classes.controlLabel}>{item.label}</span>
    </UnstyledButton>
  ));

  return <div className={classes.root}>{controls}</div>;
}
