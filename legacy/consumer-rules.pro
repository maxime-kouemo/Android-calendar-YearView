# Consumer ProGuard/R8 rules for the :legacy YearView module.
#
# These are packaged into the AAR and applied automatically to any application
# that shrinks its build. The library itself is published unminified, so this
# file is the only thing protecting the public API from the consumer's R8 pass.

# --- Public API -------------------------------------------------------------
# YearView is inflated by name from XML layouts, so the class and its
# (Context, AttributeSet) constructor must survive.
-keep public class com.mamboa.yearview.legacy.YearView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public *;
}

# Callback interface implemented by consumers, including from Java.
-keep public interface com.mamboa.yearview.legacy.MonthGestureListener { *; }

# Configuration and style holders are part of the public surface and are
# constructed reflectively by @Parcelize.
-keep public class com.mamboa.yearview.legacy.MonthConfig { *; }
-keep public class com.mamboa.yearview.legacy.DayConfig { *; }
-keep public class com.mamboa.yearview.legacy.LegacyBackgroundStyle { *; }

# --- Parcelable -------------------------------------------------------------
# @Parcelize generates a static CREATOR field that is only ever looked up
# reflectively by the framework, so R8 cannot see the reference.
-keepclassmembers class com.mamboa.yearview.legacy.** implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keep class com.mamboa.yearview.legacy.YearView$SavedState { *; }

# --- Custom shape / image providers ----------------------------------------
# Consumers supply their own implementations; keep the SPI intact.
-keep public class * implements com.mamboa.yearview.core.CustomShapeProvider { *; }
-keep public class * implements com.mamboa.yearview.core.ImageProvider { *; }

# --- Styleable attributes ---------------------------------------------------
# R.styleable is read via TypedArray during inflation.
-keepclassmembers class com.mamboa.yearview.legacy.R$styleable {
    public static <fields>;
}
