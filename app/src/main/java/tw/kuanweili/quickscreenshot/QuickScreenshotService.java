package tw.kuanweili.quickscreenshot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.app.NotificationCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;

import tw.kuanweili.quickscreenshot.helper.ImageHelper;
import tw.kuanweili.quickscreenshot.helper.MyApplication;


public class QuickScreenshotService extends AccessibilityService {
    private static final String NOTIFICATION_CHANNEL_ID = "tw.kuanwei.longscreenshot";
    private static final String CHANNEL_NAME = "Long Screenshot";

    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;

    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;

    private BroadcastReceiver mBroadcastReceiver;
    private View mFloatingPage;

    private Point mDisplaySize;

    private Bitmap mLatestScreenshot;
    private ArrayList<Bitmap> mScreenshotList;

    @Override
    protected void onServiceConnected() {
        //Get System Window Service
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mScreenshotList = new ArrayList<>();

        //Start Foreground Service
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, InvisibleActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText(CHANNEL_NAME)
                .setSmallIcon(R.drawable.ic_screenshot_black_32dp)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);

        //Start Invisible Activity
        startInvisibleActivityWithAction("Init");

        IntentFilter filter = new IntentFilter();
        filter.addAction(MyApplication.SCREENSHOT_AUTHORIZED_SIGNAL);
        filter.addAction(MyApplication.SCREENSHOT_AUTHORIZATION_CANCELED_SIGNAL);
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case MyApplication.SCREENSHOT_AUTHORIZED_SIGNAL:
                        startRecording();
                        new Handler(Looper.getMainLooper()).postDelayed(
                                (Runnable) () -> {
                                    takeScreenshot();
                                    createFloatingWindow();
                                }, 1000
                        );
                        break;
                    case MyApplication.SCREENSHOT_AUTHORIZATION_CANCELED_SIGNAL:
                        disableSelf();
                        break;
                }
            }
        };
        registerReceiver(mBroadcastReceiver, filter);
    }

    private void startRecording() {
        if (MyApplication.MediaProjection != null) {
            Display display = mWindowManager.getDefaultDisplay();
            mDisplaySize = new Point();
            display.getRealSize(mDisplaySize);
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int mScreenDensity = metrics.densityDpi;

            ImageReader mImageReader = ImageReader.newInstance(mDisplaySize.x, mDisplaySize.y, PixelFormat.RGBA_8888, 2);
            MyApplication.MediaProjection.createVirtualDisplay(getResources().getString(R.string.app_name), mDisplaySize.x, mDisplaySize.y, mScreenDensity,
                    VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, null);
            mImageReader.setOnImageAvailableListener((ImageReader.OnImageAvailableListener) this::onImageAvailable, null);

        }
    }

    private void onImageAvailable(ImageReader imageReader) {
        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            return;
        }

        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * mDisplaySize.x;
        Bitmap bitmap = Bitmap.createBitmap(mDisplaySize.x + rowPadding / pixelStride, mDisplaySize.y, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        mLatestScreenshot = bitmap;

        image.close();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
        stopForeground(true);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {

    }

    private void createFloatingWindow() {
        if (Settings.canDrawOverlays(this)) {
            // Get System Window Service

            mLayoutParams = new WindowManager.LayoutParams();
            mLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;//TYPE TOAST
            mLayoutParams.format = PixelFormat.RGBA_8888;
            mLayoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;
            mLayoutParams.width = mDisplaySize.x;
            mLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            // Create Floating Window
            mFloatingPage = View.inflate(new ContextThemeWrapper(this, R.style.Theme_LongScreenshot), R.layout.floating_window_layout, null);
            mFloatingPage.setOnTouchListener((view, motionEvent) -> {
                hideFloatingWindow();
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> saveScreenshotToExternalStorage(), 1000
                );
                disableSelf();
                return true;
            });

            BottomNavigationView floatingMenu = (BottomNavigationView) mFloatingPage.findViewById(R.id.floating_menu);
            floatingMenu.setOnNavigationItemSelectedListener(item -> {
                switch (item.getItemId()) {
                    case R.id.floating_scroll:
                        hideFloatingWindow();
                        new Handler(Looper.getMainLooper()).postDelayed(
                                (Runnable) () -> {
                                    dispatchGesture(
                                            createSwipe(
                                                    mDisplaySize.x / 2.0f, mDisplaySize.y * 2.0f / 3.0f,
                                                    mDisplaySize.x / 2.0f, mDisplaySize.y / 3.0f,
                                                    1000),
                                            null, null);
                                }, 400
                        );
                        new Handler(Looper.getMainLooper()).postDelayed(
                                (Runnable) () -> {
                                    takeScreenshot();
                                    showFloatingWindow();
                                }, 1500
                        );
                        break;
                    case R.id.floating_edit:
                        saveScreenshotToExternalStorage();
                        startInvisibleActivityWithAction("Edit");
                        disableSelf();
                        break;
                    case R.id.floating_share:
                        saveScreenshotToExternalStorage();
                        startInvisibleActivityWithAction("Share");
                        disableSelf();
                        break;
                    case R.id.floating_save:
                        hideFloatingWindow();
                        new Handler(Looper.getMainLooper()).postDelayed(
                                this::saveScreenshotToExternalStorage, 1000
                        );
                        disableSelf();
                        break;
                }
                return true;
            });


            showFloatingWindow();
        }

    }

    private void takeScreenshot() {
        mScreenshotList.add(mLatestScreenshot);
    }

    private void saveScreenshotToExternalStorage() {
        if (mScreenshotList.size() != 0) {
            try {
                final ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, Calendar.getInstance().getTime().toString() + ".png");
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
                MyApplication.ScreenshotUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                OutputStream out = getContentResolver().openOutputStream(MyApplication.ScreenshotUri);
                Bitmap resultImage = ImageHelper.StitchImages(mScreenshotList);
                resultImage.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void showFloatingWindow() {
        mWindowManager.addView(mFloatingPage, mLayoutParams);
    }

    private void hideFloatingWindow() {
        mWindowManager.removeViewImmediate(mFloatingPage);
    }

    private GestureDescription createSwipe(float startX, float startY, float endX, float endY,
                                           long duration) {
        GestureDescription.Builder swipeBuilder = new GestureDescription.Builder();
        swipeBuilder.addStroke(createSwipeStroke(startX, startY, endX, endY, duration));
        return swipeBuilder.build();
    }

    private GestureDescription.StrokeDescription createSwipeStroke(
            float startX, float startY, float endX, float endY, long endTime) {
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);
        return new GestureDescription.StrokeDescription(swipePath, 0, endTime - (long) 0);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void startInvisibleActivityWithAction(String action) {
        Intent intent = new Intent(this, InvisibleActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra("Action", action);
        startActivity(intent);
    }
}
