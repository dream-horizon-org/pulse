import { useState } from "react";
import { IconTag, IconTrophy, IconLink } from "@tabler/icons-react";
import { AttributeList } from "../AttributeList";
import { TabButton } from "../TabButton";
import classes from "./AttributeTabs.module.css";

interface AttributeTabsProps {
  resourceAttributes: Record<string, any>;
  spanAttributes: Record<string, any>;
  events: Array<Record<string, any>>;
  links: Array<Record<string, any>>;
}

type TabType = "attributes" | "events" | "links";

export function AttributeTabs({
  resourceAttributes,
  spanAttributes,
  events,
  links,
}: AttributeTabsProps) {
  const [activeTab, setActiveTab] = useState<TabType>("attributes");

  const allAttributes = { ...resourceAttributes, ...spanAttributes };
  const attributesCount = Object.keys(allAttributes).length;
  const eventsCount = events.length;
  const linksCount = links.length;

  const renderTabContent = () => {
    switch (activeTab) {
      case "attributes":
        return <AttributeList attributes={allAttributes} />;
      case "events":
        return <AttributeList events={events} />;
      case "links":
        return <AttributeList links={links} />;
      default:
        return null;
    }
  };

  return (
    <div className={classes.tabsContainer}>
      <div className={classes.tabsHeader}>
        <div className={classes.tabs}>
          <TabButton
            icon={IconTag}
            label="Attributes"
            count={attributesCount}
            isActive={activeTab === "attributes"}
            onClick={() => setActiveTab("attributes")}
          />
          <TabButton
            icon={IconTrophy}
            label="Events"
            count={eventsCount}
            isActive={activeTab === "events"}
            onClick={() => setActiveTab("events")}
          />
          <TabButton
            icon={IconLink}
            label="Links"
            count={linksCount}
            isActive={activeTab === "links"}
            onClick={() => setActiveTab("links")}
          />
        </div>
      </div>
      <div className={classes.tabContent}>{renderTabContent()}</div>
    </div>
  );
}
