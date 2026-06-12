export const filtersToQueryString = (filters: Record<string, string>) => {
  const params = new URLSearchParams();

  Object.entries(filters).forEach(([key, value]) => {
    if (value) params.append(key, value); // Only add non-empty filters
  });

  return params.toString(); // Returns a query string like "PLATFORM=Android&STATE=Mumbai"
};
