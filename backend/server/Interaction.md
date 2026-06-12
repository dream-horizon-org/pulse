# User Interaction Tracking

The user interaction tracking allows users to track the following metrics:

- **AppDex Score**
- **User experience categorisation**
- **Error rate**
- **Interaction Latencies**

The metrics mentioned above are powered through Pulse dashboard in real time and can be seen on the interaction detail page.

## Apdex Score

The Apdex score is shown on the first graph in Interaction detail page. The Apdex is calculated using the following formula:

```java
// Time taken to complete the interaction
// endEventTime is the time stamp of the last event tracked by Interaction
// startEventTime is the time stamp of the first event tracked by the Interaction
long eventDuration = endEventTime - startEventTime;

// Range of threshold
// highThreshold is the max value of the interaction time
// lowThreshold is the min value of the interaction time
long thresholdRange = highThreshold - lowThreshold;

// Normalising the threshold
long adjustedValue = highThreshold - eventDuration;

// Appdex Score
double appdexScore = 1 - (1 - (adjustedValue / thresholdRange));
```

The value of Apdex varies from 0 to 1 where:
- **0** signifies the interaction is at its worst and the time taken to complete all the interaction is higher than the highest threshold.
- **1** signifies the interaction is performing in its best possible range and the time taken to complete all the interaction is lower than the lower threshold.

The Apdex value only considers the successful interactions.

### Examples

**Example 1: Excellent Performance**
- Interaction: "Contest Join"
- Threshold range: 100ms (low) to 500ms (high)
- Event duration: 80ms
- Adjusted value: 500 - 80 = 420
- Apdex score: 1 - (1 - (420 / 400)) = 1.05 → capped at 1.0
- **Result**: Apdex = 1.0 (excellent performance, below lower threshold)

**Example 2: Average Performance**
- Interaction: "Search Results"
- Threshold range: 300ms (low) to 1500ms (high)
- Event duration: 900ms
- Adjusted value: 1500 - 900 = 600
- Apdex score: 1 - (1 - (600 / 1200)) = 0.5
- **Result**: Apdex = 0.5 (average performance, mid-range)

**Example 3: Poor Performance**
- Interaction: "Page Load"
- Threshold range: 500ms (low) to 2000ms (high)
- Event duration: 2500ms
- Adjusted value: 2000 - 2500 = -500
- Apdex score: 1 - (1 - (-500 / 1500)) = 0.33
- **Result**: Apdex = 0.33 (poor performance, exceeds high threshold)

## Error Rate

The error rate is shown in the first graph of the interaction detail page.

The error rate signifies the ratio in percentage of incomplete interaction over total of initiated interactions.

For example, if a user tries to do 100 contest joins, a 2% error rate signifies out of 100 interaction, user was only able to join a contest 98 times.

### Examples

**Example 1: Low Error Rate**
- Interaction: "Login"
- Total initiated: 10,000
- Successful completions: 9,950
- Incomplete interactions: 50
- Error rate: (50 / 10,000) × 100 = 0.5%
- **Result**: 0.5% error rate (excellent reliability)

**Example 2: Moderate Error Rate**
- Interaction: "Contest Join"
- Total initiated: 5,000
- Successful completions: 4,900
- Incomplete interactions: 100
- Error rate: (100 / 5,000) × 100 = 2%
- **Result**: 2% error rate (acceptable, minor issues)

**Example 3: High Error Rate**
- Interaction: "Payment Processing"
- Total initiated: 2,000
- Successful completions: 1,700
- Incomplete interactions: 300
- Error rate: (300 / 2,000) × 100 = 15%
- **Result**: 15% error rate (critical issue, needs investigation)

## User Experience Categorisation

The user experience is divided into four categories:

- **Excellent**
- **Good**
- **Average**
- **Poor**

The experience of the user is categorised based on the threshold set while creating any interaction.

### Examples

**Example 1: Excellent Experience**
- Interaction: "Add to Cart"
- Threshold: 100ms (low) to 500ms (high)
- Actual duration: 85ms
- Apdex score: 1.0
- Error rate: 0.1%
- **Categorisation**: Excellent
- **Reason**: Performance is below lower threshold, minimal errors, optimal user experience

**Example 2: Good Experience**
- Interaction: "Checkout Process"
- Threshold: 500ms (low) to 2000ms (high)
- Actual duration: 1200ms
- Apdex score: 0.73
- Error rate: 1.5%
- **Categorisation**: Good
- **Reason**: Performance within acceptable range, low error rate, satisfactory user experience

**Example 3: Average Experience**
- Interaction: "Search Results"
- Threshold: 300ms (low) to 1500ms (high)
- Actual duration: 1100ms
- Apdex score: 0.47
- Error rate: 3.5%
- **Categorisation**: Average
- **Reason**: Performance in mid-range, moderate error rate, acceptable but could be improved

**Example 4: Poor Experience**
- Interaction: "Page Navigation"
- Threshold: 200ms (low) to 1000ms (high)
- Actual duration: 1800ms
- Apdex score: 0.2
- Error rate: 8%
- **Categorisation**: Poor
- **Reason**: Performance exceeds high threshold significantly, high error rate, user experience is degraded

## Interaction Latencies

Interaction latencies measure the time taken for interactions to complete. These metrics help identify performance bottlenecks and optimize user experience.

### Examples

**Example 1: Fast Interaction**
- Interaction: "Button Click Response"
- P50 latency: 50ms
- P95 latency: 120ms
- P99 latency: 200ms
- **Analysis**: Very responsive, most users experience sub-100ms response times

**Example 2: Moderate Latency**
- Interaction: "Form Submission"
- P50 latency: 300ms
- P95 latency: 800ms
- P99 latency: 1500ms
- **Analysis**: Acceptable performance, majority of users experience reasonable response times

**Example 3: High Latency**
- Interaction: "Data Export"
- P50 latency: 2000ms
- P95 latency: 5000ms
- P99 latency: 10000ms
- **Analysis**: Slow performance, significant portion of users experience delays, optimization needed

**Example 4: Variable Latency**
- Interaction: "Search Query"
- P50 latency: 150ms
- P95 latency: 2000ms
- P99 latency: 5000ms
- **Analysis**: Inconsistent performance, while median is good, tail latencies are problematic

