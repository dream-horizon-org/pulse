import classes from "./MoreDetails.module.css";
import { useDisclosure } from "@mantine/hooks";
import { Popover, Text, Button, Divider } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { Row } from "../../../../hooks/useGetInteractions";

export function MoreDetails({
  createdBy,
  createdAt,
  updatedBy,
  updatedAt,
  ...restInteractionDetails
}: Row) {
  const [opened, { close, open }] = useDisclosure(false);

  const handleOnClick = (event: React.MouseEvent) => {
    event.stopPropagation();
    return;
  };

  const formatter = (date: Date) => {
    return date.toLocaleString("en-US", {
      year: "numeric",
      month: "long",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  };

  return (
    <div onClick={handleOnClick}>
      <Popover
        position="bottom"
        withArrow
        shadow="md"
        opened={opened}
        clickOutsideEvents={["mouseup", "touchend"]}
        closeOnClickOutside
      >
        <Popover.Target>
          <Button
            variant="transparent"
            onMouseEnter={open}
            onMouseLeave={close}
            className={classes.infoButton}
          >
            <IconInfoCircle size={16} />
          </Button>
        </Popover.Target>
        <Popover.Dropdown>
          <Text className={classes.infoText} size="sm">
            <i>Created By: </i>
            {createdBy}
          </Text>
          <Divider />
          <Text className={classes.infoText} size="sm">
            <i>Created At: </i>
            {formatter(new Date(Number(createdAt)))}
          </Text>
          <Divider />
          <Text className={classes.infoText} size="sm">
            <i>Last Updated By: </i>
            {updatedBy}
          </Text>
          <Divider />
          <Text className={classes.infoText} size="sm">
            <i>Last Updated At: </i>
            {formatter(new Date(Number(updatedAt)))}
          </Text>
        </Popover.Dropdown>
      </Popover>
    </div>
  );
}
