# Keep SMB / jCIFS reflection targets
-keep class jcifs.** { *; }
-dontwarn jcifs.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**

# Google Cast
-keep class com.google.android.gms.cast.framework.** { *; }

# Room
-keep class androidx.room.** { *; }
