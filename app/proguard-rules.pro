# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.conreo.couchytv.**$$serializer { *; }
-keepclassmembers class com.conreo.couchytv.** {
    *** Companion;
}
-keepclasseswithmembers class com.conreo.couchytv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The fixed power helper is invoked by app_process, outside the launcher VM.
-keep class com.conreo.couchytv.DisplayGuard { public static void main(java.lang.String[]); }
-keep class com.conreo.couchytv.SaverBridge { public static void main(java.lang.String[]); }
-keep class com.conreo.couchytv.FileBridge { public static void main(java.lang.String[]); }
