# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# WorkManager/Glance: InputMerger is instantiated reflectively from class name.
# Keep implementations and their public no-arg constructors for release builds.
-keep class * extends androidx.work.InputMerger {
	public <init>();
}

# Explicit keep for default merger used by WorkManager.
-keep class androidx.work.OverwritingInputMerger {
	public <init>();
}

# Glance callback actions are instantiated reflectively from class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
	public <init>();
}

# Explicit keep for widget action callbacks used by actionRunCallback<T>().
-keep class cn.naivetomcat.hrt_tracker.widget.StartConfirmAction {
	public <init>();
}
-keep class cn.naivetomcat.hrt_tracker.widget.ConfirmDoseAction {
	public <init>();
}
-keep class cn.naivetomcat.hrt_tracker.widget.CancelConfirmAction {
	public <init>();
}

# Future-proof: keep all widget package callback implementations and class names.
-keep class cn.naivetomcat.hrt_tracker.widget.** implements androidx.glance.appwidget.action.ActionCallback {
	public <init>();
}
-keepnames class cn.naivetomcat.hrt_tracker.widget.** implements androidx.glance.appwidget.action.ActionCallback