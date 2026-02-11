# IVSBroadcastManager - Guía de Uso

## Descripción
`IVSBroadcastManager` es una clase wrapper para simplificar el uso de AWS IVS Broadcast SDK en aplicaciones Android. Proporciona una interfaz simple para transmitir video en vivo a Amazon IVS.

## Características

- ✅ Configuración simple de credenciales AWS IVS
- ✅ Inicialización automática con configuración optimizada para móvil (720p, 30fps)
- ✅ Manejo de estados de conexión (DISCONNECTED, CONNECTING, CONNECTED, ERROR)
- ✅ Callbacks para eventos de transmisión
- ✅ Configuración de video: 1280x720, 2.5 Mbps, 30 FPS
- ✅ Configuración de audio: 128 kbps, estéreo, 44.1 kHz

## Requisitos

### Dependencias en build.gradle.kts
```kotlin
implementation("com.amazonaws:ivs-broadcast:1.38.0:stages@aar")
```

### Permisos en AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

## Uso Básico

### 1. Crear el BroadcastManager

```kotlin
val broadcastManager = IVSBroadcastManager(context, object : BroadcastListener {
    override fun onStateChanged(state: BroadcastState) {
        when (state) {
            BroadcastState.DISCONNECTED -> {
                // Transmisión desconectada
            }
            BroadcastState.CONNECTING -> {
                // Conectando al servidor
            }
            BroadcastState.CONNECTED -> {
                // ¡EN VIVO!
            }
            BroadcastState.ERROR -> {
                // Error en la transmisión
            }
            else -> {}
        }
    }
    
    override fun onError(exception: String) {
        Log.e("Broadcast", "Error: $exception")
    }
})
```

### 2. Configurar Credenciales

Obtén tus credenciales desde AWS IVS Console:

```kotlin
val streamUrl = "rtmps://xxxxx.global-contribute.live-video.net:443/app/"
val streamKey = "sk_us-west-2_xxxxxxxxxxxxxxxx"

broadcastManager.setCredentials(streamUrl, streamKey)
```

### 3. Inicializar la Sesión

```kotlin
broadcastManager.initialize()
```

### 4. Configurar la Cámara

```kotlin
// Obtener la sesión
val session = broadcastManager.getSession() ?: return

// Buscar dispositivo de cámara
val devices = session.listAttachedDevices()
val cameraDevice = devices.firstOrNull { 
    it is ImageDevice && it.descriptor.isCamera 
} as? ImageDevice

// Configurar preview
cameraDevice?.setPreviewView(previewView) // ImagePreviewView desde tu layout

// Adjuntar al mixer
session.mixer.bind(cameraDevice, "camera")
```

### 5. Iniciar Transmisión

```kotlin
broadcastManager.connect()
```

### 6. Detener Transmisión

```kotlin
broadcastManager.disconnect()
```

### 7. Liberar Recursos (en onDestroy)

```kotlin
broadcastManager.release()
```

## Configuración de Video

La configuración por defecto es:

| Parámetro | Valor |
|-----------|-------|
| Resolución | 1280x720 (720p) |
| FPS | 30 |
| Bitrate Inicial | 2.5 Mbps |
| Bitrate Máximo | 2.5 Mbps |
| Bitrate Mínimo | 1.25 Mbps |

## Configuración de Audio

| Parámetro | Valor |
|-----------|-------|
| Bitrate | 128 kbps |
| Canales | 2 (Estéreo) |
| Sample Rate | 44100 Hz |

## Métodos Disponibles

### `setCredentials(url: String, key: String)`
Establece la URL del servidor y la stream key de AWS IVS.

### `initialize()`
Inicializa el BroadcastSession con la configuración predeterminada.

### `connect()`
Conecta al servidor IVS e inicia la transmisión.

### `disconnect()`
Desconecta del servidor y detiene la transmisión.

### `release()`
Libera todos los recursos. Debe llamarse en onDestroy.

### `getSession(): BroadcastSession?`
Retorna la sesión de broadcast para configuración avanzada.

### `isStreaming(): Boolean`
Verifica si está actualmente transmitiendo.

## Estados de Transmisión

### `BroadcastState.DISCONNECTED`
No hay conexión al servidor.

### `BroadcastState.CONNECTING`
Intentando conectar al servidor.

### `BroadcastState.CONNECTED`
Conectado y transmitiendo en vivo.

### `BroadcastState.ERROR`
Ocurrió un error.

## Ejemplo Completo

Ver el archivo `BroadcastManagerExample.kt` para un ejemplo completo de implementación con:
- Configuración de cámara
- Cambio entre cámara frontal y trasera
- Manejo de estados
- Integración con Activity/Fragment

## Obtener Credenciales de AWS IVS

1. Ve a AWS Console → Amazon IVS
2. Crea un nuevo Canal (Channel)
3. Obtén la **Ingest server** (URL del servidor)
4. Obtén la **Stream key**

Ejemplo de credenciales:
```
URL: rtmps://a1b2c3d4e5f6.global-contribute.live-video.net:443/app/
Key: sk_us-west-2_A1B2C3D4E5F6G7H8
```

## Notas Importantes

- ⚠️ Siempre solicita permisos de CAMERA y RECORD_AUDIO en tiempo de ejecución (Android 6.0+)
- ⚠️ Llama a `release()` en onDestroy() para evitar memory leaks
- ⚠️ La transmisión requiere una conexión a internet estable
- ⚠️ Para producción, no hardcodees las credenciales en el código

## Troubleshooting

### Error: "Credenciales no establecidas"
Asegúrate de llamar `setCredentials()` antes de `connect()`.

### Error: "BroadcastSession no inicializado"
Asegúrate de llamar `initialize()` antes de `connect()`.

### La cámara no se muestra
Verifica que hayas:
1. Solicitado los permisos necesarios
2. Configurado el `ImagePreviewView` en tu layout
3. Llamado a `setPreviewView()` y `mixer.bind()`

### Problemas de conexión
- Verifica que las credenciales sean correctas
- Verifica que tengas conexión a internet
- Revisa los logs con el tag "IVSBroadcastManager"

## Soporte

Para más información sobre AWS IVS:
- [Documentación oficial de AWS IVS](https://docs.aws.amazon.com/ivs/)
- [SDK de AWS IVS para Android](https://github.com/aws/amazon-ivs-broadcast-for-android-demo)

