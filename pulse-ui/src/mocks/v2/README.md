# Mock Layer V2

Improved mock data generation system for the data query API with better organization and Indian-specific data.

## Structure

```
mocks/v2/
├── dataQueryMockGenerator.ts  # Core mock generator class
├── mockDataConfig.ts          # Configuration for regions, devices, networks
├── index.ts                   # Exports
└── README.md                  # This file
```

## Key Improvements Over V1

### 1. Separation of Concerns

- **Generator Logic**: `dataQueryMockGenerator.ts` - handles query parsing and data generation
- **Configuration**: `mockDataConfig.ts` - centralized data for regions, devices, ranges
- **Exports**: `index.ts` - clean module exports

### 2. Indian Context

- **8 Major Indian States**: Maharashtra, Karnataka, Delhi, Tamil Nadu, etc.
- **Popular Indian Devices**: Redmi Note 11, OnePlus Nord 2, Realme 9 Pro, etc.
- **Indian Networks**: Jio, Airtel, Vi (Vodafone Idea), BSNL, etc.

### 3. Enhanced Data Generation

- Better value ranges for realistic metrics
- Improved random variance (±30%)
- Seed-based consistency for reproducibility
- Support for custom functions (e.g., `uniqCombined(UserId)`)

## Usage

### In MockResponseGenerator

```typescript
import { generateDataQueryMockResponseV2 } from "./v2";

// In handleDataQueryEndpoint
const data = generateDataQueryMockResponseV2(requestBody);
```

### Direct Usage

```typescript
import { DataQueryMockGeneratorV2 } from "./v2";

const generator = DataQueryMockGeneratorV2.getInstance();
const response = generator.generateResponse(requestBody);
```

## Configuration

Edit `mockDataConfig.ts` to customize:

```typescript
export const mockDataConfig: MockDataConfigV2 = {
  regions: ["Maharashtra", "Karnataka", ...],
  devices: ["Redmi Note 11", "OnePlus Nord 2", ...],
  networkProviders: ["Jio", "Airtel", ...],
  ranges: {
    apdex: { min: 0.80, max: 0.95 },
    crash: { min: 2, max: 12 },
    // ... more ranges
  }
};
```

## Query Types Supported

### 1. Time-Series (with TIME_BUCKET)

```typescript
{
  select: [
    { function: "TIME_BUCKET", param: { bucket: "1m" }, alias: "t1" },
    { function: "APDEX", alias: "apdex" }
  ],
  groupBy: ["t1"]
}
```

**Returns**: Multiple rows with timestamps

**Supported bucket sizes**:

- `1m`, `5m` = minutes
- `1h` = hours
- `1d` = days
- `1w` = weeks
- `1M` = months

### 2. Grouped (by field)

```typescript
{
  select: [
    { function: "COL", param: { field: "DeviceModel" }, alias: "device" },
    { function: "CRASH", alias: "crashes" }
  ],
  groupBy: ["device"]
}
```

**Returns**: Multiple rows by device

### 3. Aggregate (no grouping)

```typescript
{
  select: [
    { function: "APDEX", alias: "apdex" },
    { function: "CRASH", alias: "crashes" },
  ];
}
```

**Returns**: Single summary row

## Supported Functions

| Function                    | Description                   | Range           |
| --------------------------- | ----------------------------- | --------------- |
| `APDEX`                     | Application Performance Index | 0.80 - 0.95     |
| `CRASH`                     | Crash count                   | 2 - 12          |
| `ANR`                       | Application Not Responding    | 3 - 15          |
| `FROZEN_FRAME`              | UI freezes                    | 5 - 25          |
| `DURATION_P50`              | 50th percentile latency (ms)  | 200 - 800       |
| `DURATION_P95`              | 95th percentile latency (ms)  | 800 - 2000      |
| `DURATION_P99`              | 99th percentile latency (ms)  | 1500 - 4000     |
| `INTERACTION_SUCCESS_COUNT` | Successful requests           | 80 - 150        |
| `INTERACTION_ERROR_COUNT`   | Failed requests               | 10 - 50         |
| `USER_CATEGORY_EXCELLENT`   | Excellent users               | 20 - 50         |
| `USER_CATEGORY_GOOD`        | Good users                    | 50 - 100        |
| `USER_CATEGORY_AVERAGE`     | Average users                 | 0 - 20          |
| `USER_CATEGORY_POOR`        | Poor users                    | 10 - 40         |
| `NET_0`                     | Connection timeout            | 0 - 2           |
| `NET_2XX`                   | Success responses             | 80 - 140        |
| `NET_4XX`                   | Client errors                 | 2 - 15          |
| `NET_5XX`                   | Server errors                 | 0 - 8           |
| `CUSTOM`                    | Custom expressions            | 100000 - 150000 |

