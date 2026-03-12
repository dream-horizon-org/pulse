import { useState, useEffect } from "react";
import {
  Textarea,
  Text,
  Group,
  Badge,
  CloseButton,
  Stack,
  Box,
} from "@mantine/core";
import { IconMail, IconAlertCircle } from "@tabler/icons-react";
import classes from "./InviteCollaboratorsInput.module.css";

interface InviteCollaboratorsInputProps {
  value: string[];
  onChange: (emails: string[]) => void;
  label?: string;
  placeholder?: string;
  description?: string;
  error?: string;
  disabled?: boolean;
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function InviteCollaboratorsInput({
  value,
  onChange,
  label = "Email Addresses",
  placeholder = "Enter email addresses separated by commas",
  description,
  error,
  disabled = false,
}: InviteCollaboratorsInputProps) {
  const [inputValue, setInputValue] = useState("");
  const [validEmails, setValidEmails] = useState<string[]>([]);
  const [invalidEmails, setInvalidEmails] = useState<string[]>([]);

  // Sync with parent value
  useEffect(() => {
    setValidEmails(value);
  }, [value]);

  const validateEmail = (email: string): boolean => {
    return EMAIL_REGEX.test(email);
  };

  const parseEmails = (
    text: string,
  ): { valid: string[]; invalid: string[] } => {
    const emails = text
      .split(",")
      .map((email) => email.trim())
      .filter((email) => email.length > 0);

    const uniqueEmails = Array.from(new Set(emails));
    const valid: string[] = [];
    const invalid: string[] = [];

    uniqueEmails.forEach((email) => {
      if (validateEmail(email)) {
        valid.push(email);
      } else {
        invalid.push(email);
      }
    });

    return { valid, invalid };
  };

  const handleInputChange = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
    const text = event.currentTarget.value;
    setInputValue(text);

    const { valid, invalid } = parseEmails(text);
    setValidEmails(valid);
    setInvalidEmails(invalid);
    onChange(valid);
  };

  const handleInputBlur = () => {
    // On blur, if there are valid emails, clear the input and show them as badges
    if (validEmails.length > 0 && inputValue.trim()) {
      setInputValue("");
    }
  };

  const removeEmail = (emailToRemove: string) => {
    const updatedEmails = validEmails.filter(
      (email) => email !== emailToRemove,
    );
    setValidEmails(updatedEmails);
    onChange(updatedEmails);
  };

  const hasError = error || invalidEmails.length > 0;

  return (
    <Stack gap="xs">
      <Textarea
        label={label}
        placeholder={placeholder}
        description={description}
        value={inputValue}
        onChange={handleInputChange}
        onBlur={handleInputBlur}
        disabled={disabled}
        error={error}
        minRows={2}
        autosize
        leftSection={<IconMail size={16} />}
      />

      {validEmails.length > 0 && (
        <Box>
          <Text size="xs" c="dimmed" mb="xs">
            {validEmails.length} email{validEmails.length !== 1 ? "s" : ""} will
            be invited:
          </Text>
          <Group gap="xs">
            {validEmails.map((email) => (
              <Badge
                key={email}
                variant="light"
                color="teal"
                size="lg"
                rightSection={
                  !disabled && (
                    <CloseButton
                      size="xs"
                      onClick={() => removeEmail(email)}
                      aria-label={`Remove ${email}`}
                    />
                  )
                }
                className={classes.emailBadge}
              >
                {email}
              </Badge>
            ))}
          </Group>
        </Box>
      )}

      {invalidEmails.length > 0 && (
        <Box>
          <Group gap="xs" align="center">
            <IconAlertCircle size={16} color="var(--mantine-color-red-6)" />
            <Text size="xs" c="red">
              Invalid email{invalidEmails.length !== 1 ? "s" : ""}:
            </Text>
          </Group>
          <Group gap="xs" mt="xs">
            {invalidEmails.map((email) => (
              <Badge key={email} variant="light" color="red" size="lg">
                {email}
              </Badge>
            ))}
          </Group>
        </Box>
      )}
    </Stack>
  );
}
