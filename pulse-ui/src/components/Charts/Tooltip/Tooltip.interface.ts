export interface TooltipOptions {
  valueFormatter?: (value: any, seriesName?: string, params?: any) => string;
  originalData?: any[]; // Original data source for custom value lookup
  customValueExtractor?: (
    dataIndex: number,
    seriesName: string,
    originalData: any[],
  ) => any;
  customHeaderFormatter?: (axisValue: any) => string;
}
