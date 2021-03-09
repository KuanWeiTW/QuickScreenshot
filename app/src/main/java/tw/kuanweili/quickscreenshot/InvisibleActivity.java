package tw.kuanweili.quickscreenshot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import kuanweitw.github.io.ananas.editimage.EditImageActivity;
import kuanweitw.github.io.ananas.editimage.ImageEditorIntentBuilder;
import tw.kuanweili.quickscreenshot.helper.Helper;
import tw.kuanweili.quickscreenshot.helper.MyApplication;


public class InvisibleActivity extends Activity {
    private static final String TAG = "InvisibleActivity";
    private static final int SCREENSHOT_REQUEST_CODE = 8899;
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 8888;
    private static final int SHARE_REQUEST_CODE = 8890;
    private static final int PHOTO_EDITOR_REQUEST_CODE = 231;

    private MediaProjectionManager mMediaProjectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String action = getIntent().getExtras().getString("Action");
        switch (action) {
            case "Init":
                //Request Storage Permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
                mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

                getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

                startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), SCREENSHOT_REQUEST_CODE);
                break;
            case "Share":
                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                sharingIntent.setType("image/*");
                sharingIntent.putExtra(Intent.EXTRA_STREAM, MyApplication.ScreenshotUri);
                startActivityForResult(Intent.createChooser(sharingIntent, "Share image using"), SHARE_REQUEST_CODE);
                break;
            case "Edit":
                try {
                    String imagePath = Helper.getRealPathFromURI(this, MyApplication.ScreenshotUri);
                    Intent intent = new ImageEditorIntentBuilder(this, MyApplication.ScreenshotUri, MyApplication.ScreenshotUri)
                            .withAddText() // Add the features you need
                            .withPaintFeature()
                            .withRotateFeature()
                            .withCropFeature()
                            .withStickerFeature()
                            .forcePortrait(true)  // Add this to force portrait mode (It's set to false by default)
                            .setSupportActionBarVisibility(false) // To hide app's default action bar
                            .build();

                    EditImageActivity.start(this, intent, PHOTO_EDITOR_REQUEST_CODE);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage()); // This could throw if either `sourcePath` or `outputPath` is blank or Null
                }
                break;
        }

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCREENSHOT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                MyApplication.MediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
                sendScreenshotAuthorizedBroadcast();
                finish();
            } else {
                sendScreenshotAuthorizationCanceled();
                finish();
            }
        } else if (requestCode == SHARE_REQUEST_CODE) {
            finish();
        } else if (requestCode == PHOTO_EDITOR_REQUEST_CODE) {
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void sendScreenshotAuthorizedBroadcast() {
        Intent intent = new Intent();
        intent.setAction(MyApplication.SCREENSHOT_AUTHORIZED_SIGNAL);
        sendBroadcast(intent);
    }

    private void sendScreenshotAuthorizationCanceled() {
        Intent intent = new Intent();
        intent.setAction(MyApplication.SCREENSHOT_AUTHORIZATION_CANCELED_SIGNAL);
        sendBroadcast(intent);
    }
}