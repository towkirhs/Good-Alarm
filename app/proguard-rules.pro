# Room and Compose ship their own keep rules; nothing extra needed for now.

# Widget button handlers are created by class name; keep them.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
