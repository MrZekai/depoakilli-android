# DepoAkıllı keeps no reflection-based application models.
# Google Mobile Ads and UMP publish their consumer rules with their AARs.

# Remove informational diagnostics from minified production/QA binaries.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int i(...);
}
