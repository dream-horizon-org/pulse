import { VitalRating } from "../../WebVitals.constants";

export const ratingToColor = (rating: VitalRating): string => {
  switch (rating) {
    case "good":
      return "#12b886";
    case "needsImprovement":
      return "#fcc419";
    case "poor":
      return "#fa5252";
    default:
      return "#868e96";
  }
};

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
