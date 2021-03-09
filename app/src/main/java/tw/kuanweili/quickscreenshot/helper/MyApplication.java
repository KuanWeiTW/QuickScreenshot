package tw.kuanweili.quickscreenshot.helper;

import android.media.projection.MediaProjection;
import android.net.Uri;

public class MyApplication {
    public static MediaProjection MediaProjection;
    public static Uri ScreenshotUri;

    public static final String TAKE_SCREENSHOT_SIGNAL = "TAKE_SCREENSHOT";
    public static final String SCREENSHOT_DONE_SIGNAL = "SCREENSHOT_DONE";
    public static final String SAVE_SCREENSHOT_SIGNAL = "SAVE_SCREENSHOT";
    public static final String SCREENSHOT_AUTHORIZED_SIGNAL = "SCREENSHOT_AUTHORIZED";
    public static final String SCREENSHOT_AUTHORIZATION_CANCELED_SIGNAL = "SCREENSHOT_AUTHORIZATION_CANCELED";
}
