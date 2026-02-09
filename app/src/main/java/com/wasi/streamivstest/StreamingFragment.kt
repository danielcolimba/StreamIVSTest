package com.wasi.streamivstest

import android.Manifest
import android.content.pm.PackageManager
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

class StreamingFragment : Fragment() {


    // Variable temporal para el binding
    private var _binding: FragmentStreamingBinding? = null
    // Esta propiedad es la que usaremos, solo es válida entre onCreateView y onDestroyView
    private val binding get() = _binding!!
    private val viewModel: StreamingViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted && audioGranted) {
            // Ambos permisos concedidos, podemos iniciar la cámara
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
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
        cameraExecutor = Executors.newSingleThreadExecutor()
        checkPermissionsAndStart()
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                // Vincular a la cámara
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)

            } catch (exc: Exception) {
                Log.e("CameraX", "Error al iniciar la cámara", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}