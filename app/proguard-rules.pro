# Add project specific ProGuard rules here.

# Gson model classes are populated via reflection (JavCinema.parseJson,
# Configurations.load, Retrofit GsonConverter). Keep structure and field names.
-keep class io.github.javcinema.data.model.** { *; }

# Retrofit service interfaces are instantiated via reflection proxies.
-keep,allowobfuscation,allowshrinking interface io.github.javcinema.network.AvmooApiService
-keep,allowobfuscation,allowshrinking interface io.github.javcinema.network.BasicService

# Generic signatures are required by Gson/Retrofit for typed reflection.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Gson
-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep @interface com.google.gson.annotations.SerializedName

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit
-keepattributes Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Serializable models (DataStore via Gson file cache)
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
