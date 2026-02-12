package com.wasi.streamivstest

import android.content.Context
import android.util.Log
import com.amazonaws.ivs.broadcast.*

enum class BroadcastState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

interface BroadcastListener {
    fun onStateChanged(state: BroadcastState)
    fun onError(exception: String)
}

class BroadcastManager(
    private val context: Context,
    private val listener: BroadcastListener
) {
    private val TAG = "IVSBroadcastManager"

    private var broadcastSession: BroadcastSession? = null
    private var currentState: BroadcastState = BroadcastState.DISCONNECTED
    private var customImageSource: SurfaceSource? = null

    // Credenciales y configuración
    private var streamUrl: String = ""
    private var streamKey: String = ""
    private val SLOT_CAMERA_NAME = "camera-wasi"

    /**
     * Establece las credenciales del servidor IVS
     * @param url URL del servidor RTMPS (ejemplo: rtmps://xxxxx.global-contribute.live-video.net:443/app/)
     * @param key Stream key proporcionada por AWS IVS
     */
    fun setCredentials(url: String, key: String) {
        this.streamUrl = url
        this.streamKey = key
        Log.d(TAG, "Credenciales establecidas - URL: $url")
    }

    /**
     * Inicializa el BroadcastSession con configuración básica para móvil
     */
    fun initialize() {
        try {
            // Configuración de video básica para móvil (720p, 30fps)
            val videoConfig = BroadcastConfiguration.Vec2(1280f, 720f)
            val videoFps = 30
            val videoBitrate = 2500000 // 2.5 Mbps

            // Configuración de audio básica
            val audioBitrate = 128000 // 128 kbps
            val audioChannels = 2 // Estéreo

            // Crear configuración de broadcast
            val config = BroadcastConfiguration().apply {
                video.initialBitrate = videoBitrate
                video.maxBitrate = videoBitrate
                video.minBitrate = videoBitrate / 2
                video.targetFramerate = videoFps
                video.size = videoConfig

                audio.bitrate = audioBitrate
                audio.channels = audioChannels

                // Configuración de mixer (mezcla de video/audio)
                mixer.slots = arrayOf(
                    BroadcastConfiguration.Mixer.Slot.with { slot ->
                        slot.name = SLOT_CAMERA_NAME
                        slot.aspect = BroadcastConfiguration.AspectMode.FIT
                        slot.size = videoConfig
                        slot.position = BroadcastConfiguration.Vec2(0f, 0f)
                        slot
                    }
                )
            }

            // Crear sesión de broadcast
            broadcastSession = BroadcastSession(
                context,
                createBroadcastListener(),
                config,
                null
            )

            Log.d(TAG, "BroadcastSession inicializado correctamente")
            //updateState(BroadcastState.DISCONNECTED)

        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar BroadcastSession", e)
            updateState(BroadcastState.ERROR)
            listener.onError("Error al inicializar: ${e.message}")
        }
    }

    /**
     * Conecta al servidor de IVS
     */
    fun connect() {
        try {
            if (streamUrl.isEmpty() || streamKey.isEmpty()) {
                throw IllegalStateException("Credenciales no establecidas. Usa setCredentials() primero.")
            }

            if (broadcastSession == null) {
                throw IllegalStateException("BroadcastSession no inicializado. Usa initialize() primero.")
            }

            updateState(BroadcastState.CONNECTING)
            Log.d(TAG, "Conectando a servidor IVS...")

            broadcastSession?.start(streamUrl, streamKey)

        } catch (e: Exception) {
            Log.e(TAG, "Error al conectar", e)
            updateState(BroadcastState.ERROR)
            listener.onError("Error al conectar: ${e.message}")
        }
    }

    /**
     * Desconecta del servidor
     */
    fun disconnect() {
        try {
            Log.d(TAG, "Desconectando...")
            broadcastSession?.stop()
            updateState(BroadcastState.DISCONNECTED)
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar", e)
            listener.onError("Error al desconectar: ${e.message}")
        }
    }

    /**
     * Libera recursos
     */
    fun release() {
        try {
            disconnect()
            broadcastSession?.release()
            broadcastSession = null
            Log.d(TAG, "Recursos liberados")
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar recursos", e)
        }
    }

    /**
     * Obtiene el BroadcastSession para agregar fuentes de video/audio
     */
    fun getSession(): BroadcastSession? = broadcastSession

    /**
     * Verifica si está transmitiendo
     */
    fun isStreaming(): Boolean = currentState == BroadcastState.CONNECTED

    /**
     * Configura la fuente de video para transmitir
     * Retorna el Surface donde CameraX debe renderizar los frames
     * @return Surface si se configuró correctamente, null si hubo error
     */
    fun setupDeviceCamera(): android.view.Surface? {
        try {
            val session = broadcastSession ?: run {
                Log.e(TAG, "BroadcastSession no está inicializado")
                return null
            }

            // Crear CustomImageSource - esto crea una fuente de video personalizada
            customImageSource = session.createImageInputSource()

            if (customImageSource != null) {
                // Obtener el Surface donde se deben renderizar los frames de la cámara
                val surface = customImageSource?.inputSurface

                if (surface != null) {
                    // Hacer bind del CustomImageSource al mixer
                    session.mixer.bind(customImageSource, SLOT_CAMERA_NAME)
                    Log.d(TAG, "CustomImageSource creado y enlazado. Surface disponible para CameraX")

                    // Retornar el Surface para que CameraX renderice en él
                    return surface
                } else {
                    Log.e(TAG, "No se pudo obtener el Surface del CustomImageSource")
                    return null
                }
            } else {
                Log.e(TAG, "No se pudo crear CustomImageSource")
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar la cámara: ${e.message}", e)
            listener.onError("Error al configurar cámara: ${e.message}")
            return null
        }
    }

    /**
     * Obtiene el Surface para renderizar frames de CameraX
     * Útil si ya se configuró previamente
     */
    fun getInputSurface(): android.view.Surface? = customImageSource?.inputSurface

    /**
     * Crea el listener para eventos de broadcast
     */
    private fun createBroadcastListener() = object : BroadcastSession.Listener() {
        override fun onStateChanged(state: BroadcastSession.State) {
            Log.d(TAG, "Estado cambiado: $state")
            when (state) {
                BroadcastSession.State.CONNECTING -> updateState(BroadcastState.CONNECTING)
                BroadcastSession.State.CONNECTED -> updateState(BroadcastState.CONNECTED)
                BroadcastSession.State.DISCONNECTED -> updateState(BroadcastState.DISCONNECTED)
                BroadcastSession.State.INVALID -> updateState(BroadcastState.ERROR)
                else -> Log.d(TAG, "Estado no manejado: $state")
            }
        }

        override fun onError(error: BroadcastException) {
            Log.e(TAG, "Error de broadcast: ${error.message}", error)
            updateState(BroadcastState.ERROR)
            listener.onError("Error de transmisión: ${error.message}")
        }
    }

    /**
     * Actualiza el estado y notifica al listener
     */
    private fun updateState(newState: BroadcastState) {
        currentState = newState
        listener.onStateChanged(newState)
    }
}