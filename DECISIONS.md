# Decisions

Safest and lightest option chosen whenever the spec was ambiguous.

## Step 1 — Scaffold
- Theme uses `android:Theme.Material.Light.NoActionBar` / `android:Theme.Material.NoActionBar`
  (children of the required Material themes) so screens draw their own header and no
  AppCompat/Material dependency is needed.
- Versions pinned to mutually compatible stable releases available at build time:
  Gradle 8.14.2, AGP 8.13.2, Kotlin 2.2.21, compile/target SDK 36, core-ktx 1.16.0,
  work-runtime 2.10.0. (core-ktx 1.19/work 2.12 require a newer SDK platform than the
  one installed here.)
- `POST_NOTIFICATIONS` is declared but optional; it is used only for the one low-importance
  "deliveries failing" alert.
- Launcher icon is an adaptive vector (minSdk 26), so no PNG variants are needed.
- `local.properties` is machine-specific and git-ignored.
