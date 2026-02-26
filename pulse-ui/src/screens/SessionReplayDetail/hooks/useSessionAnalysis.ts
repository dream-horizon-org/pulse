/**
 * useSessionAnalysis Hook
 * 
 * PRODUCT PURPOSE:
 * Automatically analyze session data and generate persona-specific insights.
 * 
 * SUPPORT sees: "What broke for the user?" → Plain language, actionable
 * PRODUCT sees: "What's the business impact?" → Metrics, patterns, revenue
 * TECH sees: "What's the root cause?" → Code refs, error chains, repro steps
 * 
 * This hook is the "brain" that makes session replay useful for everyone.
 */

import { useMemo } from 'react';
import { SessionDetailData, DetectedIssue, SessionType } from '../../../services/sessionReplay/mockSessionDetail';

interface SessionAnalysis {
  sessionType: SessionType;
  detectedIssues: DetectedIssue[];
  personaSummaries: {
    support: SupportSummary;
    product: ProductSummary;
    tech: TechSummary;
  };
}

interface SupportSummary {
  headline: string;        // "Payment Failed"
  description: string;     // "User couldn't complete $99 payment"
  severity: 'success' | 'warning' | 'error';
  actionable: boolean;     // Should we show action buttons?
  suggestedActions?: string[];
}

interface ProductSummary {
  headline: string;        // "Conversion Abandoned"
  description: string;     // "User dropped off at Payment after 2m 34s"
  impact: 'high' | 'medium' | 'low' | 'positive' | 'neutral';
  metrics?: {
    expectedDuration?: string;
    actualDuration?: string;
    similarDropoffsToday?: number;
    revenueImpact?: number;
  };
}

interface TechSummary {
  headline: string;        // "TimeoutException"
  rootCause?: string;      // "POST /api/payment returned 504"
  component?: string;      // "PaymentService"
  errorType?: string;      // "TimeoutException"
  reproSteps?: string[];
  relatedSpan?: string;
  metrics?: {
    duration?: string;
    apdex?: string;
    threshold?: string;
  };
}

/**
 * Main Hook
 */
export const useSessionAnalysis = (sessionData: SessionDetailData): SessionAnalysis => {
  // Auto-detect session type (error, abandoned, success, etc.)
  const sessionType = useMemo(() => {
    return detectSessionType(sessionData);
  }, [sessionData]);
  
  // Auto-detect issues (timeouts, errors, slowness, rage clicks)
  const detectedIssues = useMemo(() => {
    return detectIssues(sessionData);
  }, [sessionData]);
  
  // Generate persona-specific summaries
  const personaSummaries = useMemo(() => {
    return {
      support: generateSupportSummary(sessionData, detectedIssues),
      product: generateProductSummary(sessionData, sessionType),
      tech: generateTechSummary(sessionData, detectedIssues)
    };
  }, [sessionData, detectedIssues, sessionType]);
  
  return {
    sessionType,
    detectedIssues,
    personaSummaries
  };
};

// ============================================================================
// SESSION TYPE DETECTION
// ============================================================================

/**
 * Detect Session Type
 * 
 * PRODUCT LOGIC:
 * Priority order matters! Check most critical issues first.
 * 
 * 1. Errors → 'error_encountered' (CRITICAL)
 * 2. Abandonment → 'conversion_abandoned' (HIGH)
 * 3. Slowness → 'performance_issue' (MEDIUM)
 * 4. Success → 'conversion_success' (POSITIVE)
 * 5. Default → 'exploration' (NEUTRAL)
 */
