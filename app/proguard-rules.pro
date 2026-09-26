# Add project specific ProGuard rules here.

# Gson model classes are populated via reflection (JavCinema.parseJson,
# Configurations.load, Retrofit GsonConverter). Keep structure and field names.
-keep class io.github.javcinema.data.model.** { *; }

# Retrofit service interfaces are instantiated via reflection proxies.
-keep,allowobfuscation,allowshrinking interface io.github.javcinema.network.AvmooApiService
-keep,allowobfuscation,allowshrinking interface io.github.javcinema.network.BasicService

# btsearch.love / btsow.live response models (populated via Gson reflection).
-keep class io.github.javcinema.network.BtSearchResponse { *; }
-keep class io.github.javcinema.network.BtSearchItem { *; }
-keep class io.github.javcinema.network.BtSearchDetailResponse { *; }
-keep class io.github.javcinema.network.BtSearchTorrentFile { *; }
-keep class io.github.javcinema.network.BTSOSearchResponse { *; }
-keep class io.github.javcinema.network.BTSOSearchItem { *; }
-keep class io.github.javcinema.network.BTSOMagnetResponse { *; }
-keep class io.github.javcinema.network.BTSOMagnetData { *; }
-keep class io.github.javcinema.network.BTSOFile { *; }

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

# libtorrent4j：SWIG 生成的绑定。native 侧（libtorrent4j.so）按**类名+成员名**反查 Java
# （告警回调、swigDirector 虚表、getCPtr 等），一旦改名/裁剪就是运行期 UnsatisfiedLinkError
# 或静默收不到回调，而且只在 release 包里出现，极难查。整个包原样保留。
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
