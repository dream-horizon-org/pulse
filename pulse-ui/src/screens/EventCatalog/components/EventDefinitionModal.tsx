import { useEffect, useRef, useState } from "react";
import {
  Modal,
  TextInput,
  Textarea,
  Button,
  Group,
  Box,
  ActionIcon,
  Select,
  Switch,
  Stack,
  Text,
  SimpleGrid,
  ScrollArea,
  Alert,
} from "@mantine/core";
import { IconPlus, IconTrash, IconAlertCircle } from "@tabler/icons-react";
import { useCreateEventDefinition } from "../hooks/useCreateEventDefinition";
import { useUpdateEventDefinition } from "../hooks/useUpdateEventDefinition";
import { EventDefinition, EventAttribute } from "../EventCatalog.types";
import classes from "./EventDefinitionModal.module.css";

type EventDefinitionModalProps = {
  opened: boolean;
  onClose: () => void;
  eventDefinition: EventDefinition | null;
};

const DATA_TYPE_OPTIONS = [
  { value: "string", label: "String" },
  { value: "int", label: "Integer" },
  { value: "double", label: "Double" },
  { value: "boolean", label: "Boolean" },
];

const emptyAttribute = (): EventAttribute => ({
  attributeName: "",
  description: "",
  dataType: "string",
  isRequired: false,
});

