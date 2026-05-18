import { PERCENTILE_VALUE } from "../constants/Constants.interface";
import { COLUMN_NAME } from "../constants/PulseOtelSemcov";
export const getPercentileExpression = (
  percentile: PERCENTILE_VALUE,
  column: COLUMN_NAME,
  condition?: string,
): string => {
  if (condition) {
    return `quantileTDigestIf(${percentile})(${column}, ${condition})`;
  }
  return `quantileTDigest(${percentile})(${column})`;
};
