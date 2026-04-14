import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useMemo } from "react";
import { ActiveSessionsGraph } from "../Home/components/ActiveSessionsGraph";
import { UserEngagementGraph } from "../Home/components/UserEngagementGraph";
import { EngagementBreakdown } from "./components";
import classes from "./UserEngagement.module.css";
import { UserEngagementProps } from "./UserEngagement.interface";
import {
  CustomAttributeDataMap,
  CustomAttributeOption,
} from "./components/EngagementBreakdown/EngagementBreakdown.interface";

dayjs.extend(utc);

export function UserEngagement(_props: UserEngagementProps) {
  // TODO: get custom attribute options from API
  const customAttributeOptions = useMemo<CustomAttributeOption[]>(
    () => [
      { value: "vipTier", label: "VIP tier" },
      { value: "acquisitionChannel", label: "Acquisition channel" },
      { value: "subscriptionStatus", label: "Subscription status" },
    ],
    [],
  );

  const customAttributeData = useMemo<CustomAttributeDataMap>(
    () => ({
      vipTier: [
        {
          name: "Rookie",
          dau: 35200,
          wau: 138000,
          mau: 362100,
          sessions: 38500,
          wowChange: 3.5,
        },
        {
          name: "Pro",
          dau: 28780,
          wau: 114320,
          mau: 291430,
          sessions: 33410,
          wowChange: 1.2,
        },
        {
          name: "Champion",
          dau: 12980,
          wau: 50110,
          mau: 133500,
          sessions: 17640,
          wowChange: -0.7,
        },
        {
          name: "Legend",
          dau: 8640,
          wau: 32940,
          mau: 91300,
          sessions: 13220,
          wowChange: 2.8,
        },
      ],
      acquisitionChannel: [
        {
          name: "Organic",
          dau: 40110,
          wau: 157400,
          mau: 412300,
          sessions: 44720,
          wowChange: 4.2,
        },
        {
          name: "Paid",
          dau: 29870,
          wau: 118900,
          mau: 302500,
          sessions: 33140,
          wowChange: -1.0,
        },
        {
          name: "Referral",
          dau: 18840,
          wau: 74110,
          mau: 196700,
          sessions: 22830,
          wowChange: 5.4,
        },
        {
          name: "Partnerships",
          dau: 11230,
          wau: 42200,
          mau: 110850,
          sessions: 15620,
          wowChange: 0.5,
        },
      ],
      subscriptionStatus: [
        {
          name: "Free",
          dau: 76200,
          wau: 295500,
          mau: 756100,
          sessions: 80210,
          wowChange: 1.5,
        },
        {
          name: "Silver",
          dau: 21230,
          wau: 84200,
          mau: 216700,
          sessions: 24990,
          wowChange: 2.9,
        },
        {
          name: "Gold",
          dau: 12980,
          wau: 51430,
          mau: 132880,
          sessions: 17890,
          wowChange: -0.4,
        },
        {
          name: "Platinum",
          dau: 8260,
          wau: 31240,
          mau: 90420,
          sessions: 13980,
          wowChange: 3.3,
        },
      ],
    }),
    [],
  );

  return (
    <div className={classes.pageContainer}>
      <div className={classes.section}>
        <div className={classes.sectionHeader}>
          <h2 className={classes.sectionTitle}>Engagement overview</h2>
          <p className={classes.sectionCaption}>
            Daily, weekly and monthly active users plotted along with session
            volume to track how users are engaging with your app over time.
          </p>
        </div>
        <div className={classes.overviewGrid}>
          <UserEngagementGraph />
          <ActiveSessionsGraph />
        </div>
      </div>

      <div className={classes.section}>
        <EngagementBreakdown
          customAttributeOptions={customAttributeOptions}
          customAttributeData={customAttributeData}
        />
      </div>
    </div>
  );
}
