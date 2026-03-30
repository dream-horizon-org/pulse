import { Box, Button, NumberInput, Select, Text } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useFormContext } from "react-hook-form";
import classes from "./RevenueConfiguration.module.css";
import { CriticalInteractionFormData } from "../../CriticalInteractionForm.interface";
import { useState } from "react";

const CURRENCY_OPTIONS = [
  { value: "USD", label: "USD $" },
  { value: "EUR", label: "EUR €" },
  { value: "GBP", label: "GBP £" },
  { value: "INR", label: "INR ₹" },
  { value: "JPY", label: "JPY ¥" },
  { value: "AUD", label: "AUD $" },
];

const CURRENCY_SYMBOLS: Record<string, string> = {
  USD: "$", EUR: "€", GBP: "£", INR: "₹", JPY: "¥", AUD: "A$",
};

type RevenueConfigurationProps = {
  isUpdateFlow: boolean;
  onBackClick: () => void;
  onCreateClick: () => void;
};

export function RevenueConfiguration({
  isUpdateFlow,
  onBackClick,
  onCreateClick,
}: RevenueConfigurationProps) {
  const { setValue, watch } = useFormContext<CriticalInteractionFormData>();
  const revenueValue = watch("revenueValue");
  const currency = watch("currency") || "USD";

  const [localValue, setLocalValue] = useState<number | string>(revenueValue ?? "");

  const handleValueChange = (val: number | string) => {
    setLocalValue(val);
    setValue("revenueValue", val === "" ? undefined : Number(val));
  };

  const handleCurrencyChange = (val: string | null) => {
    setValue("currency", val ?? "USD");
  };

  const symbol = CURRENCY_SYMBOLS[currency] ?? currency;
  const hasValue = typeof localValue === "number" && localValue > 0;

  return (
    <Box className={classes.container}>
      <div className={classes.header}>
        <Text className={classes.sectionTitle}>Revenue per Conversion</Text>
        <Text className={classes.sectionDescription}>
          Set the revenue value for each successful completion of this interaction.
          Pulse will use this to calculate revenue generated and revenue at risk
          when the interaction score changes.
        </Text>
      </div>

      <div className={classes.formCard}>
        <div className={classes.fieldRow}>
          <Select
            className={classes.currencySelect}
            label="Currency"
            data={CURRENCY_OPTIONS}
            value={currency}
            onChange={handleCurrencyChange}
            allowDeselect={false}
          />
          <NumberInput
            className={classes.revenueInput}
            label="Revenue per successful interaction"
            placeholder="e.g. 49.99"
            min={0}
            decimalScale={8}
            value={localValue}
            onChange={handleValueChange}
            leftSection={<span style={{ fontSize: 13, fontWeight: 600, color: "#0ba09a" }}>{symbol}</span>}
            description="Leave blank to skip revenue tracking"
          />
        </div>

        {hasValue && (
          <div className={classes.previewBox}>
            <IconInfoCircle size={14} style={{ color: "#0ba09a", flexShrink: 0 }} />
            <Text className={classes.previewText}>
              Each successful interaction will count as{" "}
              <span className={classes.previewValue}>
                {new Intl.NumberFormat("en-US", {
                  style: "currency",
                  currency,
                  maximumFractionDigits: 2,
                }).format(Number(localValue))}
              </span>{" "}
              revenue. Failures will appear as revenue at risk on the interaction
              details page.
            </Text>
          </div>
        )}

        {!hasValue && (
          <Text className={classes.skipNote}>
            This step is optional — you can skip it and set a value later by editing the interaction.
          </Text>
        )}
      </div>

      <div className={classes.sectionButtons}>
        <Button variant="outline" size="md" onClick={onBackClick}>
          Back
        </Button>
        <Button variant="filled" size="md" onClick={onCreateClick}>
          {isUpdateFlow ? "Update Interaction" : "Create Interaction"}
        </Button>
      </div>
    </Box>
  );
}
