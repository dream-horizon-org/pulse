import React, { useState } from "react";
import {
  DeleteInteractionOnSettledResponse,
  useDeleteInteraction,
} from "../../../../hooks/useDeleteInteraction";
import { showNotification } from "../../../../helpers/showNotification";
import {
  IconCircleCheckFilled,
  IconSquareRoundedX,
  IconTrash,
} from "@tabler/icons-react";
import { ActionProps } from "./Actions.interface";
import {
  COOKIES_KEY,
  COMMON_CONSTANTS,
  TOOLTIP_LABLES,
  ROUTES,
} from "../../../../constants";
import { getCookies } from "../../../../helpers/cookies";
import { Loader, Tooltip } from "@mantine/core";
import { useNavigate } from "react-router-dom";
import { useProjectContext } from "../../../../contexts";
import { ConfirmationModal } from "../../../../components/ConfirmationModal";

export function DeleteAction({
  successNotificationColor,
  errorNotificationColor,
  iconColor,
  createdBy,
  name,
  isLoading,
  refetchJobDetails,
}: ActionProps) {
  const [showLoader, setShowLoader] = useState(false);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const navigate = useNavigate();
  const { projectId } = useProjectContext();

  const navigateToCriticalInteractionListingPage = () => {
    // Only navigate if projectId is available
    if (!projectId) {
      console.error('[DeleteAction] Cannot navigate: projectId is null');
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        'Project context is missing. Please reload the page.',
        <IconSquareRoundedX />,
        errorNotificationColor,
      );
      return;
    }
    
    setTimeout(() => {
      navigate(ROUTES.PROJECT_INTERACTIONS.basePath.replace(':projectId', projectId));
    }, 3000);
  };

  const deleteInteraction = useDeleteInteraction(
    (response: DeleteInteractionOnSettledResponse) => {
      setShowLoader(false);
      if (response?.status === 200) {
        showNotification(
          COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
          `Successfully deleted interaction with id ${name}`,
          <IconCircleCheckFilled />,
          successNotificationColor,
        );

        navigateToCriticalInteractionListingPage();
        return;
      }
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        response?.error?.message || COMMON_CONSTANTS.DEFAULT_ERROR_MESSAGE,
        <IconSquareRoundedX />,
        errorNotificationColor,
      );
      refetchJobDetails && refetchJobDetails();
    },
  );

  const handleDelete = () => {
    setShowLoader(true);
    setConfirmDeleteOpen(false);

    deleteInteraction.mutate({
      useCaseId: name ?? "",
      user: getCookies(COOKIES_KEY.USER_EMAIL) ?? "",
      createdBy: createdBy ?? "",
    });
  };

  const onClick = (event: React.MouseEvent) => {
    event.stopPropagation();
    setConfirmDeleteOpen(true);
  };

  if (showLoader || isLoading) {
    return <Loader size={20} type="bars" />;
  }

  return (
    <>
      <Tooltip withArrow label={TOOLTIP_LABLES.DELETE_INTERACTION}>
        <span
          onClick={onClick}
          style={{
            display: "flex",
            alignItems: "center",
            padding: "6px",
            background:
              "linear-gradient(135deg, rgba(239, 68, 68, 0.08), rgba(239, 68, 68, 0.12))",
            border: "1px solid rgba(239, 68, 68, 0.2)",
            borderRadius: "8px",
            transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
            boxShadow: "0 2px 6px rgba(239, 68, 68, 0.08)",
            cursor: "pointer",
          }}
          onMouseEnter={(e) => {
            (e.currentTarget as HTMLSpanElement).style.background =
              "linear-gradient(135deg, rgba(239, 68, 68, 0.12), rgba(239, 68, 68, 0.18))";
            (e.currentTarget as HTMLSpanElement).style.borderColor =
              "rgba(239, 68, 68, 0.3)";
            (e.currentTarget as HTMLSpanElement).style.transform =
              "translateY(-1px)";
            (e.currentTarget as HTMLSpanElement).style.boxShadow =
              "0 4px 12px rgba(239, 68, 68, 0.15)";
          }}
          onMouseLeave={(e) => {
            (e.currentTarget as HTMLSpanElement).style.background =
              "linear-gradient(135deg, rgba(239, 68, 68, 0.08), rgba(239, 68, 68, 0.12))";
            (e.currentTarget as HTMLSpanElement).style.borderColor =
              "rgba(239, 68, 68, 0.2)";
            (e.currentTarget as HTMLSpanElement).style.transform =
              "translateY(0)";
            (e.currentTarget as HTMLSpanElement).style.boxShadow =
              "0 2px 6px rgba(239, 68, 68, 0.08)";
          }}
        >
          <IconTrash color={iconColor} size={18} />
        </span>
      </Tooltip>

      <ConfirmationModal
        opened={confirmDeleteOpen}
        onClose={() => setConfirmDeleteOpen(false)}
        onConfirm={handleDelete}
        title="Delete Interaction?"
        message={`Are you sure you want to delete interaction "${name}"? This action cannot be undone.`}
        confirmLabel="Yes, Delete"
        cancelLabel="Cancel"
        confirmColor="red"
        loading={showLoader}
        severity="danger"
      />
    </>
  );
}
