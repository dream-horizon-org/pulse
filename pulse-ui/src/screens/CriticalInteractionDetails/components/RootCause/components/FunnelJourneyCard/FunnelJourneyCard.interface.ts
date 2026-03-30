export interface FunnelJourneyCardProps {
  /** Funnel or Journey identifier */
  id: string;
  /** Name of the funnel or journey */
  name: string;
  /** Type: "FUNNEL" or "JOURNEY" */
  type: "FUNNEL" | "JOURNEY";
  /** Status: "ACTIVE", "CREATING", "UPDATING", "STOPPED" */
  status: "ACTIVE" | "CREATING" | "UPDATING" | "STOPPED";
  /** Created by (e.g. "john@example.com") */
  createdBy: string;
  /** Relative creation time (e.g. "2 days ago") */
  createdAt: string;
  /** Tags associated with the funnel/journey */
  tags: string[];
  /** Brief description or summary */
  description?: string;
  /** Optional URL for viewing the funnel/journey; when set, card is clickable */
  detailUrl?: string;
  /** Optional callback when card is clicked; used when detailUrl is not provided */
  onCardClick?: () => void;
}