package com.hamza.metru.viewModels

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.CountDownTimer
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamza.metru.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(@ApplicationContext val application: Context) : ViewModel() {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    var isRecording: Boolean = false
    var isStartTimer: Boolean = false
    val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    fun setupStartRecording(callback:  (Boolean,String) -> Unit){
        isStartTimer = true
        viewModelScope.launch {
            try {
                val timer = object: CountDownTimer(5000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        callback.invoke(true,(millisUntilFinished/1000).toString())
                    }

                    override fun onFinish() {
                        callback.invoke(false,"0")
                        isStartTimer = false
                    }
                }
                timer.start()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun startCamera(owner: LifecycleOwner, surface: Preview.SurfaceProvider) {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(application)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(surface)
                }


            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera

                cameraProvider.bindToLifecycle(owner, cameraSelector, preview,videoCapture)

            } catch(exc: Exception) {
            //    Log.e(MainActivity.TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(application))
    }

     fun startRecording(callback: (Boolean, String) -> Unit) {
         isRecording = true
        val videoCapture = this.videoCapture ?: return
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
            .Builder(application.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(application, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(application,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(application)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        callback.invoke(true, "")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            Toast.makeText(application, application.getString(R.string.saved), Toast.LENGTH_SHORT).show()
                            callback.invoke(false, recordEvent.outputResults.outputUri.toString())
                        } else {
                            recording?.close()
                            recording = null
                        }

                        isRecording = false
                    }
                }
            }
    }

     fun stopRecording(){
        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
        }
        isRecording = false
    }

}