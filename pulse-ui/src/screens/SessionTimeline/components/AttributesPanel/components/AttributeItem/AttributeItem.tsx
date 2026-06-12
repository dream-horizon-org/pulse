import { Text, CopyButton, Tooltip } from "@mantine/core";
import { IconCheck, IconCopy } from "@tabler/icons-react";
import classes from "./AttributeItem.module.css";

interface AttributeItemProps {
  attributeKey: string;
  value: any;
}

const formatValue = (value: any): string => {
  if (value === null || value === undefined) return "null";
  if (typeof value === "object") return JSON.stringify(value, null, 2);
  return String(value);
};

export function AttributeItem({ attributeKey, value }: AttributeItemProps) {
  const valueString = formatValue(value);
  const isLongValue = valueString.length > 100;

  return (
    <div className={classes.item}>
      <div className={classes.keyValue}>
        <Text className={classes.key} size="xs" fw={600}>
          {attributeKey}
        </Text>
        <Text className={classes.value} size="xs">
          {isLongValue ? `${valueString.substring(0, 100)}...` : valueString}
        </Text>
      </div>
      <CopyButton value={valueString} timeout={2000}>
        {({ copied, copy }) => (
          <Tooltip label={copied ? "Copied" : "Copy"} withArrow>
            <button onClick={copy} className={classes.copyButton}>
              {copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
            </button>
          </Tooltip>
        )}
      </CopyButton>
    </div>
  );
}
