import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Anchor,
  Autocomplete,
  Button,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Text,
  TextInput,
} from "@mantine/core";
import { IconAlertCircle, IconCoin } from "@tabler/icons-react";
import { useGetFunnelEvents } from "../../../hooks/useGetFunnelData";
import {
  DEFAULT_CONVERSION_WINDOW_HOURS,
  DEFAULT_REVENUE_EVENT_PREVIEW_DAYS,
  NUMERIC_ATTRIBUTE_TYPES,
  RevenueEventConfig,
} from "../RevenueEvent.types";
import { useEventDefinitions } from "../hooks/useEventDefinitions";
import {
  buildCurrencyAttributeOptions,
  buildFixedCurrencyOptions,
} from "../revenueEventHelpers";
import { RevenueEventPreview } from "./RevenueEventPreview";
import classes from "./ConfigureRevenueEventModal.module.css";

type ConfigureRevenueEventModalProps = {
  opened: boolean;
  onClose: () => void;
  onSave: (
    config: Omit<RevenueEventConfig, "id" | "configuredAt"> & { id?: string },
  ) => void;
  editingConfig: RevenueEventConfig | null;
  configuredEventNames: string[];
};

function isEventAlreadyConfigured(
  name: string,
  configuredEventNames: string[],
  editingConfig: RevenueEventConfig | null,
): boolean {
  if (!name) {
    return false;
  }
  if (editingConfig?.eventName === name) {
    return false;
  }
  return configuredEventNames.includes(name);
}

