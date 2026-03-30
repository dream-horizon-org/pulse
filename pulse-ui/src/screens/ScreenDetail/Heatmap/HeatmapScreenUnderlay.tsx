import { useState } from "react";
import {
  HEATMAP_CHECKOUT_UNDERLAY_URL,
  HEATMAP_UNDERLAY_LAST_RESORT,
} from "./heatmapViz.constants";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapScreenUnderlayProps {
  screenshotUrl: string | null | undefined;
}

export function HeatmapScreenUnderlay({
  screenshotUrl,
}: HeatmapScreenUnderlayProps) {
  const [apiFailed, setApiFailed] = useState(false);
  const [checkoutFailed, setCheckoutFailed] = useState(false);

  const showCheckout =
    !screenshotUrl || apiFailed || screenshotUrl.trim() === "";

  const checkoutSrc = checkoutFailed
    ? HEATMAP_UNDERLAY_LAST_RESORT
    : HEATMAP_CHECKOUT_UNDERLAY_URL;

  if (showCheckout) {
    return (
      <img
        className={`${classes.screenImg} ${classes.screenImgCheckout}`}
        src={checkoutSrc}
        alt=""
        draggable={false}
        onError={() => {
          if (!checkoutFailed) setCheckoutFailed(true);
        }}
      />
    );
  }

  return (
    <img
      key={screenshotUrl}
      className={classes.screenImg}
      src={screenshotUrl}
      alt=""
      draggable={false}
      onError={() => setApiFailed(true)}
    />
  );
}
