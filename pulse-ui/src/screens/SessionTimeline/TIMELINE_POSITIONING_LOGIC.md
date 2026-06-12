# Timeline Positioning Logic Explanation

## Overview

This document explains how the timeline markers and span bars are positioned and aligned in the Session Timeline component.

## Core Positioning Formula

### `calculateEventPosition(timestamp, maxTime)`

```typescript
position = (timestamp / maxTime) * 100;
```

**What it does:**

- Converts an absolute timestamp (in milliseconds) into a percentage position
- `timestamp`: The time when an event occurred (e.g., 5000ms)
- `maxTime`: The maximum time in the timeline (e.g., 50000ms)
- Returns: A percentage (0-100) representing where on the timeline the event should appear

**Example:**

- Event at 10000ms in a timeline that goes to 50000ms
- Position = (10000 / 50000) \* 100 = 20%
- The event will appear at 20% from the left edge of the timeline

## How It's Applied

### 1. Timeline Markers (Time Axis)

- **Component**: `TimelineAxis`
- **Positioning**: Uses `calculateEventPosition(time, maxTime)` for each marker
- **Display**: Shows time labels (0ms, 5000ms, 10000ms, etc.) at regular intervals
- **Alignment**: Markers are centered using `transform: translateX(-50%)` so the marker line aligns with the time position

### 2. Span Bars (Timeline Events)

- **Component**: `SpanRow`
- **Positioning**: Uses `calculateEventPosition(span.timestamp, maxTime)` for the start position
- **Width**: Uses `calculateEventWidth(span.duration, maxTime)` to calculate bar width
- **Alignment**:
  - **Logs**: Left edge aligns with the marker position (no centering)
  - **Instant events (0ms)**: Centered at the marker position using `translateX(-50%)`
  - **Bars with duration**: Left edge aligns with the marker position

### 3. Vertical Guide Lines

- **Positioning**: Uses the same `calculateEventPosition(time, maxTime)` as markers
- **Purpose**: Visual guides to verify alignment between markers and events
- **Alignment**: Lines are centered using `translateX(-50%)` to match marker positions

## Container Structure & Padding

### Key Containers:

```
scrollableColumn (no padding)
  └── scrollableContent (padding-left: var(--mantine-spacing-md))
      ├── markerContainer (for header markers)
      ├── markerOverlay (for vertical guide lines)
      └── timelineContainer
          └── timelineVisualization (for span bars)
```

### Padding Strategy:

1. **Single padding source**: All padding is applied at the `scrollableContent` level
2. **Consistent reference**: Both markers and timeline bars use the same container (`scrollableContent`) as their positioning reference
3. **Percentage calculation**: The `left: ${position}%` is calculated relative to the `scrollableContent` width, which includes the padding in its box-sizing

## Alignment Calibration

### Why Alignment Matters:

- **Markers** show where specific times (0ms, 5000ms, etc.) are on the timeline
- **Span bars** show when events occurred and how long they lasted
- **Alignment** ensures that when a span starts at 5000ms, its left edge aligns with the 5000ms marker

### How We Ensure Alignment:

1. **Same calculation function**: Both markers and spans use `calculateEventPosition()`
2. **Same container reference**: Both are positioned relative to `scrollableContent`
3. **Consistent padding**: Padding is applied at the container level, not individual elements
4. **Box-sizing**: Using `box-sizing: border-box` ensures padding is included in width calculations

## Positioning Rules by Event Type

### Logs (`type === "log"`)

- **Position**: Left edge at `position%`
- **Transform**: None (left-aligned)
- **Duration**: Not displayed

### Instant Events (0ms duration, not logs)

- **Position**: Center at `position%`
- **Transform**: `translateX(-50%)` (centered)
- **Visual**: Small dot (4px width, 12px height)

### Spans with Duration

- **Position**: Left edge at `position%`
- **Width**: `(duration / maxTime) * 100%`
- **Transform**: None (left-aligned)
- **Duration**: Displayed below the bar

## Synchronized Scrolling

### How It Works:

1. **Two scrollable containers**: Header markers and timeline content have separate scroll containers
2. **Event listeners**: JavaScript listens to scroll events on both containers
3. **Synchronization**: When one scrolls, the other's `scrollLeft` is updated to match
4. **Loop prevention**: Uses a flag (`isScrolling`) to prevent infinite scroll loops

### Implementation:

```typescript
// When content scrolls, update header
contentElement.addEventListener("scroll", () => {
  headerElement.scrollLeft = contentElement.scrollLeft;
});

// When header scrolls, update content
headerElement.addEventListener("scroll", () => {
  contentElement.scrollLeft = headerElement.scrollLeft;
});
```

## Visual Verification

### Using the "Show markers" toggle:

1. **Enables**: Timeline axis header with time labels
2. **Enables**: Vertical guide lines through all rows
3. **Purpose**: Visual verification that:
   - Markers align with their time positions
   - Span bars align with their timestamp positions
   - Logs align with their timestamp positions
   - Everything stays aligned when scrolling

## Common Issues & Solutions

### Issue: Markers and bars don't align

**Cause**: Different padding or container references
**Solution**: Ensure both use the same container and padding strategy

### Issue: Alignment breaks when scrolling

**Cause**: Scroll synchronization not working
**Solution**: Check that refs point to the correct scrollable elements

### Issue: Percentage positioning seems off

**Cause**: Padding affecting percentage calculations
**Solution**: Use `box-sizing: border-box` and apply padding at container level

## Summary

The positioning logic is based on a simple percentage calculation:

- **Formula**: `(timestamp / maxTime) * 100`
- **Application**: Applied consistently to markers, spans, and guide lines
- **Alignment**: Ensured by using the same calculation and container reference
- **Verification**: Visual guides (markers and vertical lines) help verify alignment
