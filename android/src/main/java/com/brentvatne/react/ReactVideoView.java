package com.brentvatne.react;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.yqritc.scalablevideoview.ScalableType;
import com.yqritc.scalablevideoview.ScalableVideoView;

import java.util.HashMap;
import java.util.Map;


public class ReactVideoView extends ScalableVideoView implements MediaPlayer.OnPreparedListener, MediaPlayer
        .OnErrorListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnInfoListener, LifecycleEventListener {

    public enum Events {
        EVENT_LOAD_START("onVideoLoadStart"),
        EVENT_LOAD("onVideoLoad"),
        EVENT_ERROR("onVideoError"),
        EVENT_PROGRESS("onVideoProgress"),
        EVENT_SEEK("onVideoSeek"),
        EVENT_END("onVideoEnd"),
        EVENT_STALLED("onPlaybackStalled"),
        EVENT_RESUME("onPlaybackResume"),
        EVENT_READY_FOR_DISPLAY("onReadyForDisplay");

        private final String mName;

        Events(final String name) {
            mName = name;
        }

        @Override
        public String toString() {
            return mName;
        }
    }

    public static final String EVENT_PROP_FAST_FORWARD = "canPlayFastForward";
    public static final String EVENT_PROP_SLOW_FORWARD = "canPlaySlowForward";
    public static final String EVENT_PROP_SLOW_REVERSE = "canPlaySlowReverse";
    public static final String EVENT_PROP_REVERSE = "canPlayReverse";
    public static final String EVENT_PROP_STEP_FORWARD = "canStepForward";
    public static final String EVENT_PROP_STEP_BACKWARD = "canStepBackward";

    public static final String EVENT_PROP_DURATION = "duration";
    public static final String EVENT_PROP_PLAYABLE_DURATION = "playableDuration";
    public static final String EVENT_PROP_CURRENT_TIME = "currentTime";
    public static final String EVENT_PROP_BUFFER_PERCENT = "bufferPercent";
    public static final String EVENT_PROP_SEEK_TIME = "seekTime";
    public static final String EVENT_PROP_NATURALSIZE = "naturalSize";
    public static final String EVENT_PROP_WIDTH = "width";
    public static final String EVENT_PROP_HEIGHT = "height";
    public static final String EVENT_PROP_ORIENTATION = "orientation";

    public static final String EVENT_PROP_ERROR = "error";
    public static final String EVENT_PROP_WHAT = "what";
    public static final String EVENT_PROP_EXTRA = "extra";

    private ThemedReactContext mThemedReactContext;
    private Activity _activity;
    private RCTEventEmitter mEventEmitter;

    private Handler mProgressUpdateHandler = new Handler();
    private Runnable mProgressUpdateRunnable = null;

    private String mSrcUriString = null;
    private String mSrcType = "mp4";
    private boolean mSrcIsNetwork = false;
    private boolean mSrcIsAsset = false;
    private ScalableType mResizeMode = ScalableType.LEFT_TOP;
    private boolean mRepeat = false;
    private boolean mPaused = false;
    private boolean mMuted = false;
    private float mVolume = 1.0f;
    private float mRate = 1.0f;
    private boolean mPlayInBackground = false;

    private boolean mMediaPlayerValid = false; // True if mMediaPlayer is in prepared, started, or paused state.
    private int mVideoDuration = 0;
    private int mVideoBufferedPercent = 0;
    private boolean isCompleted = false;

    private boolean mVideoValid = true;
    public ReactVideoView(ThemedReactContext themedReactContext,Activity activity) {
        super(themedReactContext);
        _activity = activity;
        mThemedReactContext = themedReactContext;
        mEventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);
        themedReactContext.addLifecycleEventListener(this);


        initializeMediaPlayerIfNeeded();
        setSurfaceTextureListener(this);

        mProgressUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mVideoValid && !isCompleted) {
                    mProgressUpdateHandler.removeCallbacks(mProgressUpdateRunnable);
                    return;
                }

                if (mMediaPlayerValid) {
                    mVideoDuration = mMediaPlayer.getDuration();
                    WritableMap event = Arguments.createMap();
                    event.putDouble(EVENT_PROP_CURRENT_TIME, mMediaPlayer.getCurrentPosition() / 1000.0);
                    event.putDouble(EVENT_PROP_BUFFER_PERCENT, mVideoBufferedPercent);
                    event.putDouble(EVENT_PROP_PLAYABLE_DURATION, mVideoDuration / 1000.0);
                    mEventEmitter.receiveEvent(getId(), Events.EVENT_PROGRESS.toString(), event);
                }
                mProgressUpdateHandler.postDelayed(mProgressUpdateRunnable, 1000);
            }
        };
        mProgressUpdateHandler.post(mProgressUpdateRunnable);
    }

    private void initializeMediaPlayerIfNeeded() {
        if (mMediaPlayer == null) {
            mMediaPlayerValid = false;
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.setOnVideoSizeChangedListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnBufferingUpdateListener(this);
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setOnInfoListener(this);

        }
    }

    public void setSrc(final String uriString, final String type, final boolean isNetwork, final boolean isAsset) {

        mSrcUriString = uriString;
        mSrcType = type;
        mSrcIsNetwork = isNetwork;

        mMediaPlayerValid = false;
        mVideoDuration = 0;
        mVideoBufferedPercent = 0;

        class SetSrc implements Runnable {
            ReactVideoView mReactVideoView;
            SetSrc(ReactVideoView reactVideoView){mReactVideoView = reactVideoView;}
            @Override
            public void run() {
                initializeMediaPlayerIfNeeded();

                try {
                    mMediaPlayer.reset();
            if (isNetwork || isAsset) {
		                if (uriString.startsWith("content://")) {
		                    Uri parsedUrl = Uri.parse(uriString);
		                    setDataSource(mThemedReactContext, parsedUrl);
		                } else {
                        	setDataSource(uriString);
						}
                    } else {
                        setRawData(mThemedReactContext.getResources().getIdentifier(
                                uriString,
                                "raw",
                                mThemedReactContext.getPackageName()
                        ));
                    }

                    prepareAsync(mReactVideoView);

                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }



            }
        }

        new Thread(new SetSrc(this)).start();
        WritableMap src = Arguments.createMap();
        src.putString(ReactVideoViewManager.PROP_SRC_URI, uriString);
        src.putString(ReactVideoViewManager.PROP_SRC_TYPE, type);
        src.putBoolean(ReactVideoViewManager.PROP_SRC_IS_NETWORK, isNetwork);
        WritableMap event = Arguments.createMap();
        event.putMap(ReactVideoViewManager.PROP_SRC, src);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_LOAD_START.toString(), event);
        _activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);



    }

    public void setResizeModeModifier(final ScalableType resizeMode) {
        mResizeMode = resizeMode;

        if (mMediaPlayerValid) {
            setScalableType(resizeMode);
            invalidate();
        }
    }

    public void setRepeatModifier(final boolean repeat) {

        mRepeat = repeat;

        if (mMediaPlayerValid) {
            setLooping(repeat);
        }
    }

    public void setPausedModifier(final boolean paused) {

        mPaused = paused;

        if (!mMediaPlayerValid) {
            return;
        }
        class PauseVideo implements Runnable {
            @Override
            public void run() {
                if (mPaused) {
                    mMediaPlayer.pause();
                }
                else {
                    mMediaPlayer.start();
                }
            }
        }


        if (mPaused) {
            _activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        else{
            _activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        new Thread(new PauseVideo()).start();
    }

    public void setMutedModifier(final boolean muted) {
        mMuted = muted;

        if (!mMediaPlayerValid) {
            return;
        }

        if (mMuted) {
            setVolume(0, 0);
        } else {
            setVolume(mVolume, mVolume);
        }
    }

    public void setVolumeModifier(final float volume) {
        mVolume = volume;
        setMutedModifier(mMuted);
    }

    public void setRateModifier(final float rate) {
        mRate = rate;

        if (mMediaPlayerValid) {
            // TODO: Implement this.
            Log.e(ReactVideoViewManager.REACT_CLASS, "Setting playback rate is not yet supported on Android");
        }
    }

    public void applyModifiers() {
        setResizeModeModifier(mResizeMode);
        setRepeatModifier(mRepeat);
        setPausedModifier(mPaused);
        setMutedModifier(mMuted);
//        setRateModifier(mRate);


    }

    public void setPlayInBackground(final boolean playInBackground) {

        mPlayInBackground = playInBackground;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {

        mMediaPlayerValid = true;
        mVideoDuration = mp.getDuration();

        WritableMap naturalSize = Arguments.createMap();
        naturalSize.putInt(EVENT_PROP_WIDTH, mp.getVideoWidth());
        naturalSize.putInt(EVENT_PROP_HEIGHT, mp.getVideoHeight());
        if (mp.getVideoWidth() > mp.getVideoHeight())
            naturalSize.putString(EVENT_PROP_ORIENTATION, "landscape");
        else
            naturalSize.putString(EVENT_PROP_ORIENTATION, "portrait");

        WritableMap event = Arguments.createMap();
        event.putDouble(EVENT_PROP_DURATION, mVideoDuration / 1000.0);
        event.putDouble(EVENT_PROP_CURRENT_TIME, mp.getCurrentPosition() / 1000.0);
        event.putMap(EVENT_PROP_NATURALSIZE, naturalSize);
        // TODO: Actually check if you can.
        event.putBoolean(EVENT_PROP_FAST_FORWARD, true);
        event.putBoolean(EVENT_PROP_SLOW_FORWARD, true);
        event.putBoolean(EVENT_PROP_SLOW_REVERSE, true);
        event.putBoolean(EVENT_PROP_REVERSE, true);
        event.putBoolean(EVENT_PROP_FAST_FORWARD, true);
        event.putBoolean(EVENT_PROP_STEP_BACKWARD, true);
        event.putBoolean(EVENT_PROP_STEP_FORWARD, true);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_LOAD.toString(), event);

        _activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        applyModifiers();
        setResizeModeModifier(mResizeMode);
        class StartVideo implements Runnable {
            @Override
            public void run() {
                mMediaPlayer.start();
            }
        }
        new Thread(new StartVideo()).start();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {

        WritableMap error = Arguments.createMap();
        error.putInt(EVENT_PROP_WHAT, what);
        error.putInt(EVENT_PROP_EXTRA, extra);
        WritableMap event = Arguments.createMap();
        event.putMap(EVENT_PROP_ERROR, error);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_ERROR.toString(), event);
        return true;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_STALLED.toString(), Arguments.createMap());
                break;
            case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_RESUME.toString(), Arguments.createMap());
                break;
            case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                mEventEmitter.receiveEvent(getId(), Events.EVENT_READY_FOR_DISPLAY.toString(), Arguments.createMap());
                break;

            default:
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        mVideoBufferedPercent =  Math.round( ( percent) );
    }

    @Override
    public void seekTo(int msec) {

        if (mMediaPlayerValid) {
            WritableMap event = Arguments.createMap();
            event.putDouble(EVENT_PROP_CURRENT_TIME, getCurrentPosition() / 1000.0);
            event.putDouble(EVENT_PROP_SEEK_TIME, msec / 1000.0);
            mEventEmitter.receiveEvent(getId(), Events.EVENT_SEEK.toString(), event);

            super.seekTo(msec);
            if (isCompleted && mVideoDuration != 0 && msec < mVideoDuration) {
                isCompleted = false;
            }
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
//        mMediaPlayerValid = false;
        isCompleted = true;
        mEventEmitter.receiveEvent(getId(), Events.EVENT_END.toString(), null);
        _activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDetachedFromWindow() {
        mVideoValid = false;
        mMediaPlayerValid = false;
        _activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);



        class Release implements Runnable {
            ReactVideoView mReactVideoView;
            Release(ReactVideoView reactVideoView){mReactVideoView = reactVideoView;}
            @Override
            public void run() {
                try {
                    mMediaPlayer.release();
                    mMediaPlayer = null;
                }
                catch(Exception ex){
                    Log.e("ReactVideoView","onDetachedFromWindow err " + ex);
                    ex.printStackTrace();
                }

            }
        }

        new Thread(new Release(this)).start();

        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        try {
            super.onAttachedToWindow();
            setSrc(mSrcUriString, mSrcType, mSrcIsNetwork, mSrcIsAsset);
        }
        catch(Exception ex){
            Log.e("ReactVideoView","onAttachedToWindow err");
        }

    }

    @Override
    public void onHostPause() {

//        if (mMediaPlayer != null && !mPlayInBackground) {
//            mMediaPlayer.pause();
//        }

        class Release implements Runnable {
            @Override
            public void run() {
                try {
                    mMediaPlayerValid = false;
                    mMediaPlayer.release();
                }
                catch(Exception ex){
                    Log.e("ReactVideoView","onHostPause err");
                }

            }
        }

        new Thread(new Release()).start();


    }

    @Override
    public void onHostResume() {
    }

    @Override
    public void onHostDestroy() {
    }
}
