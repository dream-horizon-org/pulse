import classes from "./InteractionDetailsFilters.module.css";

export function LeftSectionTextInput({ value }: { value: string }) {
  return <span className={classes.leftSectionInputText}>{value}</span>;
}
