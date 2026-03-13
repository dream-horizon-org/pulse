import {
  SessionResponse,
  SessionTagResponse,
  GetSessionDetailResponse,
  SessionEventResponse,
  TimelineEntry,
  GetFilterSchemaResponse,
  GetDateRangeConfigResponse,
  GetQuickFiltersResponse,
} from './types';

// Mock data generator
export class MockSessionReplayData {
  private static readonly USERS = [
    'user_1234',
    'user_5678',
    'user_9012',
    'user_3456',
    'anonymous',
  ];

  private static readonly DEVICES = [
    { device: 'Android', browser: 'Chrome 121', os: 'Android 14' },
    { device: 'Android', browser: 'Chrome 120', os: 'Android 13' },
    { device: 'Android', browser: 'WebView', os: 'Android 14' },
    { device: 'iOS', browser: 'Safari 17', os: 'iOS 17.2' },
    { device: 'iOS', browser: 'Safari 16', os: 'iOS 16.5' },
  ];

  private static readonly JOURNEYS = [
    ['/home', '/contest', '/pay'],
    ['/home', '/profile', '/settings'],
    ['/home', '/search', '/contest', '/pay', '/wallet'],
    ['/home', '/offers'],
    ['/login', '/home', '/contest'],
  ];

  private static readonly TAG_TYPES: SessionTagResponse['type'][] = [
    'rage',
    'slow',
    'new',
    'js_error',
    'network_fail',
    'dead_click',
  ];

  static generateSession(id: number): SessionResponse {
    const isAnonymous = Math.random() > 0.6;
    const userId = isAnonymous ? null : this.USERS[Math.floor(Math.random() * (this.USERS.length - 1))];
    const deviceInfo = this.DEVICES[Math.floor(Math.random() * this.DEVICES.length)];
    const journey = this.JOURNEYS[Math.floor(Math.random() * this.JOURNEYS.length)];
    const hasErrors = Math.random() > 0.6;
    const errorCount = hasErrors ? Math.floor(Math.random() * 5) + 1 : 0;

    const tags: SessionTagResponse[] = [];
    
    if (hasErrors) {
      tags.push({ type: 'js_error', count: errorCount, severity: errorCount > 2 ? 'high' : 'medium' });
    }
    
    if (Math.random() > 0.8) {
      tags.push({ type: 'rage', count: Math.floor(Math.random() * 5) + 1, severity: 'high' });
    }
    
    if (Math.random() > 0.7) {
      tags.push({ type: 'slow', severity: 'medium' });
    }
    
    if (isAnonymous && Math.random() > 0.5) {
      tags.push({ type: 'new', severity: 'low' });
    }
    
    if (hasErrors && Math.random() > 0.6) {
      tags.push({ type: 'network_fail', count: Math.floor(Math.random() * 3) + 1, severity: 'high' });
    }

    const startTime = new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000);
    const duration = Math.floor(Math.random() * 600000) + 60000; // 1-10 minutes
    const events = Math.floor(Math.random() * 100) + 20;
    const pages = journey.length;
    
    // Calculate interaction quality score (0-10)
    // Lower scores for sessions with more issues
    const issueCount = tags.length + (errorCount > 0 ? 1 : 0);
    const baseQuality = 10 - (issueCount * 1.5);
    const interactionQuality = Math.max(1, Math.min(10, baseQuality + (Math.random() - 0.5)));
    
    // Build issue summary
    const hasFrustration = tags.some(t => t.type === 'rage' || t.type === 'dead_click');
    const isSlow = tags.some(t => t.type === 'slow');
    const crashed = tags.some(t => t.type === 'network_fail' && t.count && t.count > 2);
    const failedInteractions = tags.some(t => t.type === 'dead_click' || t.type === 'network_fail');
    const hasIssues = tags.length > 0 || errorCount > 0;
    
    const issueSummary = {
      hasIssues,
      failedInteractions,
      hasErrors: errorCount > 0,
      hasFrustration,
      isSlow,
      crashed,
      issueCount,
    };
    
    // Determine outcome
    let outcome: 'completed' | 'incomplete' | 'drop_off' | 'crashed';
    if (crashed) {
      outcome = 'crashed';
    } else if (duration < 30000 || pages < 2) {
      outcome = 'drop_off';
    } else if (hasIssues && Math.random() > 0.5) {
      outcome = 'incomplete';
    } else {
      outcome = 'completed';
    }

