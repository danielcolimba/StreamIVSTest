package com.wasi.streamivstest

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
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
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

import kotlin.use

class StreamingFragment : Fragment(), BroadcastListener {


    // Variable temporal para el binding
    private var _binding: FragmentStreamingBinding? = null
    // Esta propiedad es la que usaremos, solo es válida entre onCreateView y onDestroyView
    private val binding get() = _binding!!
    private val viewModel: StreamingViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var broadcastManager: BroadcastManager



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
            setupBroadcastCamera()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun setupBroadcastCamera() {
        // Inicializar el BroadcastManager
        broadcastManager.initialize()

        // Obtener la sesión y configurar la cámara de IVS
        val session = broadcastManager.getSession() ?: return

        // Buscar dispositivo de cámara
        val devices = session.listAttachedDevices()
        val cameraDevice = devices.firstOrNull {
            it is com.amazonaws.ivs.broadcast.ImageDevice &&
            it.descriptor.type == com.amazonaws.ivs.broadcast.Device.Descriptor.DeviceType.CAMERA
        } as? com.amazonaws.ivs.broadcast.ImageDevice

        cameraDevice?.let { camera ->
            // Adjuntar la cámara al mixer
            session.mixer.bind(camera, "camera")
            Log.d("StreamingFragment", "Cámara IVS configurada: ${camera.descriptor.friendlyName}")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        broadcastManager = BroadcastManager(requireContext(), this)
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

        /*viewModel.broadcastState.observe(viewLifecycleOwner) { state ->
            updateUI(state)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            Toast.makeText(requireContext(), "Error: $msg", Toast.LENGTH_LONG).show()
        }*/

        checkPermissionsAndStart()

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // No satura la memoria
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) // Formato compatible con Bitmaps
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                // Aquí recibimos cada frame (ImageProxy)
                //processFrameForIVS(imageProxy)
            }

            try {
                cameraProvider.unbindAll()

                // Vincular a la cámara
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

            } catch (exc: Exception) {
                Log.e("CameraX", "Error al iniciar la cámara", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processFrameForIVS(imageProxy: ImageProxy) {
        imageProxy.use { proxy ->
            val rawBitmap = proxy.toBitmap() ?: return@use
            val rotatedBitmap = if (proxy.imageInfo.rotationDegrees != 0) {
                rotateBitmap(rawBitmap, proxy.imageInfo.rotationDegrees.toFloat())
            } else {
                rawBitmap
            }
            val finalBitmap = Bitmap.createScaledBitmap(rotatedBitmap, 720, 1280, true)

            //viewModel.processBitmap(finalBitmap)
        }
    }


    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun updateUI(state: BroadcastState) {
        when (state) {
            BroadcastState.DISCONNECTED, BroadcastState.ERROR -> {
                binding.btnBroadcast.text = "INICIAR TRANSMISIÓN"
                binding.btnBroadcast.backgroundTintList = ColorStateList.valueOf(Color.BLUE) // O tu color primario
                binding.btnBroadcast.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
            BroadcastState.CONNECTING -> {
                binding.btnBroadcast.text = "CONECTANDO..."
                binding.btnBroadcast.isEnabled = false // Evitar doble click
                binding.progressBar.visibility = View.VISIBLE
            }
            BroadcastState.CONNECTED -> {
                binding.btnBroadcast.text = "DETENER TRANSMISIÓN"
                binding.btnBroadcast.backgroundTintList = ColorStateList.valueOf(Color.RED) // Rojo para indicar EN VIVO
                binding.btnBroadcast.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
            else -> { /* Manejar otros estados si es necesario */ }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
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