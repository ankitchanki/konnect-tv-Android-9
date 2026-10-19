# libadb talks to Conscrypt and its SPAKE2 JNI code by name.
-keep class io.github.muntashirakon.** { *; }
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