export function EventDefinitionModal({
  opened,
  onClose,
  eventDefinition,
}: EventDefinitionModalProps) {
  const isEdit = eventDefinition !== null;

  const [eventName, setEventName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("");
  const [attributes, setAttributes] = useState<EventAttribute[]>([]);
  const [attributeKeys, setAttributeKeys] = useState<string[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const keyCounter = useRef(0);

  const createMutation = useCreateEventDefinition();
  const updateMutation = useUpdateEventDefinition();

  useEffect(() => {
    setErrorMessage(null);
    keyCounter.current = 0;
    if (eventDefinition) {
      setEventName(eventDefinition.eventName);
      setDisplayName(eventDefinition.displayName || "");
      setDescription(eventDefinition.description || "");
      setCategory(eventDefinition.category || "");
      const attrs = eventDefinition.attributes?.length
        ? eventDefinition.attributes
        : [];
      setAttributes(attrs);
      setAttributeKeys(attrs.map(() => `attr-${keyCounter.current++}`));
    } else {
      setEventName("");
      setDisplayName("");
      setDescription("");
      setCategory("");
      setAttributes([]);
      setAttributeKeys([]);
    }
  }, [eventDefinition, opened]);

  const handleAddAttribute = () => {
    setAttributes([...attributes, emptyAttribute()]);
    setAttributeKeys([...attributeKeys, `attr-${keyCounter.current++}`]);
  };

  const handleRemoveAttribute = (index: number) => {
    setAttributes(attributes.filter((_, i) => i !== index));
    setAttributeKeys(attributeKeys.filter((_, i) => i !== index));
  };

  const handleAttributeChange = (
    index: number,
    field: keyof EventAttribute,
    value: string | boolean,
  ) => {
    const updated = [...attributes];
    updated[index] = { ...updated[index], [field]: value };
    setAttributes(updated);
  };

  const handleSubmit = () => {
    setErrorMessage(null);

    const payload = {
      eventName,
      displayName: displayName || eventName,
      description,
      category,
      attributes: attributes
        .filter((a) => a.attributeName.trim() !== "")
        .map((a) => ({
          attributeName: a.attributeName,
          description: a.description,
          dataType: a.dataType,
          isRequired: a.isRequired,
        })),
    };

    const onError = (result: { error?: { message?: string } | null }) => {
      const msg =
        result?.error?.message ||
        "Something went wrong. Please try again.";
      setErrorMessage(msg);
    };

    if (isEdit && eventDefinition) {
      const { eventName: _, ...updatePayload } = payload;
      updateMutation.mutate(
        { id: eventDefinition.id, ...updatePayload },
        {
          onSuccess: (result) => {
            if (result?.error) {
              onError(result);
            } else {
              onClose();
            }
          },
        },
      );
    } else {
      createMutation.mutate(payload, {
        onSuccess: (result) => {
          if (result?.error) {
            onError(result);
          } else {
            onClose();
          }
        },
      });
    }
  };

  const isSubmitting = createMutation.isPending || updateMutation.isPending;

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={isEdit ? "Edit Event Definition" : "New Event Definition"}
      size="lg"
      centered
      styles={{
        title: {
          fontWeight: 700,
          fontSize: 18,
        },
        header: {
          borderBottom: "2px solid #0ec9c2",
          paddingBottom: 12,
        },
      }}
    >
      <Stack gap="md" mt="md">
        <Box className={classes.formSection}>
          <Text className={classes.formSectionTitle}>Event Details</Text>
          <Stack gap="sm">
            <SimpleGrid cols={2}>
              <TextInput
                label="Event Name"
                placeholder="e.g. cart_viewed"
                value={eventName}
                onChange={(e) => setEventName(e.currentTarget.value)}
                disabled={isEdit}
                required
                size="sm"
              />
              <TextInput
                label="Display Name"
                placeholder="e.g. Cart Viewed"
                value={displayName}
                onChange={(e) => setDisplayName(e.currentTarget.value)}
                size="sm"
              />
            </SimpleGrid>

            <Textarea
              label="Description"
              placeholder="Describe what this event represents and when it fires..."
              value={description}
              onChange={(e) => setDescription(e.currentTarget.value)}
              minRows={2}
              autosize
              size="sm"
            />

            <TextInput
              label="Category"
              placeholder="e.g. commerce, navigation, auth"
              value={category}
              onChange={(e) => setCategory(e.currentTarget.value)}
              size="sm"
              style={{ maxWidth: "50%" }}
            />
          </Stack>
        </Box>

        <Box className={classes.formSection}>
          <Text className={classes.formSectionTitle}>
            Attributes ({attributes.length})
          </Text>

          <ScrollArea.Autosize mah={340}>
            <Stack gap="sm">
              {attributes.length === 0 && (
                <Box className={classes.emptyAttributes}>
                  No attributes defined yet. Click below to add one.
                </Box>
              )}

              {attributes.map((attr, index) => (
                <Box key={attributeKeys[index]} className={classes.attributeCard}>
                  <Group
                    align="center"
                    gap="sm"
                    wrap="nowrap"
                    className={classes.attributeCardHeader}
                  >
                    <span className={classes.attributeIndex}>{index + 1}</span>
                    <TextInput
                      placeholder="Attribute name"
                      value={attr.attributeName}
                      onChange={(e) =>
                        handleAttributeChange(
                          index,
                          "attributeName",
                          e.currentTarget.value,
                        )
                      }
                      size="xs"
                      style={{ flex: 1 }}
                      required
                    />
                    <Select
                      data={DATA_TYPE_OPTIONS}
                      value={attr.dataType}
                      onChange={(val) =>
                        handleAttributeChange(
                          index,
                          "dataType",
                          val || "string",
                        )
                      }
                      size="xs"
                      style={{ width: 110 }}
                    />
                    <Switch
                      label="Required"
                      checked={attr.isRequired}
                      onChange={(e) =>
                        handleAttributeChange(
                          index,
                          "isRequired",
                          e.currentTarget.checked,
                        )
                      }
                      size="xs"
                      styles={{
                        label: { fontSize: 11, paddingLeft: 6 },
                      }}
                    />
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      size="sm"
                      className={classes.removeAttrButton}
                      onClick={() => handleRemoveAttribute(index)}
                    >
                      <IconTrash size={14} />
                    </ActionIcon>
                  </Group>
                  <Box className={classes.attributeCardBody}>
                    <Textarea
                      placeholder="Describe what this attribute represents, expected values, constraints..."
                      value={attr.description}
                      onChange={(e) =>
                        handleAttributeChange(
                          index,
                          "description",
                          e.currentTarget.value,
                        )
                      }
                      size="xs"
                      minRows={2}
                      autosize
                      maxRows={4}
                    />
                  </Box>
                </Box>
              ))}

              <Button
                variant="subtle"
                leftSection={<IconPlus size={14} />}
                size="xs"
                className={classes.addAttributeButton}
                onClick={handleAddAttribute}
              >
                Add Attribute
              </Button>
            </Stack>
          </ScrollArea.Autosize>
        </Box>

        {errorMessage && (
          <Alert
            icon={<IconAlertCircle size={16} />}
            color="red"
            variant="light"
            withCloseButton
            onClose={() => setErrorMessage(null)}
            styles={{
              root: { borderRadius: 8 },
              message: { fontSize: 13 },
            }}
          >
            {errorMessage}
          </Alert>
        )}

        <Group justify="flex-end" className={classes.footerSection}>
          <Button
            variant="outline"
            size="sm"
            className={classes.cancelButton}
            onClick={onClose}
          >
            Cancel
          </Button>
          <Button
            size="sm"
            className={classes.submitButton}
            onClick={handleSubmit}
            loading={isSubmitting}
            disabled={!eventName.trim()}
          >
            {isEdit ? "Update Event" : "Create Event"}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
