# Xposed
-adaptresourcefilecontents META-INF/xposed/java_init.list
-adaptresourcefilecontents META-INF/xposed/native_init.list
-keepattributes RuntimeVisibleAnnotations
-keep,allowobfuscation,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public <init>(...);
    public void onPackageLoaded(...);
    public void onSystemServerLoaded(...);
}
-keep,allowoptimization,allowobfuscation @io.github.libxposed.api.annotations.* class * {
    @io.github.libxposed.api.annotations.BeforeInvocation <methods>;
    @io.github.libxposed.api.annotations.AfterInvocation <methods>;
}

# Native
-keep class com.deadcells.modding.AssetReplacer { *; }
-keep class com.deadcells.modding.PakTool { *; }
-keep class com.deadcells.modding.AssetInjector { *; }

# Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void check*(...);
	public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}

# Strip debug log
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Obfuscation
-repackageclasses
-allowaccessmodification
