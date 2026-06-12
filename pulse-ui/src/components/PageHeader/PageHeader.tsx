import { Box, ActionIcon, Tooltip } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import { PageHeaderProps } from "./PageHeader.interface";
import classes from "./PageHeader.module.css";

export function PageHeader({
  title,
  subtitle,
  count,
  countLabel,
  onBack,
  actions,
}: PageHeaderProps) {
  return (
    <Box className={classes.pageHeader}>
      <div className={classes.headerContent}>
        <div className={classes.leftSection}>
          {onBack && (
            <Tooltip label="Go back" position="right">
              <ActionIcon
                variant="light"
                color="teal"
                size="lg"
                onClick={onBack}
                className={classes.backButton}
              >
                <IconArrowLeft size={20} />
              </ActionIcon>
            </Tooltip>
          )}
          <div className={classes.titleSection}>
            <div className={classes.titleWrapper}>
              <div className={classes.titleRow}>
                <h1 className={classes.pageTitle}>{title}</h1>
                {count !== undefined && (
                  <span className={classes.countBadge}>
                    {count} {countLabel || (count === 1 ? "Item" : "Items")}
                  </span>
                )}
              </div>
              {subtitle && <p className={classes.pageSubtitle}>{subtitle}</p>}
            </div>
          </div>
        </div>
        {actions && <div className={classes.actionsSection}>{actions}</div>}
      </div>
    </Box>
  );
}
