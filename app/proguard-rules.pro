# DepoAkıllı keeps no reflection-based application models.
# Google Play Billing publishes its consumer rules with its AAR.

# Remove informational diagnostics from minified production/QA binaries.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int i(...);
}
