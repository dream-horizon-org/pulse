import { useState } from "react";
import {
  Modal,
  TextInput,
  Textarea,
  Select,
  Button,
  Stack,
} from "@mantine/core";
import { IconCheck, IconSquareRoundedX } from "@tabler/icons-react";
import { getCookies } from "../../helpers/cookies";
import { makeRequest } from "../../helpers/makeRequest";
import { showNotification } from "../../helpers/showNotification";
import { API_BASE_URL, COOKIES_KEY, COMMON_CONSTANTS } from "../../constants";

interface ContactUsModalProps {
  opened: boolean;
  onClose: () => void;
}

interface IncidentResponse {
  id: number;
  status: string;
  createdAt: string;
}

export function ContactUsModal({ opened, onClose }: ContactUsModalProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [loading, setLoading] = useState(false);

  const resetForm = () => {
    setTitle("");
    setDescription("");
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  const handleSubmit = async () => {
    console.log("orgIdentifier", getCookies(COOKIES_KEY.TENANT_ID));
    if (!title.trim() || !description.trim()) return;

    setLoading(true);
    try {
      const response = await makeRequest<IncidentResponse>({
        url: `${API_BASE_URL}/v1/incidents`,
        init: {
          method: "POST",
          body: JSON.stringify({
            title: title.trim(),
            description: description.trim(),
            reporterName: getCookies(COOKIES_KEY.USER_NAME),
            reporterEmail: getCookies(COOKIES_KEY.USER_EMAIL),
            orgIdentifier: getCookies(COOKIES_KEY.TENANT_ID) || "default",
          }),
        },
      });

      if (response.data) {
        showNotification(
          COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
          "Your query has been submitted.",
          <IconCheck />,
          "teal",
        );
        handleClose();
      } else {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          response.error?.message || "Failed to submit query.",
          <IconSquareRoundedX />,
          "red",
        );
      }
    } finally {
      setLoading(false);
    }
  };

  const isFormValid = title.trim() && description.trim();

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title="Contact Us"
      centered
      size="md"
    >
      <Stack gap="md">
        <TextInput
          label="Title"
          placeholder="Brief summary of your query"
          value={title}
          onChange={(e) => setTitle(e.currentTarget.value)}
          required
        />
        <Textarea
          label="Description"
          placeholder="Describe your query in detail"
          value={description}
          onChange={(e) => setDescription(e.currentTarget.value)}
          minRows={4}
          required
        />
        <Button
          onClick={handleSubmit}
          loading={loading}
          disabled={!isFormValid}
          color="teal"
          fullWidth
        >
          Submit
        </Button>
      </Stack>
    </Modal>
  );
}