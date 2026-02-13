package com.wasi.streamivstest

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.fragment.app.viewModels
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.wasi.streamivstest.databinding.FragmentStreamingBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class StreamingFragment : Fragment(), BroadcastListener {

    private var _binding: FragmentStreamingBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var broadcastManager: BroadcastManager
    private var ivsSurface: android.view.Surface? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted && audioGranted) {
            // Ambos permisos concedidos, podemos iniciar la cámara
            setupBroadcastCamera()
            startCamera()
        } else {
            // Explicar al usuario que sin permisos no hay streaming
            Toast.makeText(requireContext(), "Se requieren permisos para transmitir", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isEmpty()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    /**
     * Configura la cámara IVS para la transmisión
     */
    private fun setupBroadcastCamera() {
        // Inicializar el BroadcastManager
        broadcastManager.initialize()

        // Obtener el Surface de IVS donde se deben renderizar los frames
        ivsSurface = broadcastManager.setupDeviceCamera()

        if (ivsSurface != null) {
            Log.d("StreamingFragment", "Surface de IVS obtenido correctamente")
        } else {
            Log.e("StreamingFragment", "No se pudo obtener el Surface de IVS")
            Toast.makeText(requireContext(), "Error al configurar el streaming", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        broadcastManager = BroadcastManager(requireContext(), this)
        setupBroadcastCamera()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStreamingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBroadcast.setOnClickListener {
            // Alternar entre iniciar y detener la transmisión
            if (broadcastManager.isStreaming()) {
                broadcastManager.disconnect()
            } else {
                broadcastManager.connect()
            }
        }
        checkPermissionsAndStart()
    }

    /**
     * Inicia CameraX para preview Y para enviar frames a IVS
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview para que el usuario vea la cámara en pantalla
            val preview = Preview.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation) // Usar rotación de la pantalla
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                // Si tenemos el Surface de IVS, crear un segundo Preview para renderizar en él
                if (ivsSurface != null) {
                    val ivsPreview = Preview.Builder()
                        .build()

                    // Configurar el SurfaceProvider personalizado para usar el Surface de IVS
                    ivsPreview.setSurfaceProvider { request ->
                        val surface = ivsSurface
                        if (surface != null) {
                            request.provideSurface(surface, cameraExecutor) { result ->
                                Log.d("CameraX", "Surface de IVS proporcionado: ${result.surface}")
                            }
                        } else {
                            Log.e("CameraX", "Surface de IVS es null")
                        }
                    }

                    // Vincular AMBOS previews: uno para pantalla, otro para IVS
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, ivsPreview)
                    Log.d("CameraX", "Cámara vinculada con preview local Y stream IVS")
                } else {
                    // Solo preview local si no hay Surface de IVS
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview)
                    Log.w("CameraX", "Solo preview local - Surface de IVS no disponible")
                }

            } catch (exc: Exception) {
                Log.e("CameraX", "Error al iniciar la cámara", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun updateUI(state: BroadcastState) {
        when (state) {
            BroadcastState.DISCONNECTED, BroadcastState.ERROR -> {
                binding.btnBroadcast.text = "INICIAR TRANSMISIÓN"
                binding.btnBroadcast.backgroundTintList = ColorStateList.valueOf(Color.BLUE)
                binding.btnBroadcast.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
            BroadcastState.CONNECTING -> {
                binding.btnBroadcast.text = "CONECTANDO..."
                binding.btnBroadcast.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
            }
            BroadcastState.CONNECTED -> {
                binding.btnBroadcast.text = "DETENER TRANSMISIÓN"
                binding.btnBroadcast.backgroundTintList = ColorStateList.valueOf(Color.RED)
                binding.btnBroadcast.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
            else -> { /* Manejar otros estados si es necesario */ }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ivsSurface = null
        broadcastManager.release()
        cameraExecutor.shutdown()
        _binding = null
    }

    override fun onStateChanged(state: BroadcastState) {
        // Actualizar la UI en el hilo principal
        requireActivity().runOnUiThread {
            updateUI(state)
        }
    }

    override fun onError(exception: String) {
        // Mostrar error en el hilo principal
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Error de transmisión: $exception", Toast.LENGTH_LONG).show()
        }
    }
}