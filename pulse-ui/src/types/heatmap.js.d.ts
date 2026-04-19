declare module "heatmap.js" {
  export interface HeatmapDataPoint {
    x: number;
    y: number;
    value: number;
  }

  export interface HeatmapSetData {
    max: number;
    data: HeatmapDataPoint[];
  }

  export interface HeatmapInstance {
    setData(data: HeatmapSetData): void;
    addData(point: HeatmapDataPoint): void;
    repaint(): void;
    getDataURL(): string;
  }

  export interface HeatmapCreateConfig {
    container: HTMLElement;
    radius?: number;
    maxOpacity?: number;
    minOpacity?: number;
    blur?: number;
    gradient?: Record<string, string>;
    width?: number;
    height?: number;
    /** Applied to the canvas element (heatmap.js runtime supports this). */
    backgroundColor?: string;
  }

  const h337: {
    create(config: HeatmapCreateConfig): HeatmapInstance;
  };
  export default h337;
}
