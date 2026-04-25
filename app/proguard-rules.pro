# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the default proguard-android-optimize.txt configuration.

# Keep Accessibility Service
-keep class com.scrollguard.ScrollAccessibilityService { *; }
-keep class com.scrollguard.ScrollBlockerService { *; }
