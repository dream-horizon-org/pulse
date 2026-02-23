import { formatTooltipValue } from "../utils";
import { TooltipOptions } from "./Tooltip.interface";
import styles from "./Tooltip.module.css";

export const createTooltipFormatter = (options?: TooltipOptions) => {
  return function (params: any) {
    const {
      valueFormatter,
      originalData,
      customValueExtractor,
      customHeaderFormatter,
    } = options || {};

    if (!params) {
      return "";
    }

    // Handle single item format (for pie charts with trigger: "item")
    if (!Array.isArray(params)) {
      const param = params;
      let valueToFormat =
        typeof param.value === "number" ? param.value : param.value;

      if (
        originalData &&
        customValueExtractor &&
        typeof param.dataIndex === "number"
      ) {
        valueToFormat = customValueExtractor(
          param.dataIndex,
          param.seriesName,
          originalData,
        );
      }

      const formattedValue = valueFormatter
        ? valueFormatter(valueToFormat, param.seriesName || param.name, param)
        : formatTooltipValue(valueToFormat);

      // For pie charts, include percentage if available
      const percentageText =
        param.percent !== undefined ? ` (${param.percent}%)` : "";

      const headerValue = customHeaderFormatter
        ? customHeaderFormatter(param.axisValue || param.name)
        : param.name || param.axisValue || "";

      return `
        <div class="${styles.header}">${headerValue}</div>
        <div class="${styles.itemRow}">
          <div class="${styles.leftSection}">
            <span class="${styles.indicator}" style="background-color: ${param.color};"></span>
            <span class="${styles.seriesName}">${param.seriesName || param.name || ""}&nbsp;</span>
          </div>
          <span class="${styles.value}">${formattedValue} ${percentageText}</span>
        </div>
      `;
    }

    // Handle array format (for bar/line charts with trigger: "axis")
    if (params.length === 0) {
      return "";
    }

    const headerValue = customHeaderFormatter
      ? customHeaderFormatter(params[0].axisValue || params[0].name)
      : params[0].axisValue || params[0].name || "";

    let tooltipText = `<div class="${styles.header}">${headerValue}</div>`;

    params.forEach((param: any) => {
      let valueToFormat = param.value;

      if (
        originalData &&
        customValueExtractor &&
        typeof param.dataIndex === "number"
      ) {
        valueToFormat = customValueExtractor(
          param.dataIndex,
          param.seriesName,
          originalData,
        );
      }

      const formattedValue = valueFormatter
        ? valueFormatter(valueToFormat, param.seriesName, param)
        : formatTooltipValue(valueToFormat);

      tooltipText += `
        <div class="${styles.itemRow}">
          <div class="${styles.leftSection}">
            <span class="${styles.indicator}" style="background-color: ${param.color};"></span>
            <span class="${styles.seriesName}">${param.seriesName}&nbsp;</span>
          </div>
          <span class="${styles.value}">${formattedValue}</span>
        </div>
      `;
    });

    return tooltipText;
  };
};
