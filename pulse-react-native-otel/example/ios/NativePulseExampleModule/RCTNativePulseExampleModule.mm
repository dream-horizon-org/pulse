//
//  RCTNativePulseExampleModule.mm
//  New Arch: plain methods (no macros), TurboModule/spec calls them directly.
//  Old Arch: RCT_EXPORT_METHOD so bridge discovers methods. Both use shared implementation.
//

#import "RCTNativePulseExampleModule.h"
#import <React/RCTBridgeModule.h>
#import <React/RCTUtils.h>

@implementation RCTNativePulseExampleModule

RCT_EXPORT_MODULE(NativePulseExampleModule)

+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

#pragma mark - Shared implementation

static NSDictionary *resultDictionaryFromResponse(NSHTTPURLResponse *httpResponse, NSData *data)
{
  NSString *responseBody = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
  if (!responseBody) responseBody = @"";
  NSMutableDictionary *headers = [NSMutableDictionary dictionary];
  [httpResponse.allHeaderFields enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
    NSString *headerName = [key isKindOfClass:[NSString class]] ? key : [key description];
    NSString *headerValue = [obj isKindOfClass:[NSString class]] ? obj : [obj description];
    id existingValue = headers[headerName];
    if (existingValue) {
      if ([existingValue isKindOfClass:[NSArray class]]) {
        NSMutableArray *values = [existingValue mutableCopy];
        [values addObject:headerValue];
        headers[headerName] = values;
      } else {
        headers[headerName] = @[existingValue, headerValue];
      }
    } else {
      headers[headerName] = headerValue;
    }
  }];
  return @{ @"status": @(httpResponse.statusCode), @"body": responseBody, @"headers": headers };
}

- (void)doMakeGetRequest:(NSString *)url resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  NSURL *requestURL = [NSURL URLWithString:url];
  if (!requestURL) {
    reject(@"INVALID_URL", @"Invalid URL provided", nil);
    return;
  }
  NSURLSession *session = [NSURLSession sessionWithConfiguration:[NSURLSessionConfiguration defaultSessionConfiguration]];
  NSURLSessionDataTask *task = [session dataTaskWithRequest:[NSURLRequest requestWithURL:requestURL]
                                          completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
    if (error) { reject(@"NETWORK_ERROR", error.localizedDescription, error); return; }
    NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
    if (!httpResponse) { reject(@"INVALID_RESPONSE", @"Invalid HTTP response", nil); return; }
    resolve(resultDictionaryFromResponse(httpResponse, data));
  }];
  [task resume];
}

- (void)doMakePostRequest:(NSString *)url body:(NSString *)body resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  NSURL *requestURL = [NSURL URLWithString:url];
  if (!requestURL) {
    reject(@"INVALID_URL", @"Invalid URL provided", nil);
    return;
  }
  NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:requestURL];
  [request setHTTPMethod:@"POST"];
  [request setValue:@"application/json; charset=utf-8" forHTTPHeaderField:@"Content-Type"];
  if (body) [request setHTTPBody:[body dataUsingEncoding:NSUTF8StringEncoding]];
  NSURLSession *session = [NSURLSession sessionWithConfiguration:[NSURLSessionConfiguration defaultSessionConfiguration]];
  NSURLSessionDataTask *task = [session dataTaskWithRequest:request
                                          completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
    if (error) { reject(@"NETWORK_ERROR", error.localizedDescription, error); return; }
    NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
    if (!httpResponse) { reject(@"INVALID_RESPONSE", @"Invalid HTTP response", nil); return; }
    resolve(resultDictionaryFromResponse(httpResponse, data));
  }];
  [task resume];
}

#pragma mark - New Architecture (no macros)

#if RCT_NEW_ARCH_ENABLED

- (void)makeGetRequest:(NSString *)url resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [self doMakeGetRequest:url resolve:resolve reject:reject];
}

- (void)makePostRequest:(NSString *)url body:(NSString *)body resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [self doMakePostRequest:url body:body resolve:resolve reject:reject];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativePulseExampleModuleSpecJSI>(params);
}

#pragma mark - Old Architecture (RCT_EXPORT_METHOD)

#else

RCT_EXPORT_METHOD(makeGetRequest:(NSString *)url
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
  [self doMakeGetRequest:url resolve:resolve reject:reject];
}

RCT_EXPORT_METHOD(makePostRequest:(NSString *)url
                  body:(NSString *)body
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
  [self doMakePostRequest:url body:body resolve:resolve reject:reject];
}

#endif

@end
