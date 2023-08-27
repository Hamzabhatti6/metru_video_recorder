package com.hamza.metru

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import android.view.View
import com.hamza.metru.databinding.ActivityMainBinding
import com.hamza.metru.viewModels.VideoViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import com.hamza.metru.utils.RecordingTimer

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private var isPlaying: Boolean = false
    private lateinit var cameraExecutor: ExecutorService
    private val viewModel: VideoViewModel by viewModels()
    private lateinit var videoRecordingTimer: RecordingTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            viewModel.startCamera(this, viewBinding.pvCamera!!.surfaceProvider)
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        viewBinding.btnStartStop!!.setOnClickListener {
            if (viewModel.isRecording){
                viewModel.stopRecording()
                viewBinding.ivMask!!.visibility = View.VISIBLE
                viewBinding.tvVideoTimer!!.visibility = View.GONE
            }
            else{
                if (!viewModel.isStartTimer){
                    setupStartRecording()
                }
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

        videoRecordingTimer = RecordingTimer(30000L, 1000L, false,
            {
                viewModel.stopRecording()
            },
            {
                val seconds= (30 - (it / 1000)).toString()
                viewBinding.tvVideoTimer?.text = "00:$seconds"
            }
        )
    }

    private fun setupStartRecording(){
        showCameraView()
        viewBinding.tvBreath!!.visibility = View.VISIBLE
        viewBinding.tvStartTimer!!.visibility = View.VISIBLE

        viewModel.setupStartRecording(){isSetup, timer ->
            if (isSetup){
                viewBinding.tvStartTimer!!.text =  timer
            }
            else{
                viewBinding.tvVideoTimer!!.visibility = View.VISIBLE
                viewBinding.ivMask!!.visibility = View.GONE
                viewBinding.tvBreath!!.visibility = View.GONE
                viewBinding.tvStartTimer!!.visibility = View.GONE
                startRecording()
            }
        }
    }

    private fun startRecording() {
        viewBinding.btnStartStop!!.isEnabled = false
        viewBinding.tvVideoTimer?.text = "00:00"
        viewModel.startRecording { recording, url ->
            if (recording){
                viewBinding.btnStartStop!!.apply {
                    text = getString(R.string.stop)
                    isEnabled = true
                }
                videoRecordingTimer.start()
            }
            else{
                viewBinding.btnStartStop!!.apply {
                    text = getString(R.string.start)
                    isEnabled = true
                }
                showVideoView(url)
                videoRecordingTimer.stop()
            }
        }
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
                viewModel.startCamera(this, viewBinding.pvCamera!!.surfaceProvider)
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