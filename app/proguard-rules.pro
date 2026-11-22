-keep class cc.csxcl.fluidredirect.** { *; }
-keep class io.github.libxposed.** { *; }

-adaptresourcefilecontents META-INF/xposed/java_init.list

-dontwarn io.github.libxposed.**
-dontwarn com.coui.appcompat.**