function detectSessionType(data: SessionDetailData): SessionType {
  // 1. CRITICAL: Check for errors first
  const hasExceptions = data.exceptions.rows.length > 0;
  const hasErrorLogs = data.consoleLogs.some(log => log.level === 'error');
  const has5xxErrors = data.networkRequests.some(req => req.status >= 500);
  
  if (hasExceptions || hasErrorLogs || has5xxErrors) {
    return 'error_encountered';
  }
  
  // 2. HIGH: Check for abandonment
  const hasFailedInteractions = data.criticalInteractions.some(i => i.status === 'failed');
  const endsInError = data.journey[data.journey.length - 1]?.includes('error');
  
  if (hasFailedInteractions || endsInError) {
    return 'conversion_abandoned';
  }
  
  // 3. MEDIUM: Check for performance issues
  const hasSlowInteractions = data.performance.interactionMetrics.some(m => m.apdexScore < 0.5);
  const hasSlowAPIs = data.networkRequests.some(req => req.duration > 2000);
  
  if (hasSlowInteractions || hasSlowAPIs) {
    return 'performance_issue';
  }
  
  // 4. POSITIVE: Check for success
  const allSuccess = data.criticalInteractions.every(
    i => i.status === 'success' || i.status === 'not_attempted'
  );
  const hasMultipleSteps = data.journey.length > 2;
  
  if (allSuccess && hasMultipleSteps) {
    return 'conversion_success';
  }
  
  // 5. NEUTRAL: Default to exploration
  return 'exploration';
}

// ============================================================================
// ISSUE DETECTION
// ============================================================================

/**
 * Detect Issues
 * 
 * PRODUCT LOGIC:
 * Find specific problems that users/product/tech teams care about.
 * Each issue has BOTH plain-language AND technical descriptions.
 */
function detectIssues(data: SessionDetailData): DetectedIssue[] {
  const issues: DetectedIssue[] = [];
  
  // DETECT: Timeout errors
  issues.push(...detectTimeoutErrors(data));
  
  // DETECT: Slow interactions
  issues.push(...detectSlowInteractions(data));
  
  // DETECT: API failures
  issues.push(...detectAPIFailures(data));
  
  // DETECT: Client-side errors
  issues.push(...detectClientErrors(data));
  
  // Sort by severity (critical first)
  return issues.sort((a, b) => {
    const severityOrder = { critical: 0, high: 1, medium: 2, low: 3 };
    return severityOrder[a.severity] - severityOrder[b.severity];
  });
}

/**
 * Detect Timeout Errors
 * 
 * SUPPORT: "Payment timed out"
 * PRODUCT: "45 users hit this today"
 * TECH: "504 Gateway Timeout after 1.2s"
 */
function detectTimeoutErrors(data: SessionDetailData): DetectedIssue[] {
  const issues: DetectedIssue[] = [];
  
  // Check exceptions for timeout
  data.exceptions.rows.forEach((row, idx) => {
    const exceptionType = String(row[4]); // exceptionType
    const exceptionMessage = String(row[3]); // exceptionMessage
    
    if (exceptionType.includes('Timeout') || exceptionMessage.includes('timeout')) {
      const timestamp = new Date(row[0] as string).getTime() - new Date(data.startTime).getTime();
      const title = String(row[2]); // title
      const feature = String(row[5]); // screenName
      
      issues.push({
        id: `timeout_${idx}`,
        type: 'timeout',
        severity: 'high',
        timestamp,
        title,
        description: exceptionMessage,
        affectedFeature: feature,
        errorGroup: String(row[8]), // groupId
        relatedSpanId: String(row[7]), // spanId
        
        // SUPPORT: Plain language
        userFacingImpact: `User could not complete action. The request took too long to respond.`,
        
        // TECH: Technical details
        technicalCause: exceptionMessage,
        
        suggestedAction: 'Check API performance or increase timeout threshold'
      });
    }
  });
  
  // Check network requests for timeouts
  data.networkRequests.forEach((req, idx) => {
    if (req.status === 504 || req.duration > 5000) {
      issues.push({
        id: `network_timeout_${idx}`,
        type: 'timeout',
        severity: req.status === 504 ? 'high' : 'medium',
        timestamp: req.timestamp,
        title: `${req.method} ${req.url} Timeout`,
        description: `API request took ${req.duration}ms`,
        affectedFeature: extractFeatureFromURL(req.url),
        
        userFacingImpact: `User's action failed because the server took too long to respond.`,
        technicalCause: `${req.method} ${req.url} returned ${req.status} after ${req.duration}ms`,
        suggestedAction: req.status === 504 ? 'Check gateway/backend service' : 'Optimize API performance'
      });
    }
  });
  
  return issues;
}

/**
 * Detect Slow Interactions
 * 
 * SUPPORT: "User experienced delay"
 * PRODUCT: "2.4x slower than benchmark"
 * TECH: "Apdex 0.4 (below 0.5 threshold)"
 */
