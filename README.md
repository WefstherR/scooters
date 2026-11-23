# Recibos — comprobantes de pago para ventas particulares

App nativa para Android que permite a un vendedor particular registrar una venta, generar un
**recibo de pago no tributario** en PDF o imagen (PNG) y compartirlo por WhatsApp, correo u otras apps.

> Este recibo **no es una boleta ni una factura** y no reemplaza documentos tributarios obligatorios.
> La app no se conecta al SII, no procesa pagos y no necesita internet.

## Funciones del MVP

| Área | Qué incluye |
| --- | --- |
| Inicio | Botón «Nuevo comprobante», resumen (recibido, pendiente, emitidos), historial, buscador por número/cliente/producto, filtros por fecha y método de pago |
| Comprobante | Numeración correlativa automática (0001, 0002…), fecha y hora editables, estado Pagado/Pendiente, varios productos, descuento fijo, totales automáticos en CLP |
| Pago | Efectivo, transferencia o link de pago + N.º de operación opcional. «Pagado» exige confirmación manual |
| Diseño | Mismo diseño en PDF (A5 vertical) y PNG (1440 px de ancho, alto según contenido). Logo opcional y color de acento |
| Entrega | Descargar PDF/PNG en `Descargas/Recibos`, compartir por WhatsApp, correo o Android Sharesheet. Nada se envía solo |
| Historial | Volver a exportar sin duplicar, duplicar como nuevo, anular conservando el número, editar borradores, marcar pendiente como pagado |
| Vendedor | Nombre comercial, logo, teléfono, correo, mensaje de agradecimiento y apariencia. Cada recibo emitido guarda una copia de estos datos |

**Flujo principal:** Inicio → Nuevo comprobante → Completar datos → Vista previa → Confirmar y emitir → Descargar o compartir.

## Reglas importantes

- El número se asigna **al emitir** (los borradores no consumen números) dentro de una transacción de base de datos.
  Un número nunca se repite ni se reutiliza, aunque el comprobante se anule.
- Un comprobante emitido **no se puede editar**: solo cambiar su estado (anular o marcar como pagado).
  Para corregir, se anula y se duplica.
- Con estado **Pendiente** el recibo dice «TOTAL PENDIENTE» y su texto legal aclara que no acredita la recepción de dinero.
- Montos en pesos chilenos, enteros, con punto de miles: `$215.000`.

## Tecnología

Kotlin · Jetpack Compose + Material 3 · MVVM · Room · DataStore · `PdfDocument` · FileProvider · MediaStore.
Android 10 (API 29) o superior. **Sin permisos peligrosos ni permiso de internet.**

```
app/src/main/java/cl/recibos/app/
├── domain/            Modelos y reglas puras (cálculos, formato CLP, validación) — sin Android
│   ├── model/
│   └── util/
├── data/
│   ├── local/         Room: entidades, DAO, base de datos, mapeadores
│   └── repository/    ReceiptRepository (numeración, emisión, anulación), SellerRepository (DataStore), filtros
├── export/            ReceiptRenderer (diseño único), ReceiptExporter (PDF/PNG/Descargas), ShareIntents, LogoStorage
├── ui/                Pantallas Compose + ViewModels
│   ├── home/          Inicio e historial
│   ├── editor/        Formulario del comprobante
│   ├── receipt/       Vista previa, emisión, descarga y compartir
│   ├── settings/      Datos del vendedor
│   ├── components/    Componentes reutilizables
│   └── theme/
├── AppNavHost (ui/)   Navegación
├── RecibosApp.kt      Contenedor de dependencias
└── MainActivity.kt
```

## Compilar e instalar

### Opción A — Android Studio (recomendada)

1. Instala [Android Studio](https://developer.android.com/studio) (Ladybug o posterior).
2. **File → Open** y elige esta carpeta. Espera a que termine la sincronización de Gradle.
3. Conecta tu teléfono con la *depuración USB* activada (o crea un emulador) y presiona ▶ **Run**.
4. Para obtener un APK: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Opción B — Línea de comandos

Requiere JDK 17+ y el Android SDK (con `ANDROID_HOME` definido o un archivo `local.properties` con `sdk.dir=...`).

```bash
./gradlew testDebugUnitTest   # pruebas
./gradlew assembleDebug       # APK instalable → app/build/outputs/apk/debug/app-debug.apk
```

Para instalarlo, copia el APK al teléfono y ábrelo (Android pedirá permitir «instalar apps desconocidas»),
o usa `adb install app/build/outputs/apk/debug/app-debug.apk`.

### Opción C — Desde GitHub, sin instalar nada

Cada push ejecuta el flujo **Android** (`.github/workflows/android.yml`): corre las pruebas, compila el APK
y lo deja descargable en la pestaña **Actions → (última ejecución) → Artifacts → recibos-debug-apk**.
También publica muestras de comprobantes generados (`muestras-comprobantes`).

### APK de release (más liviano, firmado)

1. Crea una llave una sola vez:
   `keytool -genkey -v -keystore recibos.jks -keyalg RSA -keysize 2048 -validity 10000 -alias recibos`
2. Crea `keystore.properties` en la raíz (está en `.gitignore`, **no lo subas**):
   ```properties
   storeFile=recibos.jks
   storePassword=tu_clave
   keyAlias=recibos
   keyPassword=tu_clave
   ```
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (reducido con R8).

## Pruebas

`app/src/test` contiene pruebas que se ejecutan en el computador (JUnit + Robolectric):

- `ReceiptCalculatorTest`: subtotales, descuentos, límites y desbordes.
- `FormattersTest`: formato CLP, número `0001`, nombre `RECIBO-0001-20261123.pdf`, teléfono → WhatsApp.
- `ReceiptValidatorTest`: validaciones del formulario.
- `ReceiptNumberingTest`: numeración correlativa con Room real (borradores sin número, anulados no reutilizados,
  emitidos inmutables, duplicar, búsqueda y filtros).
- `ReceiptExportTest`: paginación A5 sin cortar filas, PNG de alta resolución y re-exportación sin duplicados.
  Deja muestras visuales en `app/build/receipt-samples/`.

`app/src/androidTest` contiene `ReceiptPdfInstrumentedTest`, que genera PDFs reales y los vuelve a abrir para
comprobar tamaño A5 y paginación. Usa el motor PDF nativo de Android, por eso corre en un teléfono o emulador:
`./gradlew connectedDebugAndroidTest` (en GitHub Actions corre automáticamente en un emulador).

## Almacenamiento y privacidad

- Comprobantes: base de datos local Room (`recibos.db`). Datos del vendedor: DataStore.
- Logo: copia reducida en el almacenamiento privado de la app.
- Archivos para compartir: caché privada (`cache/exports`), expuesta temporalmente solo mediante FileProvider.
- Descargas: `Descargas/Recibos` vía MediaStore (no requiere permisos en Android 10+). Si el archivo ya existe se reemplaza.

## Próximas mejoras sugeridas

Respaldo/exportación de la base de datos, plantillas de productos frecuentes, reporte mensual en CSV,
textos en `strings.xml` para traducir la app.
