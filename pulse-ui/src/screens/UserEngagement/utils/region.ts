import { iso31661, iso31662 } from "iso-3166";

const subdivisionMap = new Map(iso31662.map(s => [s.code, s.name]));
const countryMap = new Map(iso31661.map(c => [c.alpha2, c.alpha3]));


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

  const regionName = subdivisionMap.get(code) ?? region;
  const countryName = countryMap.get(country);

  return countryName? `${regionName}, ${countryName}` : regionName;
}