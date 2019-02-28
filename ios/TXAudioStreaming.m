#import "TXAudioStreaming.h"
#import <AVFoundation/AVFoundation.h>
#import <MediaPlayer/MediaPlayer.h>

@interface TXAudioStreaming()

@property (nonatomic, strong) AVPlayer *player;
@property (nonatomic, strong) NSString *playerURL;
@property (nonatomic, strong) NSString *playerName;
@property (nonatomic) RCTResponseSenderBlock callback;

@end

@implementation TXAudioStreaming

RCT_EXPORT_MODULE()

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

- (NSArray<NSString *> *)supportedEvents
{
    return @[@"PlayerEvent"];
}

RCT_EXPORT_METHOD(play:(NSString *)url
                  playerName:(NSString *)name
                  callback:(RCTResponseSenderBlock)callback)
{
    self.playerURL = url;
    self.playerName = name;
    self.callback = callback;
    
    if(self.player != nil) {
        AVPlayerItem *item = self.player.currentItem;
        [item removeObserver:self forKeyPath:@"status"];
        [self.player pause];
        self.player = nil;
        
        [AVAudioSession.sharedInstance setActive:NO error:nil];
    }
    
    NSError *error;
    [AVAudioSession.sharedInstance setCategory:AVAudioSessionCategoryPlayback error:&error];
    [AVAudioSession.sharedInstance setActive:YES error:&error];
    
    if(error != nil) {
        self.callback(@[error.localizedDescription]);
        return;
    }
    
    self.player = [AVPlayer playerWithURL:[NSURL URLWithString:url]];
    AVPlayerItem *item = self.player.currentItem;
    [item addObserver:self forKeyPath:@"status" options:NSKeyValueObservingOptionNew context:nil];
    [self.player play];
}

RCT_EXPORT_METHOD(stop)
{
    if(self.player != nil) {
        AVPlayerItem *item = self.player.currentItem;
        [item removeObserver:self forKeyPath:@"status"];
        [self.player pause];
        self.player = nil;
        
        [self unregisterRemoteControlEvents];
        
        [self sendEventWithName:@"PlayerEvent" body:@{@"PlayerName": [NSNull null]}];
        [AVAudioSession.sharedInstance setActive:NO error:nil];
    }
}

RCT_EXPORT_METHOD(currentPlayerName:(RCTResponseSenderBlock)callback)
{
    if(self.player != nil) {
        callback(@[self.playerName]);
    } else {
        callback(@[[NSNull null]]);
    }
}

- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary *)change
                       context:(void *)context
{
    if ([keyPath isEqualToString:@"status"] && self.player != nil) {
        AVPlayerItem *item = self.player.currentItem;
        if(item.status == AVPlayerStatusReadyToPlay) {
            
            MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
            [commandCenter.playCommand addTarget:self action:@selector(didReceivePlayCommand:)];
            [commandCenter.pauseCommand addTarget:self action:@selector(didReceivePauseCommand:)];
            commandCenter.playCommand.enabled = YES;
            commandCenter.pauseCommand.enabled = YES;
            commandCenter.stopCommand.enabled = NO;
            commandCenter.nextTrackCommand.enabled = NO;
            commandCenter.previousTrackCommand.enabled = NO;
            
            NSDictionary *nowPlayingInfo = @{
                                             MPMediaItemPropertyArtist: self.playerName
                                             };
            MPNowPlayingInfoCenter.defaultCenter.nowPlayingInfo = nowPlayingInfo;
            
            self.callback(@[[NSNull null]]);
            [self sendEventWithName:@"PlayerEvent" body:@{@"PlayerName": self.playerName}];
        } else if (item.status == AVPlayerStatusFailed) {
            [item removeObserver:self forKeyPath:@"status"];
            self.player = nil;
            self.callback(@[@"Error al reproducir."]);
        }
    } else {
        [super observeValueForKeyPath:keyPath ofObject:object change:change context:context];
    }
}

- (MPRemoteCommandHandlerStatus)didReceivePlayCommand:(MPRemoteCommand *)event
{
    if(self.player == nil) {
        NSError *error;
        [AVAudioSession.sharedInstance setCategory:AVAudioSessionCategoryPlayback error:&error];
        [AVAudioSession.sharedInstance setActive:YES error:&error];
        
        if(error != nil) {
            return MPRemoteCommandHandlerStatusCommandFailed;
        }
        
        self.player = [AVPlayer playerWithURL:[NSURL URLWithString:self.playerURL]];
        AVPlayerItem *item = self.player.currentItem;
        [item addObserver:self forKeyPath:@"status" options:NSKeyValueObservingOptionNew context:nil];
        [self.player play];
        [self sendEventWithName:@"PlayerEvent" body:@{@"PlayerName": self.playerName}];
        
        return MPRemoteCommandHandlerStatusSuccess;
    } else {
        return MPRemoteCommandHandlerStatusCommandFailed;
    }
}

- (MPRemoteCommandHandlerStatus)didReceivePauseCommand:(MPRemoteCommand *)event
{
    if(self.player != nil) {
        AVPlayerItem *item = self.player.currentItem;
        [item removeObserver:self forKeyPath:@"status"];
        [self.player pause];
        self.player = nil;
        
        [AVAudioSession.sharedInstance setActive:NO error:nil];
        
        [self sendEventWithName:@"PlayerEvent" body:@{@"PlayerName": [NSNull null]}];
        
        return MPRemoteCommandHandlerStatusSuccess;
    } else {
        return MPRemoteCommandHandlerStatusCommandFailed;
    }
}

- (void)unregisterRemoteControlEvents
{
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
    [commandCenter.playCommand removeTarget:self];
    [commandCenter.pauseCommand removeTarget:self];
}

@end
