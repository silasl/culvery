# Culvery's own R8 rules (4c design §3.3, §3.4); the libraries ship theirs.

# Release stack traces keep their line numbers; source file names are hidden. Read them with the build's mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Release logging (D8): debug and info lines are removed; warnings and errors stay, and name no one.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Warnings name the exception that caused them (D8), so the app's own exception classes keep their names.
-keepnames class uk.co.siland.culvery.** extends java.lang.Throwable