    return {
      id: id.toString(),
      sessionId: `sess_${Math.random().toString(36).substr(2, 12)}`,
      startTime: startTime.toISOString(),
      userId,
      isAnonymous,
      duration,
      pages,
      events,
      errors: errorCount,
      device: deviceInfo.device,
      browser: deviceInfo.browser,
      os: deviceInfo.os,
      journey,
      tags,
      environment: Math.random() > 0.1 ? 'production' : 'staging',
      project: deviceInfo.device === 'iOS' ? 'ios' : 'android',
      interactionQuality: Number(interactionQuality.toFixed(1)),
      issueSummary,
      outcome,
      metadata: {
        buildVersion: `4.${Math.floor(Math.random() * 30)}.0`,
        country: Math.random() > 0.5 ? 'IN' : 'US',
      },
    };
  }

  static generateSessions(count: number = 50): SessionResponse[] {
    return Array.from({ length: count }, (_, i) => this.generateSession(i + 1));
  }

  static generateSessionDetail(sessionId: string): GetSessionDetailResponse {
    const session = this.generateSession(1);
    session.sessionId = sessionId;

    const events: SessionEventResponse[] = [
      {
        id: '1',
        timestamp: session.startTime,
        type: 'navigation',
        target: '/home',
        metadata: { url: 'https://example.com/home' },
      },
      {
        id: '2',
        timestamp: new Date(new Date(session.startTime).getTime() + 5000).toISOString(),
        type: 'click',
        target: 'button#contest-join',
        value: null,
      },
      {
        id: '3',
        timestamp: new Date(new Date(session.startTime).getTime() + 15000).toISOString(),
        type: 'error',
        target: 'window',
        value: {
          message: 'TypeError: Cannot read property of undefined',
          stack: 'at handleClick (app.js:123)',
        },
      },
    ];

    const timeline: TimelineEntry[] = [
      {
        timestamp: session.startTime,
        type: 'page_view',
        description: 'Landed on home page',
      },
      {
        timestamp: new Date(new Date(session.startTime).getTime() + 5000).toISOString(),
        type: 'interaction',
        description: 'Clicked join contest button',
      },
      {
        timestamp: new Date(new Date(session.startTime).getTime() + 15000).toISOString(),
        type: 'error',
        description: 'JavaScript error occurred',
      },
    ];

    return {
      session,
      events,
      timeline,
    };
  }

  static filterSessions(
    sessions: SessionResponse[],
    filters: {
      environment?: string;
      project?: string;
      hasErrors?: boolean;
      rageClicks?: boolean;
      slowSessions?: boolean;
      mobile?: boolean;
      newUsers?: boolean;
      searchQuery?: string;
    }
  ): SessionResponse[] {
    return sessions.filter(session => {
      if (filters.environment && filters.environment !== 'all' && session.environment !== filters.environment) {
        return false;
      }

      if (filters.project && filters.project !== 'all' && session.project !== filters.project) {
        return false;
      }

      if (filters.hasErrors && session.errors === 0) {
        return false;
      }

      if (filters.rageClicks && !session.tags.some(t => t.type === 'rage')) {
        return false;
      }

      if (filters.slowSessions && !session.tags.some(t => t.type === 'slow')) {
        return false;
      }

      if (filters.mobile && session.device !== 'Android' && session.device !== 'iOS') {
        return false;
      }

      if (filters.newUsers && !session.tags.some(t => t.type === 'new')) {
        return false;
      }

      if (filters.searchQuery) {
        const query = filters.searchQuery.toLowerCase();
        const matchesUserId = session.userId?.toLowerCase().includes(query);
        const matchesSessionId = session.sessionId.toLowerCase().includes(query);
        const matchesJourney = session.journey.some(path => path.toLowerCase().includes(query));
        
        if (!matchesUserId && !matchesSessionId && !matchesJourney) {
          return false;
        }
      }

      return true;
    });
  }

  static paginateSessions(
    sessions: SessionResponse[],
    page: number = 1,
    pageSize: number = 10
  ): { sessions: SessionResponse[]; total: number; totalPages: number } {
    const start = (page - 1) * pageSize;
    const end = start + pageSize;
    
    return {
      sessions: sessions.slice(start, end),
      total: sessions.length,
      totalPages: Math.ceil(sessions.length / pageSize),
    };
  }

  static calculateMetrics(sessions: SessionResponse[]) {
    const totalSessions = sessions.length;
    
    // Calculate sessions with issues (any tag indicates an issue)
    const sessionsWithIssues = sessions.filter(s => 
      s.tags.length > 0 || s.errors > 0
    ).length;
    
    const issueRate = totalSessions > 0 ? (sessionsWithIssues / totalSessions) * 100 : 0;
    
    // Calculate clean sessions
    const cleanSessions = totalSessions - sessionsWithIssues;
    const cleanRate = totalSessions > 0 ? (cleanSessions / totalSessions) * 100 : 0;
    
    // Calculate interaction quality score (0-10 scale)
    // Lower scores for more issues
    const avgInteractionQuality = totalSessions > 0
      ? Math.max(1, 10 - (issueRate / 10)) // 0% issues = 10/10, 100% issues = 1/10
      : 10;
    
    // Mock trend (would come from comparing to previous period in real implementation)
    const qualityTrend = -0.3; // Slight decrease
    
    // Issue breakdown
    const failedInteractions = sessions.filter(s => 
      s.tags.some(t => t.type === 'dead_click' || t.type === 'network_fail')
    ).length;
    
    const errorsAndCrashes = sessions.filter(s => 
      s.errors > 0 || s.tags.some(t => t.type === 'js_error')
    ).length;
    
    const frustrationSignals = sessions.filter(s => 
      s.tags.some(t => t.type === 'rage' || t.type === 'slow')
    ).length;
    
    const dropOffs = sessions.filter(s => 
      s.outcome === 'drop_off'
    ).length;
    
    // Critical Interaction Performance - aligned with Pulse's existing system
    const criticalInteractions = [
      {
        // Payment button tap interaction
        interactionId: 1,
        interactionName: 'tap_pay_button',
        displayName: 'Payment Button Tap',
        description: 'User taps the payment button on checkout screen',
        
        // Pulse-standard metrics
        apdexScore: 0.45,  // Poor performance
        errorRate: 45.0,   // 45% of taps fail
        p50Latency: 1200,
        p95Latency: 3500,
        p99Latency: 5200,
        poorUserPercentage: 55.0,
        
        // Health status (based on Apdex thresholds)
        healthStatus: 'Poor' as const,
        
        // Breakdown
        totalAttempts: sessions.filter(s => s.journey.some(p => p.includes('/pay'))).length,
        successfulAttempts: Math.round(sessions.filter(s => s.journey.some(p => p.includes('/pay'))).length * 0.55),
        failedAttempts: Math.round(sessions.filter(s => s.journey.some(p => p.includes('/pay'))).length * 0.45),
        uniqueUsers: new Set(sessions.filter(s => s.journey.some(p => p.includes('/pay'))).map(s => s.userId || s.sessionId)).size,
        
        // Thresholds (from Pulse server config)
        lowThreshold: 500,
        highThreshold: 2000,
        
        // Session context
        sessionsWithThisInteraction: sessions.filter(s => s.journey.some(p => p.includes('/pay'))).length,
        
        // Business impact
        estimatedLoss: {
          type: 'revenue' as const,
          amount: 12000,
          unit: '$',
          period: 'week' as const,
        },
        
        severity: 'critical' as const,
      },
      {
        // Signup form interaction
        interactionId: 2,
        interactionName: 'form_submit_signup',
        displayName: 'Signup Form Submit',
        description: 'User submits signup form',
        
        // Pulse-standard metrics
        apdexScore: 0.65,  // Good performance
        errorRate: 22.0,
        p50Latency: 800,
        p95Latency: 1800,
        p99Latency: 2500,
        poorUserPercentage: 28.0,
        
        // Health status
        healthStatus: 'Good' as const,
        
        // Breakdown
        totalAttempts: sessions.filter(s => s.journey.some(p => p.includes('/signup') || p.includes('/login'))).length,
        successfulAttempts: Math.round(sessions.filter(s => s.journey.some(p => p.includes('/signup') || p.includes('/login'))).length * 0.78),
        failedAttempts: Math.round(sessions.filter(s => s.journey.some(p => p.includes('/signup') || p.includes('/login'))).length * 0.22),
        uniqueUsers: new Set(sessions.filter(s => s.journey.some(p => p.includes('/signup') || p.includes('/login'))).map(s => s.userId || s.sessionId)).size,
        
        // Thresholds
        lowThreshold: 300,
        highThreshold: 1500,
        
        // Session context
        sessionsWithThisInteraction: sessions.filter(s => s.journey.some(p => p.includes('/signup') || p.includes('/login'))).length,
        
        // Business impact
        estimatedLoss: {
          type: 'users' as const,
          amount: 340,
          unit: 'users',
          period: 'week' as const,
        },
        
        severity: 'high' as const,
      },
      {
        // Contest join interaction
        interactionId: 3,
        interactionName: 'tap_join_contest',
        displayName: 'Contest Join Button',
        description: 'User taps join button on contest page',
        
        // Pulse-standard metrics
        apdexScore: 0.88,  // Excellent performance
        errorRate: 8.0,
        p50Latency: 250,
        p95Latency: 600,
        p99Latency: 1100,
        poorUserPercentage: 10.0,
        
        // Health status
        healthStatus: 'Excellent' as const,
        
        // Breakdown
        totalAttempts: sessions.filter(s => s.journey.some(p => p.includes('/contest'))).length,
        successfulAttempts: Math.round(sessions.filter(s => s.journey.some(p => p.includes('/contest'))).length * 0.92),
        failedAttempts: Math.round(sessions.filter(s => s.journey.some(p => p.includes('/contest'))).length * 0.08),
        uniqueUsers: new Set(sessions.filter(s => s.journey.some(p => p.includes('/contest'))).map(s => s.userId || s.sessionId)).size,
        
        // Thresholds
        lowThreshold: 200,
        highThreshold: 1000,
        
        // Session context
        sessionsWithThisInteraction: sessions.filter(s => s.journey.some(p => p.includes('/contest'))).length,
        
        // Business impact
        estimatedLoss: {
          type: 'conversion' as const,
          amount: 2,
          unit: '%',
          period: 'week' as const,
        },
        
        severity: 'low' as const,
      },
    ];
    
    // Business Impact
    const totalUsersInPeriod = new Set(sessions.map(s => s.userId || s.sessionId)).size;
    const affectedUsersCount = new Set(
      sessions.filter(s => s.tags.length > 0 || s.errors > 0).map(s => s.userId || s.sessionId)
    ).size;
    
    const estimatedImpact = {
      totalRevenueAtRisk: 12000,
      revenueAtRiskPeriod: 'week' as const,
      affectedUsers: affectedUsersCount,
      totalUsers: totalUsersInPeriod,
      affectedUsersPercentage: totalUsersInPeriod > 0 ? (affectedUsersCount / totalUsersInPeriod) * 100 : 0,
      conversionImpact: 8.5, // percentage points lost
      conversionBaseline: 85.0, // Historical baseline
      supportTicketCorrelation: {
        count: 34,
        confidence: 'high' as const,
        totalTickets: 120,
      },
    };
    
    // Top Issue Hotspots - what UX needs to see
    // IMPORTANT: Limit to top 3 hotspots (sorted by severity × hit rate)
    const paySessionsAtLocation = sessions.filter(s => s.journey.includes('/pay')).length;
    const signupSessionsAtLocation = sessions.filter(s => s.journey.includes('/signup')).length;
    const contestSessionsAtLocation = sessions.filter(s => s.journey.includes('/contest')).length;
    
    const topIssueHotspots = [
      {
        location: '/pay (Payment page)',
        issueType: 'dead_click' as const,
        affectedSessions: 23,
        totalSessionsAtLocation: paySessionsAtLocation,
        hitRate: paySessionsAtLocation > 0 ? (23 / paySessionsAtLocation) * 100 : 0,
        uniqueUsers: 18,
        avgStruggleTime: 45,
        medianStruggleTime: 32,
        struggleTimeRange: { min: 15, max: 120 },
        specificElement: '"Submit Payment" button',
        elementIdentifier: 'data-testid="submit-payment-btn"',
        severity: 'critical' as const,
      },
      {
        location: '/signup (Signup form)',
        issueType: 'form_abandon' as const,
        affectedSessions: 18,
        totalSessionsAtLocation: signupSessionsAtLocation,
        hitRate: signupSessionsAtLocation > 0 ? (18 / signupSessionsAtLocation) * 100 : 0,
        uniqueUsers: 14,
        avgStruggleTime: 32,
        medianStruggleTime: 28,
        struggleTimeRange: { min: 10, max: 90 },
        specificElement: 'Email validation field',
        elementIdentifier: 'id="email-input"',
        severity: 'high' as const,
      },
      {
        location: '/contest (Contest selection)',
        issueType: 'rage_click' as const,
        affectedSessions: 12,
        totalSessionsAtLocation: contestSessionsAtLocation,
        hitRate: contestSessionsAtLocation > 0 ? (12 / contestSessionsAtLocation) * 100 : 0,
        uniqueUsers: 10,
        avgStruggleTime: 28,
        medianStruggleTime: 22,
        struggleTimeRange: { min: 8, max: 65 },
        specificElement: '"Join Now" button',
        elementIdentifier: 'data-testid="join-contest-btn"',
        severity: 'medium' as const,
      },
    ];
    // Backend should return these sorted by: severity (critical > high > medium > low) then hitRate (desc)
    
    // Error Patterns - clusters identical errors to avoid watching duplicate sessions
    // IMPORTANT: Limit to top 3 patterns (sorted by severity × affected sessions)
    const topErrorPatterns = [
      {
        errorSignature: 'POST_/api/payment_504',
        displayName: 'POST /api/payment → 504 Gateway Timeout',
        errorType: 'network' as const,
        count: 32, // Total occurrences
        affectedSessions: 28, // Unique sessions with this error
        uniqueUsers: 23, // Unique users affected
        firstSeen: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
        lastSeen: new Date().toISOString(),
        platformBreakdown: [
          { platform: 'iOS', count: 18 },
          { platform: 'Android', count: 14 },
        ],
        sampleSessionId: sessions[0]?.sessionId || 'sess_payment_timeout_001', // ONE representative session
        severity: 'critical' as const,
      },
      {
        errorSignature: 'TypeError_amount_undefined',
        displayName: 'TypeError: Cannot read property \'amount\' of undefined',
        errorType: 'javascript' as const,
        count: 18, // Total occurrences
        affectedSessions: 16, // Unique sessions with this error
        uniqueUsers: 12, // Unique users affected
        firstSeen: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
        lastSeen: new Date().toISOString(),
        platformBreakdown: [
          { platform: 'Web', count: 18 },
        ],
        sampleSessionId: sessions[1]?.sessionId || 'sess_js_error_001',
        severity: 'high' as const,
      },
      {
        errorSignature: 'App_Crash_ANR',
        displayName: 'App crashed: Application Not Responding',
        errorType: 'crash' as const,
        count: 8, // Total occurrences
        affectedSessions: 8, // Unique sessions with this error
        uniqueUsers: 8, // Unique users affected
        firstSeen: new Date(Date.now() - 4 * 24 * 60 * 60 * 1000).toISOString(),
        lastSeen: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
        platformBreakdown: [
          { platform: 'Android', count: 8 },
        ],
        sampleSessionId: sessions[2]?.sessionId || 'sess_anr_crash_001',
        severity: 'high' as const,
      },
    ];
    // Backend should return these sorted by: severity (critical > high > medium > low) then affectedSessions (desc)
    
    // Comparison & Trends - provides context
    const now = new Date();
    const currentPeriodStart = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    const comparisonPeriodStart = new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000);
    const comparisonPeriodEnd = currentPeriodStart;
    
    const sessionsWithIssuesCount = sessions.filter(s => 
      s.tags.length > 0 || s.errors > 0
    ).length;
    const previousSessionsWithIssuesCount = Math.round(totalSessions * 0.65); // Simulated previous period
    
    const comparison = {
      currentPeriod: {
        start: currentPeriodStart.toISOString(),
        end: now.toISOString(),
        label: `${currentPeriodStart.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}-${now.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`,
      },
      comparisonPeriod: {
        start: comparisonPeriodStart.toISOString(),
        end: comparisonPeriodEnd.toISOString(),
        label: `${comparisonPeriodStart.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}-${comparisonPeriodEnd.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}`,
      },
      totalSessions: {
        current: totalSessions,
        previous: totalSessions, // Assume same volume
        change: 0,
        changePercent: 0,
      },
      sessionsWithIssues: {
        current: sessionsWithIssuesCount,
        currentPercent: totalSessions > 0 ? (sessionsWithIssuesCount / totalSessions) * 100 : 0,
        previous: previousSessionsWithIssuesCount,
        previousPercent: 65.0,
        change: ((sessionsWithIssuesCount / totalSessions) * 100) - 65.0, // Change in percentage points
        trend: sessionsWithIssuesCount > previousSessionsWithIssuesCount ? 'declining' as const : 'improving' as const,
      },
      topDegradedFlows: [
        { flowName: 'Payment', changeInSuccessRate: -30.0 }, // 55% - 85% baseline
        { flowName: 'Signup', changeInSuccessRate: -10.0 },
      ],
      topImprovedFlows: [
        { flowName: 'Contest Join', changeInSuccessRate: 2.0 },
      ],
    };

    // Journey Analysis - Top user paths
    const topJourneys = [
      {
        path: ['Home', 'Contest', 'Join Contest', 'Success'],
        sessionCount: 127,
        completionRate: 82.5,
        avgDuration: 45000,
        conversionRate: 82.5,
        pathLength: 4,
        isCompleted: true,
        isBounce: false,
      },
      {
        path: ['Home', 'Profile', 'Payment', 'Checkout'],
        sessionCount: 89,
        completionRate: 48.3,
        avgDuration: 182000,
        dropOffPoint: 'Payment',
        conversionRate: 48.3,
        pathLength: 4,
        isCompleted: false,
        isBounce: false,
      },
      {
        path: ['Home', 'Signup', 'Verification', 'Complete'],
        sessionCount: 76,
        completionRate: 65.8,
        avgDuration: 95000,
        dropOffPoint: 'Verification',
        conversionRate: 65.8,
        pathLength: 4,
        isCompleted: true,
        isBounce: false,
      },
      {
        path: ['Home', 'Contest', 'Abandon'],
        sessionCount: 54,
        completionRate: 0,
        avgDuration: 12000,
        dropOffPoint: 'Contest',
        pathLength: 3,
        isCompleted: false,
        isBounce: false,
      },
      {
        path: ['Home', 'Profile', 'Settings', 'Logout'],
        sessionCount: 43,
        completionRate: 100,
        avgDuration: 28000,
        pathLength: 4,
        isCompleted: true,
        isBounce: false,
      },
      {
        path: ['Home', 'Search', 'Contest Details', 'Join'],
        sessionCount: 38,
        completionRate: 71.1,
        avgDuration: 52000,
        pathLength: 4,
        isCompleted: true,
        isBounce: false,
      },
      {
        path: ['Home', 'Payment', 'Abandon'],
        sessionCount: 32,
        completionRate: 0,
        avgDuration: 85000,
        dropOffPoint: 'Payment',
        pathLength: 3,
        isCompleted: false,
        isBounce: false,
      },
      // BOUNCES - Single screen visits (will be filtered out in UI)
      {
        path: ['Home'],
        sessionCount: 234, // High count - users just opening and killing app
        completionRate: 0,
        avgDuration: 2000, // 2 seconds
        dropOffPoint: 'Home',
        pathLength: 1,
        isCompleted: false,
        isBounce: true,
      },
      {
        path: ['Splash'],
        sessionCount: 156,
        completionRate: 0,
        avgDuration: 1500,
        dropOffPoint: 'Splash',
        pathLength: 1,
        isCompleted: false,
        isBounce: true,
      },
      {
        path: ['Home', 'Profile', 'Edit Profile', 'Save'],
        sessionCount: 28,
        completionRate: 89.3,
        avgDuration: 67000,
      },
      {
        path: ['Home', 'Contest', 'Leaderboard', 'Exit'],
        sessionCount: 21,
        completionRate: 100,
        avgDuration: 18000,
      },
      {
        path: ['Home', 'Signup', 'Abandon'],
        sessionCount: 19,
        completionRate: 0,
        avgDuration: 34000,
        dropOffPoint: 'Signup',
      },
    ];

    // Segmentation Analysis
    const iosSessions = sessions.filter(s => s.device === 'iOS');
    const androidSessions = sessions.filter(s => s.device === 'Android');
    const webSessions = sessions.filter(s => s.device !== 'iOS' && s.device !== 'Android');
    
    const segmentation = {
      byPlatform: [
        {
          platform: 'iOS' as const,
          sessionCount: iosSessions.length,
          errorRate: iosSessions.length > 0 ? (iosSessions.filter(s => s.errors > 0).length / iosSessions.length) * 100 : 0,
          topIssue: 'Network timeout',
        },
        {
          platform: 'Android' as const,
          sessionCount: androidSessions.length,
          errorRate: androidSessions.length > 0 ? (androidSessions.filter(s => s.errors > 0).length / androidSessions.length) * 100 : 0,
          topIssue: 'App crashes on startup',
        },
        {
          platform: 'Web' as const,
          sessionCount: webSessions.length,
          errorRate: webSessions.length > 0 ? (webSessions.filter(s => s.errors > 0).length / webSessions.length) * 100 : 0,
          topIssue: 'JS bundle load error',
        },
      ],
      // Optional: App version breakdown (example data)
      byAppVersion: [
        {
          version: '2.5.1',
          platform: 'iOS' as const,
          sessionCount: Math.round(iosSessions.length * 0.65),
          errorRate: 8.5,
          releaseDate: '2024-02-01',
          isLatest: true,
        },
        {
          version: '2.5.0',
          platform: 'iOS' as const,
          sessionCount: Math.round(iosSessions.length * 0.25),
          errorRate: 12.3,
          releaseDate: '2024-01-15',
          isLatest: false,
        },
        {
          version: '2.4.8',
          platform: 'iOS' as const,
          sessionCount: Math.round(iosSessions.length * 0.10),
          errorRate: 15.8,
          releaseDate: '2023-12-20',
          isLatest: false,
        },
        {
          version: '2.5.1',
          platform: 'Android' as const,
          sessionCount: Math.round(androidSessions.length * 0.55),
          errorRate: 14.2,
          releaseDate: '2024-02-01',
          isLatest: true,
        },
        {
          version: '2.5.0',
          platform: 'Android' as const,
          sessionCount: Math.round(androidSessions.length * 0.30),
          errorRate: 18.9,
          releaseDate: '2024-01-15',
          isLatest: false,
        },
      ],
      // Optional: Geographic breakdown (top 5)
      byCountry: [
        { countryCode: 'US', countryName: 'United States', sessionCount: Math.round(totalSessions * 0.35), errorRate: 11.2 },
        { countryCode: 'IN', countryName: 'India', sessionCount: Math.round(totalSessions * 0.20), errorRate: 18.5 },
        { countryCode: 'GB', countryName: 'United Kingdom', sessionCount: Math.round(totalSessions * 0.12), errorRate: 9.8 },
        { countryCode: 'CA', countryName: 'Canada', sessionCount: Math.round(totalSessions * 0.08), errorRate: 10.5 },
        { countryCode: 'DE', countryName: 'Germany', sessionCount: Math.round(totalSessions * 0.06), errorRate: 13.2 },
      ],
    };

    // Performance Metrics - Core Web Vitals & RUM
    const performance = {
      coreWebVitals: {
        lcp: {
          p50: 1800,
          p75: 2400,
          p95: 3900,
          rating: 'needs-improvement' as const,
          sessionsGood: Math.round(totalSessions * 0.35),
          sessionsNeedsImprovement: Math.round(totalSessions * 0.45),
          sessionsPoor: Math.round(totalSessions * 0.20),
        },
        fid: {
          p50: 85,
          p75: 120,
          p95: 210,
          rating: 'good' as const,
          sessionsGood: Math.round(totalSessions * 0.72),
          sessionsNeedsImprovement: Math.round(totalSessions * 0.18),
          sessionsPoor: Math.round(totalSessions * 0.10),
        },
        cls: {
          p50: 0.08,
          p75: 0.15,
          p95: 0.28,
          rating: 'needs-improvement' as const,
          sessionsGood: Math.round(totalSessions * 0.48),
          sessionsNeedsImprovement: Math.round(totalSessions * 0.32),
          sessionsPoor: Math.round(totalSessions * 0.20),
        },
      },
      pageLoadDistribution: {
        under1s: 18,
        between1and3s: 42,
        between3and5s: 28,
        over5s: 12,
        avgLoadTime: 2850,
      },
      networkHealth: {
        apiSuccessRate: 92.5,
        avgApiLatency: 385,
        failedRequests: Math.round(totalSessions * 8 * 0.075), // Avg 8 API calls per session, 7.5% fail
        totalRequests: Math.round(totalSessions * 8),
        slowRequests: Math.round(totalSessions * 8 * 0.12), // 12% are slow (>3s)
      },
    };

    // Time-based Patterns
    const timePatterns = {
      peakErrorHours: [
        { hour: 0, errorCount: 8, sessionCount: 45, errorRate: 17.8 },
        { hour: 1, errorCount: 5, sessionCount: 32, errorRate: 15.6 },
        { hour: 2, errorCount: 3, sessionCount: 21, errorRate: 14.3 },
        { hour: 3, errorCount: 2, sessionCount: 18, errorRate: 11.1 },
        { hour: 4, errorCount: 3, sessionCount: 24, errorRate: 12.5 },
        { hour: 5, errorCount: 7, sessionCount: 38, errorRate: 18.4 },
        { hour: 6, errorCount: 12, sessionCount: 67, errorRate: 17.9 },
        { hour: 7, errorCount: 18, sessionCount: 95, errorRate: 18.9 },
        { hour: 8, errorCount: 24, sessionCount: 128, errorRate: 18.8 },
        { hour: 9, errorCount: 35, sessionCount: 156, errorRate: 22.4 }, // Peak errors
        { hour: 10, errorCount: 42, sessionCount: 185, errorRate: 22.7 }, // Peak errors
        { hour: 11, errorCount: 38, sessionCount: 172, errorRate: 22.1 },
        { hour: 12, errorCount: 32, sessionCount: 165, errorRate: 19.4 },
        { hour: 13, errorCount: 28, sessionCount: 158, errorRate: 17.7 },
        { hour: 14, errorCount: 31, sessionCount: 176, errorRate: 17.6 },
        { hour: 15, errorCount: 29, sessionCount: 168, errorRate: 17.3 },
        { hour: 16, errorCount: 26, sessionCount: 152, errorRate: 17.1 },
        { hour: 17, errorCount: 22, sessionCount: 142, errorRate: 15.5 },
        { hour: 18, errorCount: 19, sessionCount: 125, errorRate: 15.2 },
        { hour: 19, errorCount: 21, sessionCount: 118, errorRate: 17.8 },
        { hour: 20, errorCount: 18, sessionCount: 98, errorRate: 18.4 },
        { hour: 21, errorCount: 15, sessionCount: 82, errorRate: 18.3 },
        { hour: 22, errorCount: 12, sessionCount: 64, errorRate: 18.8 },
        { hour: 23, errorCount: 10, sessionCount: 52, errorRate: 19.2 },
      ],
      performanceByHour: [
        { hour: 0, avgResponseTime: 245, rating: 'excellent' as const },
        { hour: 1, avgResponseTime: 238, rating: 'excellent' as const },
        { hour: 2, avgResponseTime: 232, rating: 'excellent' as const },
        { hour: 3, avgResponseTime: 228, rating: 'excellent' as const },
        { hour: 4, avgResponseTime: 235, rating: 'excellent' as const },
        { hour: 5, avgResponseTime: 268, rating: 'excellent' as const },
        { hour: 6, avgResponseTime: 312, rating: 'good' as const },
        { hour: 7, avgResponseTime: 358, rating: 'good' as const },
        { hour: 8, avgResponseTime: 425, rating: 'good' as const },
        { hour: 9, avgResponseTime: 485, rating: 'fair' as const },
        { hour: 10, avgResponseTime: 512, rating: 'fair' as const },
        { hour: 11, avgResponseTime: 498, rating: 'fair' as const },
        { hour: 12, avgResponseTime: 445, rating: 'good' as const },
        { hour: 13, avgResponseTime: 432, rating: 'good' as const },
        { hour: 14, avgResponseTime: 458, rating: 'good' as const },
        { hour: 15, avgResponseTime: 442, rating: 'good' as const },
        { hour: 16, avgResponseTime: 418, rating: 'good' as const },
        { hour: 17, avgResponseTime: 385, rating: 'good' as const },
        { hour: 18, avgResponseTime: 362, rating: 'good' as const },
        { hour: 19, avgResponseTime: 342, rating: 'good' as const },
        { hour: 20, avgResponseTime: 328, rating: 'good' as const },
        { hour: 21, avgResponseTime: 298, rating: 'good' as const },
        { hour: 22, avgResponseTime: 278, rating: 'excellent' as const },
        { hour: 23, avgResponseTime: 258, rating: 'excellent' as const },
      ],
      errorTrend: [
        { date: new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], errorCount: 145, sessionCount: 892, errorRate: 16.3 },
        { date: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], errorCount: 168, sessionCount: 925, errorRate: 18.2 },
        { date: new Date(Date.now() - 4 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], errorCount: 189, sessionCount: 952, errorRate: 19.9 },
        { date: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], errorCount: 178, sessionCount: 935, errorRate: 19.0 },
        { date: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], errorCount: 195, sessionCount: 968, errorRate: 20.1 },
        { date: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], errorCount: 212, sessionCount: 985, errorRate: 21.5 },
        { date: new Date().toISOString().split('T')[0], errorCount: 198, sessionCount: 942, errorRate: 21.0 },
      ],
    };

    // Geographic & Browser Distribution
    const geographic = {
      topRegions: [
        { country: 'United States', countryCode: 'US', sessionCount: Math.round(totalSessions * 0.35), errorRate: 18.5, avgPerformance: 0.72, topIssue: 'Payment failures' },
        { country: 'India', countryCode: 'IN', sessionCount: Math.round(totalSessions * 0.25), errorRate: 22.8, avgPerformance: 0.65, topIssue: 'Slow load times' },
        { country: 'United Kingdom', countryCode: 'GB', sessionCount: Math.round(totalSessions * 0.12), errorRate: 15.2, avgPerformance: 0.78, topIssue: 'Form validation' },
        { country: 'Germany', countryCode: 'DE', sessionCount: Math.round(totalSessions * 0.08), errorRate: 14.1, avgPerformance: 0.81 },
        { country: 'Canada', countryCode: 'CA', sessionCount: Math.round(totalSessions * 0.07), errorRate: 16.3, avgPerformance: 0.75 },
        { country: 'Australia', countryCode: 'AU', sessionCount: Math.round(totalSessions * 0.05), errorRate: 19.7, avgPerformance: 0.68, topIssue: 'High latency' },
        { country: 'Brazil', countryCode: 'BR', sessionCount: Math.round(totalSessions * 0.04), errorRate: 24.5, avgPerformance: 0.62, topIssue: 'Network timeouts' },
        { country: 'Japan', countryCode: 'JP', sessionCount: Math.round(totalSessions * 0.04), errorRate: 12.8, avgPerformance: 0.84 },
      ],
      browserBreakdown: [
        { browser: 'Chrome', version: '121', sessionCount: Math.round(totalSessions * 0.52), errorRate: 17.2, compatibilityIssues: 12, topIssue: 'WebGL crashes' },
        { browser: 'Safari', version: '17', sessionCount: Math.round(totalSessions * 0.28), errorRate: 19.8, compatibilityIssues: 24, topIssue: 'Webkit rendering bugs' },
        { browser: 'Firefox', version: '122', sessionCount: Math.round(totalSessions * 0.12), errorRate: 16.5, compatibilityIssues: 8 },
        { browser: 'Edge', version: '121', sessionCount: Math.round(totalSessions * 0.06), errorRate: 18.3, compatibilityIssues: 5 },
        { browser: 'Opera', sessionCount: Math.round(totalSessions * 0.02), errorRate: 21.2, compatibilityIssues: 3 },
      ],
      deviceTypeBreakdown: [
        { deviceType: 'mobile' as const, sessionCount: Math.round(totalSessions * 0.62), errorRate: 21.5, avgPerformance: 0.68 },
        { deviceType: 'desktop' as const, sessionCount: Math.round(totalSessions * 0.32), errorRate: 14.8, avgPerformance: 0.78 },
        { deviceType: 'tablet' as const, sessionCount: Math.round(totalSessions * 0.06), errorRate: 16.2, avgPerformance: 0.74 },
      ],
    };

    return {
      totalSessions,
      criticalInteractions,
      estimatedImpact,
      topIssueHotspots,
      topErrorPatterns,
      topJourneys,
      segmentation,
      performance,
      timePatterns,
      geographic,
      comparison,
      // Legacy (for backward compatibility)
      sessionsWithIssues,
      issueRate,
      cleanSessions,
      cleanRate,
      avgInteractionQuality: undefined, // Removed - not defensible
      qualityTrend: undefined, // Removed - use comparison.sessionsWithIssues.change
      issueBreakdown: {
        failedInteractions,
        errorsAndCrashes,
        frustrationSignals,
        dropOffs,
      },
    };
  }
}

