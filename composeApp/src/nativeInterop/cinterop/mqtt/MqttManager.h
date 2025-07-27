#import <Foundation/Foundation.h>

@interface MqttManager : NSObject
+ (instancetype)shared;
- (void)connect;
- (void)disconnect;
- (void)publishWithTopic:(NSString*)topic message:(NSString*)message;
- (BOOL)isConnectedStatus;
@end