function detectSlowInteractions(data: SessionDetailData): DetectedIssue[] {
  const issues: DetectedIssue[] = [];
  
  data.performance.interactionMetrics.forEach(metric => {
    if (metric.apdexScore < 0.5) {
      const interaction = data.criticalInteractions.find(i => i.interactionId === metric.interactionId);
      const severity = metric.apdexScore < 0.3 ? 'high' : 'medium';
      
      issues.push({
        id: `slow_${metric.interactionId}`,
        type: 'slowness',
        severity,
        timestamp: interaction?.timestamp || 0,
        title: `Slow ${metric.interactionName}`,
        description: `Interaction took ${metric.duration}ms (Apdex: ${metric.apdexScore.toFixed(2)})`,
        affectedFeature: interaction?.businessValue || metric.interactionName,
        
        userFacingImpact: `User experienced noticeable delay (${metric.duration}ms). This may feel sluggish.`,
        technicalCause: `Interaction latency ${metric.duration}ms is ${(metric.duration / 500).toFixed(1)}x above 500ms threshold`,
        suggestedAction: 'Investigate API performance or optimize client-side processing'
      });
    }
  });
  
  return issues;
}

/**
 * Detect API Failures
 * 
 * SUPPORT: "Server error prevented action"
 * PRODUCT: "Which API is causing drop-offs?"
 * TECH: "POST /api/payment returned 500"
 */
function detectAPIFailures(data: SessionDetailData): DetectedIssue[] {
  const issues: DetectedIssue[] = [];
  
  data.networkRequests.forEach((req, idx) => {
    if (req.status >= 400 && req.status !== 504) { // Not timeout (handled above)
      const severity = req.status >= 500 ? 'high' : 'medium';
      
      issues.push({
        id: `api_error_${idx}`,
        type: 'error',
        severity,
        timestamp: req.timestamp,
        title: `${req.method} ${req.url} Failed`,
        description: `API returned ${req.status} status code`,
        affectedFeature: extractFeatureFromURL(req.url),
        
        userFacingImpact: `User's action failed due to ${req.status >= 500 ? 'server error' : 'invalid request'}.`,
        technicalCause: `${req.method} ${req.url} returned ${req.status}`,
        suggestedAction: req.status >= 500 ? 'Check backend service health' : 'Review request validation'
      });
    }
  });
  
  return issues;
}

/**
 * Detect Client-Side Errors
 * 
 * SUPPORT: "App crashed"
 * PRODUCT: "Which screen has errors?"
 * TECH: "TypeError at PaymentService:45"
 */
function detectClientErrors(data: SessionDetailData): DetectedIssue[] {
  const issues: DetectedIssue[] = [];
  
  data.consoleLogs.forEach((log, idx) => {
    if (log.level === 'error') {
      issues.push({
        id: `client_error_${idx}`,
        type: 'error',
        severity: 'high',
        timestamp: log.timestamp,
        title: 'Client Error',
        description: log.message,
        
        userFacingImpact: 'App encountered an error. User may have seen error message or crash.',
        technicalCause: log.message + (log.stackTrace ? `\n${log.stackTrace}` : ''),
        suggestedAction: 'Review error logs and stack trace'
      });
    }
  });
  
  return issues;
}

// ============================================================================
// PERSONA SUMMARIES
// ============================================================================

/**
 * Generate Support Summary
 * 
 * GOAL: Help support rep understand and act fast
 * - What broke?
 * - How many others affected?
 * - What should I do?
 */
function generateSupportSummary(data: SessionDetailData, issues: DetectedIssue[]): SupportSummary {
  const criticalIssues = issues.filter(i => i.severity === 'critical' || i.severity === 'high');
  
  // No issues = success
  if (criticalIssues.length === 0) {
    return {
      headline: 'No Critical Issues',
      description: 'Session completed without major errors. User was able to complete their actions successfully.',
      severity: 'success',
      actionable: false
    };
  }
  
  // Has issues = show main issue + actions
  const mainIssue = criticalIssues[0];
  const otherCount = criticalIssues.length - 1;
  
  return {
    headline: mainIssue.title,
    description: mainIssue.userFacingImpact + (otherCount > 0 ? ` (Plus ${otherCount} other issue${otherCount > 1 ? 's' : ''})` : ''),
    severity: 'error',
    actionable: true,
    suggestedActions: [
      'Create Support Ticket',
      'Send Workaround to User',
      'Escalate to Engineering'
    ]
  };
}

