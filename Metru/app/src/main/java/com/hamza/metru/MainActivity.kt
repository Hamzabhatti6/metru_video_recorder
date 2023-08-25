package com.hamza.metru

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.view.View
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.PermissionChecker
import com.hamza.metru.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording: Boolean = false
    private var isVideoView: Boolean = false
    private var isPlaying: Boolean = false
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        viewBinding.btnStartStop!!.setOnClickListener {
            if (isRecording){
               stopRecording()
            }
            else{
                setupStartRecording()
            }
        }
        viewBinding.btnPlayPause!!.setOnClickListener {
            if (isPlaying){
                pauseVideo()
            }
            else{
                playVideo()
            }
        }
        viewBinding.vvVideo!!.setOnCompletionListener {
            viewBinding.btnPlayPause!!.text = getString(R.string.play)
            isPlaying = false
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupStartRecording(){
        showCameraView()
        viewBinding.tvBreath!!.visibility = View.VISIBLE
        viewBinding.tvStartTimer!!.visibility = View.VISIBLE

        val timer = object: CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                viewBinding.tvStartTimer!!.text = (millisUntilFinished/1000).toString()
            }

            override fun onFinish() {
                viewBinding.tvVideoTimer!!.visibility = View.VISIBLE
                viewBinding.ivMask!!.visibility = View.GONE
                viewBinding.tvBreath!!.visibility = View.GONE
                viewBinding.tvStartTimer!!.visibility = View.GONE
                isRecording = true
                startRecording()
            }
        }
        timer.start()
    }

    private fun stopRecording(){
        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
        }
        viewBinding.ivMask!!.visibility = View.VISIBLE
        viewBinding.tvVideoTimer!!.visibility = View.GONE
        isRecording = false
    }

    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.btnStartStop!!.isEnabled = false


        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Metru Videos")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.btnStartStop!!.apply {
                            text = getString(R.string.stop)
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            Toast.makeText(baseContext, getString(R.string.saved), Toast.LENGTH_SHORT).show()
                            showVideoView(recordEvent.outputResults.outputUri.toString())

                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, getString(R.string.error) + "${recordEvent.error}")
                        }
                        viewBinding.btnStartStop!!.apply {
                            text = getString(R.string.start)
                            isEnabled = true
                        }
                    }
                }
            }
    }

    private fun showVideoView(url: String){
        viewBinding.vvVideo!!.visibility = View.VISIBLE
        viewBinding.pvCamera!!.visibility = View.INVISIBLE
        viewBinding.tvVideoTimer!!.visibility = View.INVISIBLE
        viewBinding.tvBreath!!.visibility = View.INVISIBLE
        viewBinding.tvStartTimer!!.visibility = View.INVISIBLE
        viewBinding.ivMask!!.visibility = View.INVISIBLE

        viewBinding.vvVideo!!.setVideoPath(url)
        viewBinding.vvVideo!!.seekTo( 1 )

    }

    private fun showCameraView(){
        viewBinding.vvVideo!!.visibility = View.GONE
        viewBinding.vvVideo!!.stopPlayback()
        viewBinding.pvCamera!!.visibility = View.VISIBLE
        viewBinding.ivMask!!.visibility = View.VISIBLE

        viewBinding.vvVideo!!.stopPlayback()
        viewBinding.btnPlayPause!!.text = getString(R.string.play)
        isPlaying = false
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.pvCamera!!.surfaceProvider)
                }


            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview,videoCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun playVideo(){
        viewBinding.btnPlayPause!!.text = getString(R.string.pause)
        viewBinding.vvVideo!!.start()
        isPlaying = true
    }
    private fun pauseVideo(){
        viewBinding.btnPlayPause!!.text = getString(R.string.play)
        viewBinding.vvVideo!!.pause()
        isPlaying = false
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, getString(R.string.permission_msg), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "MetruApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}