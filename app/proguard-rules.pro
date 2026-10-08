# The worker and the widget are instantiated by the system from their class names.
-keep class app.deliveryday.CheckWorker { <init>(...); }
-keep class app.deliveryday.OrderWidget { <init>(); }

# OkHttp optional dependencies.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
