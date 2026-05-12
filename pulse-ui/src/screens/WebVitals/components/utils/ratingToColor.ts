import { VitalRating } from "../../WebVitals.constants";

export const ratingToColorName = (
  rating: VitalRating,
): "green" | "yellow" | "red" => {
  switch (rating) {
    case "good":
      return "green";
    case "needsImprovement":
      return "yellow";
    case "poor":
      return "red";
  }
};