// Export mock sessions for consistent data across the app
export const MOCK_SESSIONS_DATA = MockSessionReplayData.generateSessions(50);

// Mock Configuration Responses

export class MockConfigurationData {
  
  // Mock Filter Schema Response
  static getFilterSchema(platform: 'web' | 'ios' | 'android' = 'ios'): GetFilterSchemaResponse {
    if (platform === 'web') {
      return {
        platform: 'web',
        operatorLabels: {
          equals: 'equals',
          not_equals: 'does not equal',
          contains: 'contains',
          not_contains: 'does not contain',
          greater_than: 'greater than',
          less_than: 'less than',
          greater_than_or_equal: 'greater than or equal to',
          less_than_or_equal: 'less than or equal to',
          in: 'in',
          not_in: 'not in',
          exists: 'exists',
          not_exists: 'does not exist',
        },
        categories: [
          {
            key: 'ui_interaction',
            label: 'UI Interactions',
            description: 'Filter by user interactions',
            fields: [
              {
                key: 'interaction.type',
                label: 'Interaction Type',
                category: 'ui_interaction',
                type: 'enum',
                operators: ['equals', 'not_equals', 'in', 'not_in'],
                enumValues: [
                  { value: 'click', label: 'Click' },
                  { value: 'scroll', label: 'Scroll' },
                  { value: 'input', label: 'Text Input' },
                  { value: 'hover', label: 'Hover' },
                ],
                description: 'Type of user interaction',
              },
              {
                key: 'interaction.page',
                label: 'Page URL',
                category: 'ui_interaction',
                type: 'string',
                operators: ['equals', 'not_equals', 'contains', 'not_contains'],
                description: 'Page where interaction occurred',
              },
            ],
          },
          {
            key: 'rum',
            label: 'Performance Metrics',
            description: 'Web performance metrics',
            fields: [
              {
                key: 'rum.lcp',
                label: 'Largest Contentful Paint',
                category: 'rum',
                type: 'number',
                operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
                unit: 'ms',
                description: 'Largest contentful paint time',
              },
              {
                key: 'rum.fcp',
                label: 'First Contentful Paint',
                category: 'rum',
                type: 'number',
                operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
                unit: 'ms',
                description: 'First contentful paint time',
              },
              {
                key: 'rum.cls',
                label: 'Cumulative Layout Shift',
                category: 'rum',
                type: 'number',
                operators: ['greater_than', 'less_than'],
                description: 'Cumulative layout shift score',
              },
              {
                key: 'rum.page_load_time',
                label: 'Page Load Time',
                category: 'rum',
                type: 'number',
                operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
                unit: 'ms',
                description: 'Total page load time',
              },
            ],
          },
          {
            key: 'session_property',
            label: 'Session Properties',
            fields: [
              {
                key: 'session.duration',
                label: 'Session Duration',
                category: 'session_property',
                type: 'number',
                operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
                unit: 'ms',
                description: 'Total session duration',
              },
              {
                key: 'session.error_count',
                label: 'Error Count',
                category: 'session_property',
                type: 'number',
                operators: ['equals', 'greater_than', 'less_than'],
                description: 'Number of errors in session',
              },
              {
                key: 'session.page_count',
                label: 'Page Count',
                category: 'session_property',
                type: 'number',
                operators: ['equals', 'greater_than', 'less_than'],
                description: 'Number of pages viewed',
              },
            ],
          },
          {
            key: 'user_property',
            label: 'User Properties',
            fields: [
              {
                key: 'user.id',
                label: 'User ID',
                category: 'user_property',
                type: 'string',
                operators: ['equals', 'not_equals', 'contains', 'exists', 'not_exists'],
                description: 'User identifier',
              },
              {
                key: 'user.is_anonymous',
                label: 'Is Anonymous',
                category: 'user_property',
                type: 'boolean',
                operators: ['equals'],
                description: 'Whether user is anonymous',
              },
            ],
          },
        ],
      };
    }
    
    // iOS/Android (mobile)
    return {
      platform,
      operatorLabels: {
        equals: 'equals',
        not_equals: 'does not equal',
        contains: 'contains',
        not_contains: 'does not contain',
        greater_than: 'greater than',
        less_than: 'less than',
        greater_than_or_equal: 'greater than or equal to',
        less_than_or_equal: 'less than or equal to',
        in: 'in',
        not_in: 'not in',
        exists: 'exists',
        not_exists: 'does not exist',
      },
      categories: [
        {
          key: 'ui_interaction',
          label: 'UI Interactions',
          description: 'Filter by user interactions',
          fields: [
            {
              key: 'interaction.type',
              label: 'Interaction Type',
              category: 'ui_interaction',
              type: 'enum',
              operators: ['equals', 'not_equals', 'in', 'not_in'],
              enumValues: [
                { value: 'tap', label: 'Tap' },
                { value: 'swipe', label: 'Swipe' },
                { value: 'scroll', label: 'Scroll' },
                { value: 'long_press', label: 'Long Press' },
                { value: 'pinch', label: 'Pinch' },
              ],
              description: 'Type of user interaction',
            },
            {
              key: 'interaction.screen',
              label: 'Screen Name',
              category: 'ui_interaction',
              type: 'string',
              operators: ['equals', 'not_equals', 'contains', 'not_contains'],
              description: 'Screen where interaction occurred',
            },
            {
              key: 'interaction.element_id',
              label: 'Element ID',
              category: 'ui_interaction',
              type: 'string',
              operators: ['equals', 'not_equals', 'contains'],
              description: 'UI element identifier',
            },
          ],
        },
        {
          key: 'performance',
          label: 'Performance Metrics',
          description: 'Mobile performance metrics',
          fields: [
            {
              key: 'performance.app_start_time',
              label: 'App Start Time',
              category: 'performance',
              type: 'number',
              operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
              unit: 'ms',
              description: 'Cold start time',
            },
            {
              key: 'performance.screen_load_time',
              label: 'Screen Load Time',
              category: 'performance',
              type: 'number',
              operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
              unit: 'ms',
              description: 'Time to load screen',
            },
            {
              key: 'performance.frame_drop_rate',
              label: 'Frame Drop Rate',
              category: 'performance',
              type: 'number',
              operators: ['greater_than', 'less_than'],
              unit: '%',
              description: 'Percentage of dropped frames',
            },
            {
              key: 'performance.memory_usage',
              label: 'Memory Usage',
              category: 'performance',
              type: 'number',
              operators: ['greater_than', 'less_than'],
              unit: 'MB',
              description: 'Peak memory usage',
            },
          ],
        },
        {
          key: 'session_property',
          label: 'Session Properties',
          fields: [
            {
              key: 'session.duration',
              label: 'Session Duration',
              category: 'session_property',
              type: 'number',
              operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
              unit: 'ms',
              description: 'Total session duration',
            },
            {
              key: 'session.error_count',
              label: 'Error Count',
              category: 'session_property',
              type: 'number',
              operators: ['equals', 'greater_than', 'less_than'],
              description: 'Number of errors in session',
            },
            {
              key: 'session.screen_count',
              label: 'Screen Count',
              category: 'session_property',
              type: 'number',
              operators: ['equals', 'greater_than', 'less_than'],
              description: 'Number of screens viewed',
            },
          ],
        },
        {
          key: 'user_property',
          label: 'User Properties',
          fields: [
            {
              key: 'user.id',
              label: 'User ID',
              category: 'user_property',
              type: 'string',
              operators: ['equals', 'not_equals', 'contains', 'exists', 'not_exists'],
              description: 'User identifier',
            },
            {
              key: 'user.is_anonymous',
              label: 'Is Anonymous',
              category: 'user_property',
              type: 'boolean',
              operators: ['equals'],
              description: 'Whether user is anonymous',
            },
          ],
        },
        {
          key: 'device',
          label: 'Device',
          fields: [
            {
              key: 'device.model',
              label: 'Device Model',
              category: 'device',
              type: 'string',
              operators: ['equals', 'not_equals', 'contains'],
              description: 'Device model',
            },
            {
              key: 'device.os_version',
              label: 'OS Version',
              category: 'device',
              type: 'string',
              operators: ['equals', 'not_equals', 'contains'],
              description: 'Operating system version',
            },
          ],
        },
      ],
    };
  }

