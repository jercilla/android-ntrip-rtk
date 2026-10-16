# Reglas de ProGuard/R8 para la app.
# Por ahora minify está desactivado; se afinará al preparar la release.

# kotlinx.serialization: conservar los serializadores generados.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class es.gailen.rtk.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class es.gailen.rtk.**$$serializer { *; }
