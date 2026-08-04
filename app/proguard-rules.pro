# kotlinx-serialization : les sérialiseurs générés sont référencés par réflexion sur les
# companions, que R8 ne voit pas.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class fr.plantarrosage.** {
    *** Companion;
}
-keepclasseswithmembers class fr.plantarrosage.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class fr.plantarrosage.**$$serializer { *; }

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room génère des implémentations référencées par nom.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
