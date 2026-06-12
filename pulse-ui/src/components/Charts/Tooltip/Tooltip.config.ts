import { createTooltipFormatter } from "./Tooltip";

export const CustomToolTip = {
  trigger: "axis",
  formatter: createTooltipFormatter(),
  backgroundColor: "rgba(255, 255, 255, 0.95)",
  borderColor: "#ddd",
  borderWidth: 1,
  borderRadius: 8,
  padding: [12, 16],
  textStyle: {
    color: "#333",
    fontSize: 12,
  },
};
