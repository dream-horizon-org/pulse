export const filterAttributes = (
  attributes: Record<string, any>,
  searchQuery: string,
): Record<string, any> => {
  if (!searchQuery) return attributes;
  const query = searchQuery.toLowerCase();
  return Object.fromEntries(
    Object.entries(attributes).filter(
      ([key, value]) =>
        key.toLowerCase().includes(query) ||
        String(value).toLowerCase().includes(query),
    ),
  );
};

export const filterArrayItems = (
  items: Array<Record<string, any>>,
  searchQuery: string,
): Array<Record<string, any>> => {
  if (!searchQuery) return items;
  const query = searchQuery.toLowerCase();
  return items.filter((item) =>
    Object.entries(item).some(
      ([key, value]) =>
        key.toLowerCase().includes(query) ||
        String(value).toLowerCase().includes(query),
    ),
  );
};