## Field Mappings

| Field                                         | Values                                                                                                                                            |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Platform` / `platform`                       | Android, iOS                                                                                                                                      |
| `Region` / `region` / `GeoState` / `geostate` | Maharashtra, Karnataka, Delhi, Tamil Nadu, UP, West Bengal, Gujarat, Rajasthan                                                                    |
| `DeviceModel` / `devicemodel`                 | Samsung Galaxy S21, iPhone 13 Pro, Redmi Note 11, OnePlus Nord 2, Realme 9 Pro, Vivo V23, Oppo Reno 7, iPhone 14, Samsung Galaxy A53, Poco X4 Pro |
| `OsVersion` / `osversion`                     | Android 13, Android 12, Android 11, iOS 16, iOS 15, iOS 14                                                                                        |
| `NetworkProvider` / `networkprovider`         | Jio, Airtel, Vi (Vodafone Idea), BSNL, Aircel, Other                                                                                              |
| `AppVersion` / `appversion`                   | 1.0.0, 1.1.0, 1.2.0, 2.0.0, 2.1.0                                                                                                                 |
| `ConnectionType` / `connectiontype`           | WiFi, 4G, 5G, 3G                                                                                                                                  |

**Note**: Field matching is case-insensitive

## Filter Support

The generator respects filters in the request:

```typescript
{
  filters: [
    { field: "SpanName", operator: "IN", value: ["Login", "Checkout"] }
  ],
  groupBy: ["interaction_name"]
}
```

When `SpanName` filter is provided, it will generate data for only those interactions instead of using default group values.

## Console Logging

The V2 generator includes detailed console logging:

```
[DataQueryMockV2] Generating response for: {hasTimeBucket: true, ...}
[DataQueryMockV2] Time-series query - filters: [...]
[DataQueryMockV2] Generated 6 time points from 2025-07-07...
[DataQueryMockV2] Sample row: ["2025-07-07T05:04Z", "0.8523...", ...]
[DataQueryMockV2] Generated time-series response: 6 rows
```

## Migration from V1

To switch from V1 to V2:

1. Update import in `MockResponseGenerator.ts`:

```typescript
// Old
import { generateDataQueryMockResponse } from "./dataQuery";

// New
import { generateDataQueryMockResponseV2 } from "./v2";
```

2. Update function call:

```typescript
// Old
const data = generateDataQueryMockResponse(requestBody);

// New
const data = generateDataQueryMockResponseV2(requestBody);
```

## Extending

### Add New Region

```typescript
// mockDataConfig.ts
regions: [...existing, "Kerala", "Punjab"];
```

### Add New Device

```typescript
// mockDataConfig.ts
devices: [...existing, "Nothing Phone 2", "Motorola Edge 40"];
```

### Add New Function Type

```typescript
// dataQueryMockGenerator.ts - generateValueForFunction()
case "NEW_METRIC":
  return this.randomCount(min, max, interactionName, groupValue).toString();
```

### Adjust Value Ranges

```typescript
// mockDataConfig.ts
ranges: {
  crash: { min: 5, max: 20 },  // Increase crash range
  apdex: { min: 0.70, max: 0.85 }  // Lower Apdex range
}
```

## Best Practices

1. **Use Configuration**: Don't hardcode data in the generator, use `mockDataConfig`
2. **Seed-based Random**: Use the hash function for consistent but varied data
3. **Case-insensitive**: Always normalize field names for matching
4. **Filter First**: Check filters before using default group values
5. **Log Everything**: Keep console logs for debugging

## Troubleshooting

### Issue: No data in charts

**Check**: Console logs show correct number of rows?
**Solution**: Verify field names match exactly (case-insensitive)

### Issue: Generic "Group1", "Group2" values

**Check**: Is the field name in `groupValueMap`?
**Solution**: Add field mapping to `mockDataConfig` and update `getGroupValues()`

### Issue: Values too high/low

**Check**: Current ranges in `mockDataConfig.ranges`
**Solution**: Adjust min/max values in configuration

### Issue: Data changes on every request

**Expected**: Seed-based randomness includes timestamp for variance
**If not desired**: Remove `Date.now()` from hash seed

## Future Enhancements

- [ ] Correlation between metrics (high errors → low Apdex)
- [ ] Anomaly simulation (spikes, drops)
- [ ] Configurable variance percentage
- [ ] Historical data patterns
- [ ] Regional performance differences
- [ ] Device-specific error patterns
- [ ] Time-of-day variations
