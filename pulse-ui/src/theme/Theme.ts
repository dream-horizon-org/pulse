import { MantineThemeOverride, createTheme } from "@mantine/core";

// Default theme colors: https://v1.mantine.dev/pages/theming/
// CSS Variables: https://mantine.dev/styles/css-variables-list/
// Theme Object: https://mantine.dev/theming/theme-object/

export const theme: MantineThemeOverride = createTheme({
  colors: {
    primary: [
      "#E3F2FD", // lightest blue
      "#BBDEFB",
      "#90CAF9",
      "#64B5F6",
      "#42A5F5", // primary blue
      "#2196F3",
      "#1E88E5",
      "#1976D2",
      "#1565C0",
      "#0D47A1", // darkest blue
    ],
    error: [
      "#ffe5e5",
      "#ffcccc",
      "#ffb3b3",
      "#ff9999",
      "#ff8080",
      "#ff6666",
      "#ff4d4d",
      "#ff3333",
      "#ff1a1a",
      "#e60000",
    ],
    warning: [
      "#fff8e1",
      "#ffecb3",
      "#ffe082",
      "#ffd54f",
      "#ffca28",
      "#ffc107",
      "#ffb300",
      "#ffa000",
      "#ff8f00",
      "#ff6f00",
    ],
    info: [
      "#e3f2fd",
      "#bbdefb",
      "#90caf9",
      "#64b5f6",
      "#42a5f5",
      "#2196f3",
      "#1e88e5",
      "#1976d2",
      "#1565c0",
      "#0d47a1",
    ],
  },
  primaryColor: "primary",
  fontFamily: "Rubik, sans-serif",
  headings: { fontFamily: "Rubik, sans-serif" },
  fontSizes: {
    xs: "12px",
    sm: "14px",
    md: "16px",
    lg: "18px",
    xl: "20px",
    xxl: "24px",
  },
  defaultRadius: "md",
});
