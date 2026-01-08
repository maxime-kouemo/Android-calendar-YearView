package com.mamboa.yearview.core.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.mamboa.yearview.core.TitleGravity;

import java.util.Locale;
import java.util.Random;

/**
 * Created by mamboa on 9/4/2018.
 */
public class Utils {
    public static Locale getCurrentLocale(Context context) {
        if (context == null) {
            return Locale.getDefault();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.getResources().getConfiguration().getLocales().get(0);
        } else {
            //noinspection deprecation
            return context.getResources().getConfiguration().locale;
        }
    }

    public static int getRandomColor() {
        Random rnd = new Random();
        return Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
    }

    /**
     * Converts an integer color value to a Color object.
     *
     * @param colorInt The integer representation of the color.
     * @return The Color object representing the specified color value.
     */
    public static Color fromIntToColor(int colorInt) {
        return Color.valueOf(colorInt);
    }

    public static TitleGravity getRandomTitleGravity() {
        Random rnd = new Random();
        return TitleGravity.getEntries().get(rnd.nextInt(TitleGravity.getEntries().size()));
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;

        if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
            return ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth();
        width = width > 0 ? width : 1;
        int height = drawable.getIntrinsicHeight();
        height = height > 0 ? height : 1;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }
}