  // Mock Date Range Configuration
  static getDateRangeConfig(): GetDateRangeConfigResponse {
    return {
      options: [
        { value: '1h', label: 'Last hour' },
        { value: '1d', label: 'Last 24 hours' },
        { value: '7d', label: 'Last 7 days' },
        { value: '30d', label: 'Last 30 days' },
        { value: 'custom', label: 'Custom Range' },
      ],
      defaultValue: '7d',
      customRangeEnabled: true,
    };
  }

  // Mock Quick Filters
  static getQuickFilters(): GetQuickFiltersResponse {
    return {
      filters: [
        {
          id: 'failed_interactions',
          label: 'Failed Interactions',
          description: 'Sessions where interactions did not complete successfully',
          icon: 'alert-circle',
          filterCondition: {
            'interaction.failed': { equals: true },
          },
        },
        {
          id: 'errors_crashes',
          label: 'Errors & Crashes',
          description: 'Sessions with JavaScript errors or app crashes',
          icon: 'bug',
          filterCondition: {
            'session.error_count': { greater_than: 0 },
          },
        },
        {
          id: 'frustration_signals',
          label: 'Frustration Signals',
          description: 'Rage clicks, dead clicks, or repeated failed actions',
          icon: 'click',
          filterCondition: {
            OR: [
              { 'interaction.rage_click': { equals: true } },
              { 'interaction.dead_click': { equals: true } },
            ],
          },
        },
        {
          id: 'drop_offs',
          label: 'Drop-offs',
          description: 'Sessions that ended unexpectedly or without completion',
          icon: 'activity',
          filterCondition: {
            'session.outcome': { equals: 'drop_off' },
          },
        },
        {
          id: 'slow_performance',
          label: 'Slow Performance',
          description: 'Sessions with slow load times or poor interaction quality',
          icon: 'clock',
          filterCondition: {
            'interaction.quality': { less_than: 5 },
          },
        },
      ],
    };
  }
}
