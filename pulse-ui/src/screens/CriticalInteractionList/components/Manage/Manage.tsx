import { Divider, Group, useMantineTheme } from "@mantine/core";
import { Row } from "../../../../hooks/useGetInteractions";
import { EditAction, DeleteAction, StartAndStopAction } from "../Actions";
import { MoreDetails } from "../MoreDetails";
import { useState } from "react";
import classes from "./Manage.module.css";

export function Manage({
  refetchJobList,
  refetchJobDetails,
  ...restInteractionDetails
}: Row) {

  const { id, name, description, uptimeLowerLimitInMs, uptimeMidLimitInMs, uptimeUpperLimitInMs, thresholdInMs, events, globalBlacklistedEvents, createdAt, createdBy, updatedAt, updatedBy, status } = restInteractionDetails;
  const [newJobId, setNewJobId] = useState<number | undefined>(id);

  const theme = useMantineTheme();
  
  return (
    <Group
      className={classes.manageGroupButton}
      justify={"end"}
    >
      {status && (
        <>
          <StartAndStopAction
            status={status}
            successNotificationColor="#0ba09a"
            errorNotificationColor={theme.colors.red[6]}
            iconColor="#0ba09a"
            jobId={newJobId}
            name={name}
            setNewJobId={setNewJobId}
            refetchJobList={refetchJobList}
            refetchJobDetails={refetchJobDetails}
            interactionDetails={{ id, name, description, uptimeLowerLimitInMs, uptimeMidLimitInMs, uptimeUpperLimitInMs, thresholdInMs, events, globalBlacklistedEvents, createdAt, createdBy, updatedAt, updatedBy, status }}
          />
          <Divider variant="solid" orientation="vertical" />
          <EditAction
            status={status}
            notficationColor="#0ba09a"
            errorNotificationColor={theme.colors.red[6]}
            iconColor="#0ba09a"
            jobId={newJobId}
            name={name}
          />
          <Divider variant="solid" orientation="vertical" />
          <DeleteAction
            status={status}
            name={name}
            notficationColor={theme.colors.blue[6]}
            successNotificationColor={theme.colors.teal[6]}
            errorNotificationColor={theme.colors.red[6]}
            iconColor={theme.colors.red[6]}
            jobId={newJobId}
            createdBy={createdBy}
            refetchJobList={refetchJobList}
            refetchJobDetails={refetchJobDetails}
          />
          <Divider variant="solid" orientation="vertical" />
        </>
      )}
      <MoreDetails
        {...restInteractionDetails}
      />
    </Group>
  );
}
