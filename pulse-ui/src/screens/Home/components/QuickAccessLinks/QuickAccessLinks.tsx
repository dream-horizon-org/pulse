import { Text } from "@mantine/core";
import { QuickAccessLinksProps } from "./QuickAccessLinks.interface";
import classes from "./QuickAccessLinks.module.css";

export function QuickAccessLinks({
  links,
  onLinkClick,
}: QuickAccessLinksProps) {
  return (
    <div className={classes.quickLinksGrid}>
      {links.map((link, index) => {
        const Icon = link.icon;
        return (
          <div
            key={index}
            className={classes.quickLinkCard}
            onClick={() => onLinkClick(link.route)}
          >
            <div className={classes.quickLinkIcon}>
              <Icon size={22} stroke={1.8} />
            </div>
            <div className={classes.quickLinkContent}>
              <Text className={classes.quickLinkTitle} size="sm" fw={700}>
                {link.title}
              </Text>
              <Text className={classes.quickLinkDescription} size="xs" fw={500}>
                {link.description}
              </Text>
            </div>
          </div>
        );
      })}
    </div>
  );
}
