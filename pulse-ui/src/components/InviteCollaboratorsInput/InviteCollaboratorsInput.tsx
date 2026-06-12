import { useState, useEffect, useRef } from "react";
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

function validateEmail(email: string): boolean {
  return EMAIL_REGEX.test(email);
}

/**
 * Email list is owned by the parent (`value`). The textarea is only a draft:
 * emails are merged in on comma or blur, so addresses added elsewhere (e.g. org
 * picker) are never wiped while typing.
 */
export function InviteCollaboratorsInput({
  value,
  onChange,
  label = "Emails",
  placeholder = "Enter email addresses separated by commas (e.g., john@example.com)",
  description,
  error,
  disabled = false,
}: InviteCollaboratorsInputProps) {
  const [draft, setDraft] = useState("");
  const [invalidCommitted, setInvalidCommitted] = useState<string[]>([]);
  const prevInviteKeyRef = useRef<string | null>(null);

  const inviteKey = value.slice().sort().join("|");

  useEffect(() => {
    const prev = prevInviteKeyRef.current;
    prevInviteKeyRef.current = inviteKey;
    if (prev !== null && inviteKey === "" && prev !== "") {
      setDraft("");
      setInvalidCommitted([]);
    }
  }, [inviteKey]);

  const commitChunks = (chunks: string[]) => {
    const validAdds: string[] = [];
    const invalidAdds: string[] = [];
    for (const c of chunks) {
      if (validateEmail(c)) {
        validAdds.push(c);
      } else {
        invalidAdds.push(c);
      }
    }
    if (validAdds.length > 0) {
      onChange(Array.from(new Set([...value, ...validAdds])));
    }
    return invalidAdds;
  };

  const handleInputChange = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
    const text = event.currentTarget.value;
    setInvalidCommitted([]);

    if (!text.includes(",")) {
      setDraft(text);
      return;
    }

    const parts = text.split(",");
    const remainder = (parts[parts.length - 1] ?? "").trim();
    const completeChunks = parts
      .slice(0, -1)
      .map((p) => p.trim())
      .filter((p) => p.length > 0);

    // If the last segment is a valid email, commit it too (e.g. paste "a@a.com, b@b.com, c@c.com")
    const chunksToCommit =
      remainder && validateEmail(remainder)
        ? [...completeChunks, remainder]
        : completeChunks;
    const newRemainder =
      remainder && validateEmail(remainder)
        ? ""
        : (parts[parts.length - 1] ?? "");

    if (chunksToCommit.length === 0) {
      setDraft(newRemainder);
      return;
    }

    const invalidAdds = commitChunks(chunksToCommit);
    setInvalidCommitted(invalidAdds);
    setDraft(newRemainder);
  };

  const commitDraft = () => {
    const t = draft.trim();
    if (!t) {
      setInvalidCommitted([]);
      return;
    }
    const parts = t
      .split(",")
      .map((p) => p.trim())
      .filter((p) => p.length > 0);
    const validAdds: string[] = [];
    const invalidAdds: string[] = [];
    for (const p of parts) {
      if (validateEmail(p)) validAdds.push(p);
      else invalidAdds.push(p);
    }
    if (invalidAdds.length > 0) {
      setInvalidCommitted(invalidAdds);
      return;
    }
    if (validAdds.length > 0) {
      onChange(Array.from(new Set([...value, ...validAdds])));
    }
    setInvalidCommitted([]);
    setDraft("");
  };

  const handleBlur = () => {
    commitDraft();
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key !== "Enter") return;
    event.preventDefault();
    commitDraft();
  };

  const removeEmail = (emailToRemove: string) => {
    onChange(value.filter((email) => email !== emailToRemove));
  };

  return (
    <Stack gap="xs">
      <Textarea
        label={label}
        placeholder={placeholder}
        description={description}
        value={draft}
        onChange={handleInputChange}
        onBlur={handleBlur}
        onKeyDown={handleKeyDown}
        disabled={disabled}
        error={error}
        minRows={1}
        autosize
        leftSection={<IconMail size={16} />}
      />

      {value.length > 0 && (
        <Box>
          <Text size="xs" c="dimmed" mb="xs">
            {value.length} email{value.length !== 1 ? "s" : ""} will be invited:
          </Text>
          <Group gap="xs">
            {value.map((email) => (
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

      {invalidCommitted.length > 0 && (
        <Box>
          <Group gap="xs" align="center">
            <IconAlertCircle size={16} color="var(--mantine-color-red-6)" />
            <Text size="xs" c="red">
              Invalid email{invalidCommitted.length !== 1 ? "s" : ""}:
            </Text>
          </Group>
          <Group gap="xs" mt="xs">
            {invalidCommitted.map((email) => (
              <Badge
                key={email}
                variant="light"
                color="red"
                size="lg"
                className={classes.invalidBadge}
              >
                {email}
              </Badge>
            ))}
          </Group>
        </Box>
      )}
    </Stack>
  );
}
