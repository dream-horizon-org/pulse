declare module "iso-3166-2" {
  export interface SubdivisionInfo {
    type: string;
    name: string;
    countryName: string;
    countryCode: string;
    code: string;
    regionCode?: string;
  }
  interface Iso3166 {
    subdivision(countryOrFullCode: string, regionCode?: string): SubdivisionInfo | null;
  }
  const iso3166: Iso3166;
  export default iso3166;
}