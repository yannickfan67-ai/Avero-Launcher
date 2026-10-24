# Historical, uncompiled launcher development

These files were moved out of Android's main/test source roots for the 0.2.0-download-only release. They are not compiled into its APK, are not declared as Activities, and cannot be activated by a runtime setting.

They preserve previous Microsoft authentication, JVM/runtime and native-renderer development plus related tests. The earlier source is retained for development history; it is not a promise of current gameplay functionality. Moving it back into a future release would require a reviewed implementation, an appropriate registered AppID, Microsoft authentication, Minecraft entitlement/profile enforcement, and fresh end-to-end validation.

The current shipping app is under android/app/src/main .
