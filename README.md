# Marca de Agua para Android

Aplicación Android nativa, procesamiento local y exportación a la galería. No requiere conexión a Internet para editar imágenes.

## Compilación automática

El workflow `.github/workflows/android.yml` compila un APK Debug cuando se suben cambios a `main` o al ejecutarlo manualmente desde la pestaña Actions.

## Funciones incluidas en esta primera versión

- Importación de una o varias fotos desde el dispositivo.
- Importación de un logo desde el dispositivo.
- Vista previa y ubicación del logo mediante arrastre.
- Ajuste de opacidad y tamaño.
- Exportación individual o por lote.
- JPEG o PNG, con recorte para formatos sociales comunes.
- Guardado en `Pictures/MarcaDeAgua`, sin modificar originales.
- Preferencias de tamaño, opacidad y posición guardadas localmente.

## Compilar localmente

Requiere JDK 17, Android SDK 35 y Gradle 8.9. Ejecutar `gradle assembleDebug`.
