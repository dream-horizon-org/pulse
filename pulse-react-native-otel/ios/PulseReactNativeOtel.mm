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

#pragma mark - Shared implementation

- (NSNumber *)doIsInitialized { return @NO; }

- (NSNumber *)doTrackEvent:(NSString *)event observedTimeMs:(double)observedTimeMs properties:(NSDictionary *)properties
{ return @NO; }

- (NSString *)doStartSpan:(NSString *)name inheritContext:(NSNumber *)inheritContext attributes:(NSDictionary *)attributes
{ return [NSString stringWithFormat:@"noop-ios-%@", [[NSUUID UUID] UUIDString]]; }

- (NSNumber *)doEndSpan:(NSString *)spanId statusCode:(NSString *)statusCode
{ return @NO; }

- (NSNumber *)doAddSpanEvent:(NSString *)spanId name:(NSString *)name attributes:(NSDictionary *)attributes
{ return @NO; }

- (NSNumber *)doSetSpanAttributes:(NSString *)spanId attributes:(NSDictionary *)attributes
{ return @NO; }

- (NSNumber *)doRecordSpanException:(NSString *)spanId errorMessage:(NSString *)errorMessage stackTrace:(NSString *)stackTrace
{ return @NO; }

- (NSNumber *)doReportException:(NSString *)errorMessage observedTimeMs:(double)observedTimeMs stackTrace:(NSString *)stackTrace isFatal:(BOOL)isFatal errorType:(NSString *)errorType attributes:(NSDictionary *)attributes
{ return @NO; }

- (NSNumber *)doDiscardSpan:(NSString *)spanId { return @NO; }

- (void)doSetUserId:(NSString *)id { }

- (void)doSetUserProperties:(NSDictionary *)properties { }

- (void)doSetUserProperty:(NSString *)name value:(NSString *)value { }

- (void)doTriggerAnr { }

- (NSNumber *)doSetCurrentScreenName:(NSString *)screenName { return @NO; }

- (NSDictionary *)doGetAllFeatures { return nil; }

- (NSNumber *)doShutdown { return @YES; }

#pragma mark - New Architecture

#if RCT_NEW_ARCH_ENABLED

- (NSNumber *)isInitialized { return [self doIsInitialized]; }

- (NSNumber *)trackEvent:(NSString *)event observedTimeMs:(double)observedTimeMs properties:(NSDictionary *)properties
{ return [self doTrackEvent:event observedTimeMs:observedTimeMs properties:properties]; }

- (NSString *)startSpan:(NSString *)name inheritContext:(NSNumber *)inheritContext attributes:(NSDictionary *)attributes
{ return [self doStartSpan:name inheritContext:inheritContext attributes:attributes]; }

- (NSNumber *)endSpan:(NSString *)spanId statusCode:(NSString *)statusCode
{ return [self doEndSpan:spanId statusCode:statusCode]; }

- (NSNumber *)addSpanEvent:(NSString *)spanId name:(NSString *)name attributes:(NSDictionary *)attributes
{ return [self doAddSpanEvent:spanId name:name attributes:attributes]; }

- (NSNumber *)setSpanAttributes:(NSString *)spanId attributes:(NSDictionary *)attributes
{ return [self doSetSpanAttributes:spanId attributes:attributes]; }

- (NSNumber *)recordSpanException:(NSString *)spanId errorMessage:(NSString *)errorMessage stackTrace:(NSString *)stackTrace
{ return [self doRecordSpanException:spanId errorMessage:errorMessage stackTrace:stackTrace]; }

- (NSNumber *)reportException:(NSString *)errorMessage observedTimeMs:(double)observedTimeMs stackTrace:(NSString *)stackTrace isFatal:(BOOL)isFatal errorType:(NSString *)errorType attributes:(NSDictionary *)attributes
{ return [self doReportException:errorMessage observedTimeMs:observedTimeMs stackTrace:stackTrace isFatal:isFatal errorType:errorType attributes:attributes]; }

- (NSNumber *)discardSpan:(NSString *)spanId { return [self doDiscardSpan:spanId]; }

- (void)setUserId:(NSString *)id { [self doSetUserId:id]; }

- (void)setUserProperties:(NSDictionary *)properties { [self doSetUserProperties:properties]; }

- (void)setUserProperty:(NSString *)name value:(NSString *)value { [self doSetUserProperty:name value:value]; }

- (void)triggerAnr { [self doTriggerAnr]; }

- (NSNumber *)setCurrentScreenName:(NSString *)screenName { return [self doSetCurrentScreenName:screenName]; }

- (NSDictionary *)getAllFeatures { return [self doGetAllFeatures]; }

- (NSNumber *)shutdown { return [self doShutdown]; }

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativePulseReactNativeOtelSpecJSI>(params);
}

#pragma mark - Old Architecture

#else

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(isInitialized)
{ return [self doIsInitialized]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(trackEvent:(NSString *)event observedTimeMs:(double)observedTimeMs properties:(NSDictionary *)properties)
{ return [self doTrackEvent:event observedTimeMs:observedTimeMs properties:properties]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(startSpan:(NSString *)name inheritContext:(NSNumber *)inheritContext attributes:(NSDictionary *)attributes)
{ return [self doStartSpan:name inheritContext:inheritContext attributes:attributes]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(endSpan:(NSString *)spanId statusCode:(NSString *)statusCode)
{ return [self doEndSpan:spanId statusCode:statusCode]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(addSpanEvent:(NSString *)spanId name:(NSString *)name attributes:(NSDictionary *)attributes)
{ return [self doAddSpanEvent:spanId name:name attributes:attributes]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(setSpanAttributes:(NSString *)spanId attributes:(NSDictionary *)attributes)
{ return [self doSetSpanAttributes:spanId attributes:attributes]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(recordSpanException:(NSString *)spanId errorMessage:(NSString *)errorMessage stackTrace:(NSString *)stackTrace)
{ return [self doRecordSpanException:spanId errorMessage:errorMessage stackTrace:stackTrace]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(reportException:(NSString *)errorMessage observedTimeMs:(double)observedTimeMs stackTrace:(NSString *)stackTrace isFatal:(BOOL)isFatal errorType:(NSString *)errorType attributes:(NSDictionary *)attributes)
{ return [self doReportException:errorMessage observedTimeMs:observedTimeMs stackTrace:stackTrace isFatal:isFatal errorType:errorType attributes:attributes]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(discardSpan:(NSString *)spanId)
{ return [self doDiscardSpan:spanId]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(setCurrentScreenName:(NSString *)screenName)
{ return [self doSetCurrentScreenName:screenName]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getAllFeatures)
{ return [self doGetAllFeatures]; }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(shutdown)
{ return [self doShutdown]; }

RCT_EXPORT_METHOD(setUserId:(NSString *)id)
{ [self doSetUserId:id]; }

RCT_EXPORT_METHOD(setUserProperties:(NSDictionary *)properties)
{ [self doSetUserProperties:properties]; }

RCT_EXPORT_METHOD(setUserProperty:(NSString *)name value:(NSString *)value)
{ [self doSetUserProperty:name value:value]; }

RCT_EXPORT_METHOD(triggerAnr)
{ [self doTriggerAnr]; }

#endif

@end
