# The app is open source, so nothing is obfuscated: names stay readable. R8 only shrinks and
# optimises. It does merge classes, so for an exact stack trace use the mapping.txt of the release.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# Only rules that are needed and not shipped by the libraries themselves. Moshi's generated
# adapters, ObjectBox, Hilt, WorkManager, Compose and AboutLibraries bring their own.

# Automerge: the native library finds these classes, constructors, fields and methods by name
# through JNI, and R8 cannot see that. Without this rule documents silently stay empty.
-keep class org.automerge.** { *; }

# kutepreferences has optional dependencies for preference types this app does not use
# (color, URL), and the debug build does not contain them either.
-dontwarn com.godaddy.android.colorpicker.**
-dontwarn org.apache.commons.validator.routines.UrlValidator
