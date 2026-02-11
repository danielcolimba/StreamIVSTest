package com.wasi.streamivstest

import android.content.Context
import android.util.Log
import com.amazonaws.ivs.broadcast.*

enum class BroadcastState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED, // EN VIVO
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

    // Credenciales y configuración
    private var streamUrl: String = ""
    private var streamKey: String = ""

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
                        slot.name = "camera"
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