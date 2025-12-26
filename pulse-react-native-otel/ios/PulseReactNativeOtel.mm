#import "PulseReactNativeOtel.h"

@implementation PulseReactNativeOtel

RCT_EXPORT_MODULE()

+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

- (instancetype)init
{
  self = [super init];
  if (self) {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
      NSLog(@"[Pulse] iOS support coming soon. All operations will be no-ops.");
    });
  }
  return self;
}

// Check if native SDK is initialized (always false on iOS)
- (NSNumber *)isInitialized
{
  return @NO;
}

// Track event (no-op)
- (NSNumber *)trackEvent:(NSString *)event
          observedTimeMs:(double)observedTimeMs
              properties:(NSDictionary *)properties
{
  return @NO;
}

- (NSString *)startSpan:(NSString *)name
             attributes:(NSDictionary *)attributes
         inheritContext:(NSNumber *)inheritContext
{
  return [NSString stringWithFormat:@"noop-ios-%@", [[NSUUID UUID] UUIDString]];
}

// End span (no-op)
- (NSNumber *)endSpan:(NSString *)spanId
{
  return @NO;
}

// Add span event (no-op)
- (NSNumber *)addSpanEvent:(NSString *)spanId
                name:(NSString *)name
          attributes:(NSDictionary *)attributes
{
  return @NO;
}

// Set span attributes (no-op)
- (NSNumber *)setSpanAttributes:(NSString *)spanId
               attributes:(NSDictionary *)attributes
{
  return @NO;
}

// Record span exception (no-op)
- (NSNumber *)recordSpanException:(NSString *)spanId
               errorMessage:(NSString *)errorMessage
                 stackTrace:(NSString *)stackTrace
{
  return @NO;
}

// Discard span (no-op on iOS)
- (NSNumber *)discardSpan:(NSString *)spanId
{
  return @NO;
}

- (nonnull NSNumber *)reportException:(nonnull NSString *)errorMessage observedTimeMs:(double)observedTimeMs stackTrace:(nonnull NSString *)stackTrace isFatal:(BOOL)isFatal errorType:(nonnull NSString *)errorType attributes:(nonnull NSDictionary *)attributes { 
  return @NO;
}


- (void)setUserId:(NSString * _Nullable)id { 
  // No op
}


- (void)setUserProperties:(nonnull NSDictionary *)properties { 
  // No op
}


- (void)setUserProperty:(nonnull NSString *)name value:(NSString * _Nullable)value { 
  // No op
}


- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativePulseReactNativeOtelSpecJSI>(params);
}

@end
