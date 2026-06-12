export interface ResourceInfoItem {
  key: string;
  label: string;
  value: string | null;
}

export const RESOURCE_ATTRIBUTE_MAPPINGS: Array<{
  key: string;
  label: string;
}> = [
  { key: "device.model", label: "Device" },
  { key: "os.version", label: "OS Version" },
  { key: "service.name", label: "Service" },
  { key: "service.version", label: "Version" },
  { key: "device.memory.total", label: "Memory Total" },
  { key: "device.manufacturer", label: "Manufacturer" },
  { key: "device.screen.resolution", label: "Screen Resolution" },
  { key: "geo.state", label: "State" },
];

export const buildResourceInfoItems = (
  resourceAttrs: Record<string, any>,
): ResourceInfoItem[] => {
  const getValue = (key: string): string | null => {
    const value = resourceAttrs[key];
    return value ? String(value) : null;
  };

  return RESOURCE_ATTRIBUTE_MAPPINGS.map((mapping) => ({
    key: mapping.key,
    label: mapping.label,
    value: getValue(mapping.key),
  })).filter((item) => item.value !== null);
};
