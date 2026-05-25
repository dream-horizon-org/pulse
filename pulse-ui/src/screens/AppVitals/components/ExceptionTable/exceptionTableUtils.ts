import dayjs from "dayjs";

/**
 * Formats app versions string into a range (first - last)
 * Example: "2.3.0, 2.3.5, 2.4.0" -> "2.3.0 - 2.4.0"
 */
export function formatAppVersionRange(appVersions: string): string {
  if (!appVersions || appVersions.trim() === "") {
    return "-";
  }

  const versions = appVersions
    .split(",")
    .map((v) => v.trim())
    .filter((v) => v.length > 0);

  if (versions.length === 0) {
    return "-";
  }

  if (versions.length === 1) {
    return versions[0];
  }

  const sortedVersions = versions.sort((a, b) => {
    const aParts = a.split(".").map(Number);
    const bParts = b.split(".").map(Number);

    for (let i = 0; i < Math.max(aParts.length, bParts.length); i++) {
      const aPart = aParts[i] || 0;
      const bPart = bParts[i] || 0;
      if (aPart !== bPart) {
        return aPart - bPart;
      }
    }
    return 0;
  });

  const first = sortedVersions[0];
  const last = sortedVersions[sortedVersions.length - 1];

  return first === last ? first : `${first} - ${last}`;
}

export function formatExceptionTimestamp(value: string | undefined): string {
  if (!value || value === "-" || !dayjs(value).isValid()) {
    return "-";
  }
  return dayjs(value).format("MMM DD, YYYY HH:mm:ss");
}
