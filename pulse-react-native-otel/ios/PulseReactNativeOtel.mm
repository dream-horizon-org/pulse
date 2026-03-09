#import "PulseReactNativeOtel.h"

#if __has_include(<PulseReactNativeOtel/PulseReactNativeOtel-Swift.h>)
#import <PulseReactNativeOtel/PulseReactNativeOtel-Swift.h>
#elif __has_include("PulseReactNativeOtel-Swift.h")
#import "PulseReactNativeOtel-Swift.h"
#endif

@implementation PulseReactNativeOtel

RCT_EXPORT_MODULE()

+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

#pragma mark - Shared implementation (used by both arches)

- (NSNumber *)doIsInitialized
{
  return @([PulseSDK pulseIsInitialized]);
}

- (NSNumber *)doTrackEvent:(NSString *)event observedTimeMs:(double)observedTimeMs properties:(NSDictionary *)properties
{
  [PulseReactNativeOtelLogger trackEvent:event observedTimeMs:observedTimeMs properties:properties];
  return @YES;
}

- (NSString *)doStartSpan:(NSString *)name inheritContext:(BOOL)inheritContext attributes:(NSDictionary *)attributes
{
  return [PulseReactNativeOtelTracer startSpan:name inheritContext:inheritContext attributes:attributes];
}

- (NSNumber *)doEndSpan:(NSString *)spanId statusCode:(NSString *)statusCode
{
  [PulseReactNativeOtelTracer endSpan:spanId statusCode:statusCode];
  return @YES;
}

- (NSNumber *)doAddSpanEvent:(NSString *)spanId name:(NSString *)name attributes:(NSDictionary *)attributes
{
  [PulseReactNativeOtelTracer addEvent:spanId name:name attributes:attributes];
  return @YES;
}

- (NSNumber *)doSetSpanAttributes:(NSString *)spanId attributes:(NSDictionary *)attributes
{
  [PulseReactNativeOtelTracer setAttributes:spanId attributes:attributes];
  return @YES;
}

- (NSNumber *)doRecordSpanException:(NSString *)spanId errorMessage:(NSString *)errorMessage stackTrace:(NSString *)stackTrace
{
  [PulseReactNativeOtelTracer recordException:spanId errorMessage:errorMessage stackTrace:stackTrace];
  return @YES;
}

- (NSNumber *)doReportException:(NSString *)errorMessage observedTimeMs:(double)observedTimeMs stackTrace:(NSString *)stackTrace isFatal:(BOOL)isFatal errorType:(NSString *)errorType attributes:(NSDictionary *)attributes
{
  [PulseReactNativeOtelLogger reportException:errorMessage observedTimeMs:observedTimeMs stackTrace:stackTrace isFatal:isFatal errorType:errorType attributes:attributes];
  return @YES;
}

- (NSNumber *)doDiscardSpan:(NSString *)spanId
{
  [PulseReactNativeOtelTracer discardSpan:spanId];
  return @YES;
}

- (void)doSetUserId:(NSString *)id
{
  [PulseSDK pulseSetUserId:id];
}

- (void)doSetUserProperties:(NSDictionary *)properties
{
  NSDictionary<NSString *, PulseAttributeValue *> *converted = [AttributeValueConverter convertFromDictionary:properties];
  [PulseSDK pulseSetUserProperties:converted];
}

- (void)doSetUserProperty:(NSString *)name value:(NSString *)value
{
  PulseAttributeValue *attrValue = value ? [PulseAttributeValue attributeValueFromValue:value] : nil;
  [PulseSDK pulseSetUserProperty:name value:attrValue];
}

- (NSNumber *)doSetCurrentScreenName:(NSString *)screenName
{
  [ReactNativeScreenNameTracker setCurrentScreenName:screenName];
  return @YES;
}

- (NSNumber *)doShutdown
{
  return @([PulseSDK shutdown]);
}

- (NSDictionary *)doGetAllFeatures
{
  return [PulseSDK pulseGetAllFeatures];
}

#pragma mark - New Architecture (no macros; TurboModule/spec calls these directly)

#if RCT_NEW_ARCH_ENABLED

- (NSNumber *)isInitialized { return [self doIsInitialized]; }

- (NSNumber *)trackEvent:(NSString *)event observedTimeMs:(double)observedTimeMs properties:(NSDictionary *)properties
{ return [self doTrackEvent:event observedTimeMs:observedTimeMs properties:properties]; }

- (NSString *)startSpan:(NSString *)name inheritContext:(BOOL)inheritContext attributes:(NSDictionary *)attributes
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

- (void)triggerAnr { }

- (NSNumber *)shutdown { return [self doShutdown]; }

- (NSNumber *)setCurrentScreenName:(NSString *)screenName { return [self doSetCurrentScreenName:screenName]; }

- (NSDictionary *)getAllFeatures { return [self doGetAllFeatures]; }

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativePulseReactNativeOtelSpecJSI>(params);
}

#pragma mark - Old Architecture (RCT_EXPORT_* so bridge discovers methods)

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

RCT_EXPORT_METHOD(setUserId:(NSString *)id)
{ [self doSetUserId:id]; }

RCT_EXPORT_METHOD(setUserProperties:(NSDictionary *)properties)
{ [self doSetUserProperties:properties]; }

RCT_EXPORT_METHOD(setUserProperty:(NSString *)name value:(NSString *)value)
{ [self doSetUserProperty:name value:value]; }

RCT_EXPORT_METHOD(triggerAnr)
{ }

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(shutdown)
{ return [self doShutdown]; }

#endif

@end