export function ConfigureRevenueEventModal({
  opened,
  onClose,
  onSave,
  editingConfig,
  configuredEventNames,
}: ConfigureRevenueEventModalProps) {
  const [eventName, setEventName] = useState("");
  const [valueAttribute, setValueAttribute] = useState("");
  const [manualAttribute, setManualAttribute] = useState(false);
  const [manualCurrency, setManualCurrency] = useState(true);
  const [currencyAttributeKey, setCurrencyAttributeKey] = useState("");
  const [currency, setCurrency] = useState<string>("");
  const [conversionWindowHours, setConversionWindowHours] = useState(
    DEFAULT_CONVERSION_WINDOW_HOURS,
  );
  const [previewDays, setPreviewDays] = useState(
    DEFAULT_REVENUE_EVENT_PREVIEW_DAYS,
  );
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const { data: funnelEventsData, isLoading: loadingEvents } =
    useGetFunnelEvents();

  const { data: definitionsData } = useEventDefinitions({
    search: eventName,
    limit: 50,
    offset: 0,
  });

  const isDuplicateSelection = isEventAlreadyConfigured(
    eventName.trim(),
    configuredEventNames,
    editingConfig,
  );

  const eventOptions = useMemo(() => {
    const names = new Set(funnelEventsData?.data?.events ?? []);
    if (eventName) {
      names.add(eventName);
    }
    return Array.from(names)
      .sort((a, b) => a.localeCompare(b))
      .map((name) => {
        const configured = isEventAlreadyConfigured(
          name,
          configuredEventNames,
          editingConfig,
        );
        return {
          value: name,
          label: configured ? `${name} · already configured` : name,
          disabled: configured,
        };
      });
  }, [
    funnelEventsData?.data?.events,
    eventName,
    configuredEventNames,
    editingConfig,
  ]);

  const matchedDefinition = useMemo(() => {
    if (!eventName) {
      return undefined;
    }
    return definitionsData?.data?.eventDefinitions?.find(
      (d) => d.eventName === eventName,
    );
  }, [definitionsData, eventName]);

  const attributeOptions = useMemo(() => {
    const attrs = matchedDefinition?.attributes ?? [];
    const numeric = attrs.filter((a) =>
      NUMERIC_ATTRIBUTE_TYPES.has(a.dataType?.toLowerCase() ?? ""),
    );
    const source = numeric.length > 0 ? numeric : attrs;
    return source.map((a) => ({
      value: a.attributeName,
      label: a.attributeName,
    }));
  }, [matchedDefinition]);

  const currencyAttributeOptions = useMemo(
    () => buildCurrencyAttributeOptions(matchedDefinition?.attributes),
    [matchedDefinition],
  );

  const fixedCurrencyOptions = useMemo(() => buildFixedCurrencyOptions(), []);

  const isCurrencyConfigured = manualCurrency
    ? !!currency
    : !!currencyAttributeKey.trim();

  const revenueMetricsReady =
    !!valueAttribute.trim() && isCurrencyConfigured;

  useEffect(() => {
    if (!opened) {
      return;
    }
    setErrorMessage(null);
    setPreviewDays(DEFAULT_REVENUE_EVENT_PREVIEW_DAYS);
    if (editingConfig) {
      setEventName(editingConfig.eventName);
      setValueAttribute(editingConfig.valueAttribute);
      setManualAttribute(false);
      if (editingConfig.currencyAttribute) {
        setManualCurrency(false);
        setCurrencyAttributeKey(editingConfig.currencyAttribute);
        setCurrency("");
      } else {
        setManualCurrency(true);
        setCurrency(editingConfig.currency);
        setCurrencyAttributeKey("");
      }
      setConversionWindowHours(editingConfig.conversionWindowHours);
    } else {
      setEventName("");
      setValueAttribute("");
      setManualAttribute(false);
      setManualCurrency(true);
      setCurrencyAttributeKey("");
      setCurrency("");
      setConversionWindowHours(DEFAULT_CONVERSION_WINDOW_HOURS);
    }
  }, [opened, editingConfig]);

  useEffect(() => {
    if (!eventName || manualAttribute || editingConfig) {
      return;
    }
    if (
      attributeOptions.length === 1 &&
      valueAttribute !== attributeOptions[0].value
    ) {
      setValueAttribute(attributeOptions[0].value);
    }
  }, [
    eventName,
    attributeOptions,
    manualAttribute,
    editingConfig,
    valueAttribute,
  ]);

  useEffect(() => {
    if (!eventName || manualCurrency || editingConfig) {
      return;
    }
    if (
      currencyAttributeOptions.length === 1 &&
      currencyAttributeKey !== currencyAttributeOptions[0].value
    ) {
      setCurrencyAttributeKey(currencyAttributeOptions[0].value);
    }
  }, [
    eventName,
    manualCurrency,
    editingConfig,
    currencyAttributeOptions,
    currencyAttributeKey,
  ]);

  const handleEventChange = (val: string | null) => {
    const next = val ?? "";
    setEventName(next);
    setValueAttribute("");
    setManualAttribute(false);
    setManualCurrency(true);
    setCurrencyAttributeKey("");
    setCurrency("");
    setErrorMessage(null);

    if (
      isEventAlreadyConfigured(next, configuredEventNames, editingConfig)
    ) {
      setErrorMessage(
        `"${next}" is already a revenue event. Edit the existing row or pick another event.`,
      );
    }
  };

  const handleSubmit = () => {
    setErrorMessage(null);
    const trimmedEvent = eventName.trim();
    const trimmedAttr = valueAttribute.trim();
    const trimmedCurrencyAttr = currencyAttributeKey.trim();

    if (!trimmedEvent) {
      setErrorMessage("Select a revenue event.");
      return;
    }
    if (isDuplicateSelection) {
      setErrorMessage(
        `"${trimmedEvent}" is already configured as a revenue event.`,
      );
      return;
    }
    if (!trimmedAttr) {
      setErrorMessage("Select or enter a value attribute.");
      return;
    }
    if (manualCurrency) {
      if (!currency) {
        setErrorMessage("Select a fixed currency code.");
        return;
      }
    } else if (!trimmedCurrencyAttr) {
      setErrorMessage("Select or enter a currency attribute.");
      return;
    }
    if (!conversionWindowHours || conversionWindowHours < 1) {
      setErrorMessage("Conversion window must be at least 1 hour.");
      return;
    }

    onSave({
      id: editingConfig?.id,
      eventName: trimmedEvent,
      valueAttribute: trimmedAttr,
      currency: manualCurrency ? currency : "",
      currencyAttribute: manualCurrency ? undefined : trimmedCurrencyAttr,
      conversionWindowHours,
    });
    onClose();
  };

  const formDisabled = isDuplicateSelection || !eventName.trim();

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={
        <div className={classes.modalTitleBlock}>
          <div className={classes.modalTitleRow}>
            <div className={classes.modalTitleIcon}>
              <IconCoin size={20} stroke={1.75} />
            </div>
            <div className={classes.modalTitleText}>
              <Text className={classes.modalTitle}>
                {editingConfig ? "Edit revenue event" : "Configure revenue event"}
              </Text>
              <Text className={classes.modalSubtitle}>
                Preview updates as you configure.
              </Text>
            </div>
          </div>
        </div>
      }
      size="auto"
      centered
      classNames={{ content: classes.modalContent }}
      styles={{
        header: {
          alignItems: "flex-start",
          paddingBottom: 14,
          marginBottom: 0,
          borderBottom: "1px solid rgba(14, 201, 194, 0.18)",
        },
        body: { paddingTop: 18, paddingBottom: 16 },
      }}
    >
      <div className={classes.modalBody}>
        <div className={classes.formColumn}>
          <Stack gap="sm">
            <Select
              label="Revenue event"
              placeholder={loadingEvents ? "Loading events…" : "Select event"}
              searchable
              data={eventOptions}
              value={eventName || null}
              onChange={handleEventChange}
              nothingFoundMessage="No events found in catalogue"
              error={
                isDuplicateSelection
                  ? "Already configured as revenue"
                  : undefined
              }
              size="sm"
              required
            />
            {isDuplicateSelection && (
              <Alert
                color="orange"
                variant="light"
                className={classes.duplicateAlert}
                icon={<IconAlertCircle size={16} />}
              >
                &quot;{eventName}&quot; is already configured. Pick another event
                or edit the existing row.
              </Alert>
            )}

            {manualAttribute || attributeOptions.length === 0 ? (
              <TextInput
                label="Value attribute"
                placeholder={eventName ? "order_amount" : "Select event first"}
                value={valueAttribute}
                onChange={(e) => setValueAttribute(e.currentTarget.value)}
                disabled={formDisabled}
                size="sm"
                required
              />
            ) : (
              <Select
                label="Value attribute"
                placeholder="Select attribute"
                searchable
                data={attributeOptions}
                value={valueAttribute || null}
                onChange={(val) => setValueAttribute(val ?? "")}
                disabled={formDisabled}
                size="sm"
                required
              />
            )}
            {attributeOptions.length > 0 && (
              <Text size="xs" mt={-4}>
                <Anchor
                  component="button"
                  type="button"
                  disabled={formDisabled}
                  className={classes.fieldToggleLink}
                  onClick={() => setManualAttribute((v) => !v)}
                >
                  {manualAttribute ? "Pick from catalogue" : "Enter manually"}
                </Anchor>
              </Text>
            )}

            {manualCurrency ? (
              <>
                <Select
                  label="Currency"
                  placeholder="Select currency"
                  data={fixedCurrencyOptions}
                  value={currency || null}
                  onChange={(val) => setCurrency(val ?? "")}
                  disabled={formDisabled}
                  size="sm"
                  required
                />
                <Text size="xs" mt={-4}>
                  <Anchor
                    component="button"
                    type="button"
                    disabled={formDisabled}
                    className={classes.fieldToggleLink}
                    onClick={() => {
                      setManualCurrency(false);
                      setCurrency("");
                    }}
                  >
                    Use currency attribute
                  </Anchor>
                </Text>
              </>
            ) : (
              <>
                {currencyAttributeOptions.length > 0 ? (
                  <Autocomplete
                    label="Currency attribute"
                    placeholder="e.g. currency"
                    data={currencyAttributeOptions}
                    value={currencyAttributeKey}
                    onChange={(val) => setCurrencyAttributeKey(val)}
                    disabled={formDisabled}
                    size="sm"
                    required
                    comboboxProps={{ withinPortal: true }}
                  />
                ) : (
                  <TextInput
                    label="Currency attribute"
                    placeholder={eventName ? "currency" : "Select event first"}
                    value={currencyAttributeKey}
                    onChange={(e) =>
                      setCurrencyAttributeKey(e.currentTarget.value)
                    }
                    disabled={formDisabled}
                    size="sm"
                    required
                  />
                )}
                <Text size="xs" mt={-4}>
                  <Anchor
                    component="button"
                    type="button"
                    disabled={formDisabled}
                    className={classes.fieldToggleLink}
                    onClick={() => {
                      setManualCurrency(true);
                      setCurrencyAttributeKey("");
                    }}
                  >
                    Use fixed currency
                  </Anchor>
                </Text>
              </>
            )}

            <NumberInput
              className={classes.conversionField}
              label="Conversion window (hours)"
              value={conversionWindowHours}
              onChange={(val) =>
                setConversionWindowHours(
                  typeof val === "number"
                    ? val
                    : DEFAULT_CONVERSION_WINDOW_HOURS,
                )
              }
              min={1}
              max={168}
              size="sm"
              required
            />
          </Stack>
        </div>

        <div className={classes.previewColumn}>
          <RevenueEventPreview
            eventName={isDuplicateSelection ? "" : eventName}
            valueAttribute={valueAttribute}
            manualCurrency={manualCurrency}
            fixedCurrency={currency}
            currencyAttribute={
              manualCurrency ? null : currencyAttributeKey.trim() || null
            }
            revenueMetricsReady={revenueMetricsReady}
            previewDays={previewDays}
            onPreviewDaysChange={setPreviewDays}
          />
        </div>
      </div>

      {errorMessage && !isDuplicateSelection && (
        <Alert
          icon={<IconAlertCircle size={16} />}
          color="red"
          variant="light"
          withCloseButton
          onClose={() => setErrorMessage(null)}
          mt="md"
        >
          {errorMessage}
        </Alert>
      )}

      <Group justify="flex-end" className={classes.footerSection}>
        <Button
          variant="outline"
          className={classes.cancelButton}
          onClick={onClose}
        >
          Cancel
        </Button>
        <Button
          className={classes.submitButton}
          onClick={handleSubmit}
          disabled={isDuplicateSelection}
        >
          {editingConfig ? "Save changes" : "Confirm"}
        </Button>
      </Group>
    </Modal>
  );
}
