package mamboa.yearview;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;

import java.util.Locale;
import java.util.Random;

/**
 * Created by mamboa on 9/4/2018.
 */
public class Utils {
    public static Locale getCurrentLocale(Context context){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            return context.getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            return context.getResources().getConfiguration().locale;
        }
    }

    public static int getRandomColor(){
        Random rnd = new Random();
        return Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
    }
}
