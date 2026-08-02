# ADBeesh Library ProGuard Rules

# Kotlin-specific protection for reflection/metadata
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn kotlin.Metadata
-keep class kotlin.Metadata { *; }

# Keep public API of ADBeesh to prevent it from being stripped or renamed
-keep class com.indidevs.android.adbeesh.** {
    public *;
}
