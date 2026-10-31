# Strip all android.util.Log calls from release builds.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
}

# Keep the WorkManager worker constructor entry point.
-keep class me.mjunaid.mjpay.work.DeliveryWorker { <init>(...); }
-keep class me.mjunaid.mjpay.work.HeartbeatWorker { <init>(...); }
-keep class me.mjunaid.mjpay.work.UpdateWorker { <init>(...); }