/**
 * Generate Product Summary
 * 
 * GOAL: Understand business impact and patterns
 * - Did conversion succeed/fail?
 * - How does this compare to benchmark?
 * - Are there patterns?
 */
function generateProductSummary(data: SessionDetailData, sessionType: SessionType): ProductSummary {
  const failedInteractions = data.criticalInteractions.filter(i => i.status === 'failed');
  
  // ABANDONED: User started but didn't finish
  if (sessionType === 'conversion_abandoned' && failedInteractions.length > 0) {
    const failurePoint = failedInteractions[0];
    const revenueImpact = failurePoint.revenueImpact || 0;
    
    return {
      headline: 'Conversion Abandoned',
      description: `User dropped off at "${failurePoint.displayName}" after ${formatDuration(data.duration)}`,
      impact: 'high',
      metrics: {
        expectedDuration: '2-3 minutes',
        actualDuration: formatDuration(data.duration),
        similarDropoffsToday: data.businessContext?.similarErrorsToday || 0,
        revenueImpact
      }
    };
  }
  
  // SUCCESS: User completed goal
  if (sessionType === 'conversion_success') {
    return {
      headline: 'Conversion Completed',
      description: 'User successfully completed their goal. This is a successful session.',
      impact: 'positive',
      metrics: {
        actualDuration: formatDuration(data.duration),
        similarDropoffsToday: 0
      }
    };
  }
  
  // ERROR: Technical error blocked user
  if (sessionType === 'error_encountered') {
    return {
      headline: 'Error Encountered',
      description: 'Technical error prevented user from completing their action',
      impact: 'high',
      metrics: {
        similarDropoffsToday: data.businessContext?.similarErrorsToday || 0
      }
    };
  }
  
  // PERFORMANCE: Slow but completed
  if (sessionType === 'performance_issue') {
    return {
      headline: 'Performance Issue',
      description: 'User experienced slow interactions but may have completed their goal',
      impact: 'medium',
      metrics: {
        actualDuration: formatDuration(data.duration)
      }
    };
  }
  
  // EXPLORATION: Just browsing
  return {
    headline: 'Exploratory Session',
    description: 'User browsing without clear conversion intent. No critical issues detected.',
    impact: 'neutral'
  };
}

/**
 * Generate Tech Summary
 * 
 * GOAL: Find root cause and enable fix
 * - What's the error?
 * - Where in code?
 * - How to reproduce?
 */
function generateTechSummary(data: SessionDetailData, issues: DetectedIssue[]): TechSummary {
  const errors = data.exceptions.rows;
  const slowInteractions = data.performance.interactionMetrics.filter(m => m.apdexScore < 0.5);
  
  // ERRORS: Show main error with technical details
  if (errors.length > 0) {
    const mainError = errors[0];
    return {
      headline: String(mainError[2]), // title
      rootCause: String(mainError[3]), // exceptionMessage
      component: String(mainError[5]), // screenName
      errorType: String(mainError[4]), // exceptionType
      reproSteps: data.technicalContext?.reproductionSteps || [],
      relatedSpan: String(mainError[7]) // spanId
    };
  }
  
  // PERFORMANCE: Show slowest interaction
  if (slowInteractions.length > 0) {
    const slowest = slowInteractions[0];
    return {
      headline: `Performance Issue: ${slowest.interactionName}`,
      rootCause: 'Slow API response or client-side bottleneck',
      component: slowest.interactionName,
      metrics: {
        duration: `${slowest.duration}ms`,
        apdex: slowest.apdexScore.toFixed(2),
        threshold: '500ms'
      }
    };
  }
  
  // NO ISSUES: All good
  return {
    headline: 'No Technical Issues',
    rootCause: 'Session completed without errors or performance degradation'
  };
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

function formatDuration(ms: number): string {
  if (ms < 60000) return `${Math.round(ms / 1000)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
}

function extractFeatureFromURL(url: string): string {
  // Extract feature name from URL
  // /api/payment → Payment
  // /api/user/profile → User Profile
  const parts = url.split('/').filter(p => p && p !== 'api');
  return parts.map(p => p.charAt(0).toUpperCase() + p.slice(1)).join(' ') || 'Unknown';
}
