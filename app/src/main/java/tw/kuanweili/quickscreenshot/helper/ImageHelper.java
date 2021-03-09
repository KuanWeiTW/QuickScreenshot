package tw.kuanweili.quickscreenshot.helper;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import java.util.List;

public class ImageHelper {
    public static Bitmap StitchImages(List<Bitmap> images)
    {
        if (images.size() == 1) {
            return images.get(0);
        } else {
            Bitmap result = Bitmap.createBitmap(images.get(0).getWidth(), (int) (images.get(0).getHeight() * (1 + (images.size() - 1) / 3.0)), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            canvas.drawBitmap(images.get(0), 0f, 0, null);
            for (int i = 1; i < images.size(); i++) {
                Bitmap croppedBitmap = Bitmap.createBitmap(images.get(i), 0, images.get(i).getHeight() / 3, images.get(i).getWidth(), images.get(i).getHeight() * 2 / 3);
                canvas.drawBitmap(croppedBitmap, 0f, (float) ((i + 1) * images.get(0).getHeight() / 3.0), null);
            }
            return result;
        }
    }
}
