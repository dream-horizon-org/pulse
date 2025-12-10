//
//  RCTNativePulseExampleModule.m
//  PulseReactNativeOtelExample
//
//  Created by Shubham Gupta on 09/12/25.
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

- (void)makeGetRequest:(NSString *)url
               resolve:(RCTPromiseResolveBlock)resolve
                reject:(RCTPromiseRejectBlock)reject
{
  NSURL *requestURL = [NSURL URLWithString:url];
  if (!requestURL) {
    reject(@"INVALID_URL", @"Invalid URL provided", nil);
    return;
  }

  NSURLSessionConfiguration *configuration = [NSURLSessionConfiguration defaultSessionConfiguration];
  NSURLSession *session = [NSURLSession sessionWithConfiguration:configuration];
  
  NSURLRequest *request = [NSURLRequest requestWithURL:requestURL];
  
  NSURLSessionDataTask *task = [session dataTaskWithRequest:request
                                            completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
    if (error) {
      reject(@"NETWORK_ERROR", error.localizedDescription, error);
      return;
    }
    
    NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
    if (!httpResponse) {
      reject(@"INVALID_RESPONSE", @"Invalid HTTP response", nil);
      return;
    }
    
    NSString *responseBody = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    if (!responseBody) {
      responseBody = @"";
    }
    
    NSMutableDictionary *headers = [NSMutableDictionary dictionary];
    [httpResponse.allHeaderFields enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
      NSString *headerName = [key isKindOfClass:[NSString class]] ? key : [key description];
      NSString *headerValue = [obj isKindOfClass:[NSString class]] ? obj : [obj description];
      
      // Handle multiple values for the same header (convert to array)
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
    
    NSDictionary *result = @{
      @"status": @(httpResponse.statusCode),
      @"body": responseBody,
      @"headers": headers
    };
    
    resolve(result);
  }];
  
  [task resume];
}

- (void)makePostRequest:(NSString *)url
                    body:(NSString *)body
                 resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject
{
  NSURL *requestURL = [NSURL URLWithString:url];
  if (!requestURL) {
    reject(@"INVALID_URL", @"Invalid URL provided", nil);
    return;
  }

  NSURLSessionConfiguration *configuration = [NSURLSessionConfiguration defaultSessionConfiguration];
  NSURLSession *session = [NSURLSession sessionWithConfiguration:configuration];
  
  NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:requestURL];
  [request setHTTPMethod:@"POST"];
  [request setValue:@"application/json; charset=utf-8" forHTTPHeaderField:@"Content-Type"];
  
  if (body) {
    NSData *bodyData = [body dataUsingEncoding:NSUTF8StringEncoding];
    [request setHTTPBody:bodyData];
  }
  
  NSURLSessionDataTask *task = [session dataTaskWithRequest:request
                                            completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
    if (error) {
      reject(@"NETWORK_ERROR", error.localizedDescription, error);
      return;
    }
    
    NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
    if (!httpResponse) {
      reject(@"INVALID_RESPONSE", @"Invalid HTTP response", nil);
      return;
    }
    
    NSString *responseBody = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    if (!responseBody) {
      responseBody = @"";
    }
    
    NSMutableDictionary *headers = [NSMutableDictionary dictionary];
    [httpResponse.allHeaderFields enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
      NSString *headerName = [key isKindOfClass:[NSString class]] ? key : [key description];
      NSString *headerValue = [obj isKindOfClass:[NSString class]] ? obj : [obj description];
      
      // Handle multiple values for the same header (convert to array)
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
    
    NSDictionary *result = @{
      @"status": @(httpResponse.statusCode),
      @"body": responseBody,
      @"headers": headers
    };
    
    resolve(result);
  }];
  
  [task resume];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativePulseExampleModuleSpecJSI>(params);
}

@end

