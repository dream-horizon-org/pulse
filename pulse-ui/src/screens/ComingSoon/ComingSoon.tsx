import classes from "./ComingSoon.module.css";
import {
  IconSparkles,
  IconBrain,
  IconRoute,
  IconChartDots,
  IconVideo,
  IconEye,
  IconCheck,
  IconClock,
  IconPointerSearch,
  IconUniverse,
  IconFileAnalytics,
  IconArrowRight,
} from "@tabler/icons-react";
import { useNavigate } from "react-router-dom";
import { Button } from "@mantine/core";

interface Product {
  title: string;
  description: string;
  icon: React.ElementType;
  features: string[];
  sneakPeekRoute?: string;
}

export function ComingSoon() {
  const navigate = useNavigate();

  const upcomingProducts: Product[] = [
    {
      title: "Stream Events",
      description:
        "Discover and explore all streaming events in your application with powerful search and filtering capabilities to understand event flows.",
      icon: IconPointerSearch,
      features: [
        "Real-time event discovery",
        "Advanced filtering & search",
        "Event schema inspection",
        "Event frequency tracking",
      ],
      sneakPeekRoute: undefined,
    },
    {
      title: "Real-Time Querying",
      description:
        "Execute powerful real-time SQL queries on your event data with an intuitive interface for custom analytics and data exploration.",
      icon: IconUniverse,
      features: [
        "SQL-like query interface",
        "Real-time data access",
        "Custom aggregations",
        "Export query results",
      ],
      sneakPeekRoute: undefined,
    },
    {
      title: "Impact Analysis",
      description:
        "Analyze the impact of releases, changes, and deployments on user experience with comprehensive before/after comparison analytics.",
      icon: IconFileAnalytics,
      features: [
        "Release comparison analytics",
        "A/B testing insights",
        "Deployment impact tracking",
        "User segment analysis",
      ],
      sneakPeekRoute: undefined,
    },
    {
      title: "Anomaly Detection",
      description:
        "Automatically detect unusual patterns and anomalies in your application metrics, user behavior, and performance data using AI-powered algorithms.",
      icon: IconSparkles,
      features: [
        "Real-time anomaly alerts",
        "ML-based pattern recognition",
        "Customizable sensitivity thresholds",
        "Historical anomaly tracking",
      ],
    },
    {
      title: "AI Insights",
      description:
        "Get intelligent, actionable insights about your application performance and user experience powered by advanced AI models.",
      icon: IconBrain,
      features: [
        "Automated root cause analysis",
        "Predictive performance insights",
        "Smart recommendations",
        "Natural language queries",
      ],
    },
    {
      title: "Path Analysis",
      description:
        "Visualize and analyze user navigation paths through your application to understand common flows and identify drop-off points.",
      icon: IconRoute,
      features: [
        "Visual path flow diagrams",
        "Conversion funnel analysis",
        "Drop-off identification",
        "A/B path comparison",
      ],
    },
    {
      title: "Heatmaps",
      description:
        "See exactly where users interact with your application through visual heatmaps showing clicks, taps, scrolls, and attention patterns.",
      icon: IconChartDots,
      features: [
        "Click and tap heatmaps",
        "Scroll depth tracking",
        "Attention zone analysis",
        "Mobile gesture tracking",
      ],
    },
    {
      title: "Session Replay",
      description:
        "Watch recordings of actual user sessions to understand behavior, identify issues, and improve user experience.",
      icon: IconVideo,
      features: [
        "Full session recordings",
        "Skip inactivity feature",
        "Privacy controls & masking",
        "Console & network logs",
      ],
    },
    {
      title: "End-to-End Observability",
      description:
        "Complete visibility across your entire application stack, from frontend to backend, with distributed tracing and unified monitoring.",
      icon: IconEye,
      features: [
        "Distributed tracing",
        "Service dependency mapping",
        "Cross-stack correlation",
        "Unified metrics dashboard",
      ],
    },
  ];

  const handleSneakPeek = (route: string) => {
    navigate(route);
  };

  return (
    <div className={classes.comingSoonContainer}>
      <div className={classes.header}>
        <h1 className={classes.title}>Exciting Features Coming Soon</h1>
        <p className={classes.subtitle}>
          We're continuously innovating to bring you cutting-edge observability
          and analytics features. Here's what's on the horizon.
        </p>
      </div>

      <div className={classes.productsGrid}>
        {upcomingProducts.map((product) => {
          const IconComponent = product.icon;
          return (
            <div key={product.title} className={classes.productCard}>
              <div className={classes.iconWrapper}>
                <IconComponent size={32} color="#0ba09a" stroke={2} />
              </div>
              <h3 className={classes.productTitle}>{product.title}</h3>
              <p className={classes.productDescription}>
                {product.description}
              </p>
              <div className={classes.badgeContainer}>
                <div className={classes.comingSoonBadge}>
                  <IconClock size={14} />
                  Coming Soon
                </div>
                {product.sneakPeekRoute && (
                  <Button
                    variant="light"
                    color="teal"
                    size="xs"
                    rightSection={<IconArrowRight size={14} />}
                    onClick={() => handleSneakPeek(product.sneakPeekRoute!)}
                    className={classes.sneakPeekButton}
                  >
                    View Sneak Peek
                  </Button>
                )}
              </div>
              <ul className={classes.features}>
                {product.features.map((feature) => (
                  <li key={feature} className={classes.featureItem}>
                    <IconCheck
                      size={16}
                      color="#0ba09a"
                      className={classes.featureIcon}
                    />
                    <span>{feature}</span>
                  </li>
                ))}
              </ul>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default ComingSoon;
