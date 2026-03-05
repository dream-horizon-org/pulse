import React from "react";

const ReactECharts = (props: any) => (
  <div data-testid="echarts-react" data-option={JSON.stringify(props.option)} />
);

export default ReactECharts;
