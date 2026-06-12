// @ts-nocheck

import { Card, CardContent, Typography, Box } from "@mui/material";
import { ResponsiveContainer } from "recharts";

/**
 * Reusable Card wrapper for charts
 */
const ChartCard = ({ title, icon, children, height = 300 }) => {
  return (
    <Card
      sx={{
        background: "linear-gradient(145deg, #ffffff 0%, #fafbfc 100%)",
        border: "1px solid rgba(14, 201, 194, 0.12)",
        borderRadius: "16px",
        boxShadow:
          "0 4px 12px rgba(14, 201, 194, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)",
        transition: "all 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
        position: "relative",
        overflow: "hidden",
        "&:hover": {
          boxShadow:
            "0 8px 20px rgba(14, 201, 194, 0.1), 0 4px 12px rgba(0, 0, 0, 0.04), inset 0 1px 0 rgba(255, 255, 255, 1)",
          transform: "translateY(-2px)",
          borderColor: "rgba(14, 201, 194, 0.2)",
        },
        "&::before": {
          content: '""',
          position: "absolute",
          top: 0,
          left: 0,
          right: 0,
          height: "2px",
          background:
            "linear-gradient(90deg, #0ec9c2 0%, #0ba09a 50%, transparent 100%)",
          opacity: 0,
          transition: "opacity 0.3s ease",
        },
        "&:hover::before": {
          opacity: 1,
        },
      }}
    >
      <CardContent>
        {icon ? (
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
            {icon}
            <Typography
              variant="h6"
              sx={{
                fontWeight: 700,
                fontSize: "14px",
                color: "#0ba09a",
                letterSpacing: "-0.2px",
              }}
            >
              {title}
            </Typography>
          </Box>
        ) : (
          <Typography
            variant="h6"
            sx={{
              fontWeight: 700,
              mb: 2,
              fontSize: "14px",
              color: "#0ba09a",
              letterSpacing: "-0.2px",
            }}
          >
            {title}
          </Typography>
        )}
        <Box sx={{ height }}>
          <ResponsiveContainer>{children}</ResponsiveContainer>
        </Box>
      </CardContent>
    </Card>
  );
};

export default ChartCard;
