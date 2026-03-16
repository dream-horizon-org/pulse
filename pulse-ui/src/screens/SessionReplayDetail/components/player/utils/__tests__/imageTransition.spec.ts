import { calculateImageTransition } from "../imageTransition";
import type { SessionReplayImage } from "../../../../../../services/sessionReplay/sessionReplayImages";

describe("imageTransition", () => {
  const imageAt0: SessionReplayImage = {
    timestamp: 0,
    imageUrl: "https://example.com/0.png",
    blobKey: "0",
  };
  const imageAt100: SessionReplayImage = {
    timestamp: 100,
    imageUrl: "https://example.com/100.png",
    blobKey: "100",
  };
  it("returns imageToShow null and opacity 1 when currentImage is null", () => {
    const result = calculateImageTransition(null, imageAt0, new Set([0]), 50);
    expect(result.imageToShow).toBeNull();
    expect(result.transitionOpacity).toBe(1);
  });

  it("returns current image with opacity 1 when current is loaded and no previous", () => {
    const loaded = new Set([100]);
    const result = calculateImageTransition(imageAt100, null, loaded, 100);
    expect(result.imageToShow).toEqual(imageAt100);
    expect(result.transitionOpacity).toBe(1);
  });

  it("returns current image when current is loaded", () => {
    const loaded = new Set([100]);
    const result = calculateImageTransition(imageAt100, imageAt0, loaded, 100);
    expect(result.imageToShow).toEqual(imageAt100);
  });

  it("returns previous image when current not loaded but previous is loaded", () => {
    const loaded = new Set([0]);
    const result = calculateImageTransition(imageAt100, imageAt0, loaded, 50);
    expect(result.imageToShow).toEqual(imageAt0);
    expect(result.transitionOpacity).toBe(1);
  });

  it("returns opacity 1 when both previous and current are loaded (no crossfade)", () => {
    const loaded = new Set([0, 100]);
    const result = calculateImageTransition(imageAt100, imageAt0, loaded, 50);
    expect(result.imageToShow).toEqual(imageAt100);
    expect(result.transitionOpacity).toBe(1);
  });

  it("returns opacity 1 when time is past current image", () => {
    const loaded = new Set([0, 100]);
    const result = calculateImageTransition(imageAt100, imageAt0, loaded, 150);
    expect(result.transitionOpacity).toBe(1);
  });

  it("falls back to current image when neither current nor previous is loaded", () => {
    const loaded = new Set<number>();
    const result = calculateImageTransition(imageAt100, imageAt0, loaded, 100);
    expect(result.imageToShow).toEqual(imageAt100);
  });
});
