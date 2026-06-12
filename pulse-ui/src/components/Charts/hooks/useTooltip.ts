import { useMemo } from "react";
import { createTooltipFormatter, CustomToolTip } from "../Tooltip";

interface UseTooltipProps {
  tooltipValueFormatter?: (value: any) => string;
}

export const useTooltip = ({ tooltipValueFormatter }: UseTooltipProps = {}) => {
  const tooltip = useMemo(() => {
    return tooltipValueFormatter
      ? {
          ...CustomToolTip,
          formatter: createTooltipFormatter({
            valueFormatter: tooltipValueFormatter,
          }),
        }
      : CustomToolTip;
  }, [tooltipValueFormatter]);

  return tooltip;
};
