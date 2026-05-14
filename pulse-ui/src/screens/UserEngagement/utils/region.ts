import iso31662 from "iso-3166-2";

export function getRegionName(regionCode: string, countryCode: string): string {
  const region = regionCode.trim();
  if (!region) {
    return "Unknown";
  }

  const country = countryCode.trim().toUpperCase();
  if (!country) {
    return region;
  }

  const code = `${country}-${region.toUpperCase()}`;
  return iso31662.subdivision(code)?.name ?? region;
}