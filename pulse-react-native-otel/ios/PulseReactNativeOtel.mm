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

- (NSNumber *)isInitialized
{
  return @([PulseSDK isSDKInitialized]);
}

- (NSNumber *)trackEvent:(NSString *)event
          observedTimeMs:(double)observedTimeMs
              properties:(NSDictionary *)properties
{
  [PulseReactNativeOtelLogger trackEvent:event
                        observedTimeMs:observedTimeMs
                            properties:properties];
  return @YES;
}

- (NSString *)startSpan:(NSString *)name
             attributes:(NSDictionary *)attributes
{
  return [PulseReactNativeOtelTracer startSpan:name attributes:attributes];
}

- (NSNumber *)endSpan:(NSString *)spanId
           statusCode:(NSString *)statusCode
{
  [PulseReactNativeOtelTracer endSpan:spanId statusCode:statusCode];
  return @YES;
}

- (NSNumber *)addSpanEvent:(NSString *)spanId
                name:(NSString *)name
          attributes:(NSDictionary *)attributes
{
  [PulseReactNativeOtelTracer addEvent:spanId name:name attributes:attributes];
  return @YES;
}

- (NSNumber *)setSpanAttributes:(NSString *)spanId
               attributes:(NSDictionary *)attributes
{
  [PulseReactNativeOtelTracer setAttributes:spanId attributes:attributes];
  return @YES;
}

- (NSNumber *)recordSpanException:(NSString *)spanId
               errorMessage:(NSString *)errorMessage
                 stackTrace:(NSString *)stackTrace
{
  [PulseReactNativeOtelTracer recordException:spanId errorMessage:errorMessage stackTrace:stackTrace];
  return @YES;
}

- (nonnull NSNumber *)reportException:(nonnull NSString *)errorMessage 
                        observedTimeMs:(double)observedTimeMs 
                            stackTrace:(nonnull NSString *)stackTrace 
                              isFatal:(BOOL)isFatal 
                            errorType:(nonnull NSString *)errorType 
                           attributes:(nonnull NSDictionary *)attributes
{ 
  [PulseReactNativeOtelLogger reportException:errorMessage
                                observedTimeMs:observedTimeMs
                                    stackTrace:stackTrace
                                      isFatal:isFatal
                                    errorType:errorType
                                   attributes:attributes];
  return @YES;
}

- (void)setUserId:(NSString * _Nullable)id { 
  [PulseSDK setUserId:id];
}

- (void)setUserProperties:(nonnull NSDictionary *)properties { 
  NSDictionary<NSString *, PulseAttributeValue *> *convertedProperties = [AttributeValueConverter convertFromDictionary:properties];
  [PulseSDK setUserProperties:convertedProperties];
}

- (void)setUserProperty:(nonnull NSString *)name value:(NSString * _Nullable)value { 
  PulseAttributeValue *attrValue = value ? [PulseAttributeValue attributeValueFromValue:value] : nil;
  [PulseSDK setUserProperty:name value:attrValue];
}

- (void)triggerAnr {
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativePulseReactNativeOtelSpecJSI>(params);
}

@end
