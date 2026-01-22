package com.rokid.cxrmsamples.activities.audio

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rokid.cxrmsamples.R
import com.rokid.cxrmsamples.ui.theme.CXRMSamplesTheme

class AudioUsageActivity : ComponentActivity() {
    private val viewModel: AudioUsageViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CXRMSamplesTheme {
                AudioUsageScreen(viewModel)
            }
        }
    }
}

@Composable
fun AudioUsageScreen(
    viewModel: AudioUsageViewModel
) {
    val pickupField = viewModel.pickUpType.collectAsState()
    val playState by viewModel.playStatus.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val recording by viewModel.recording.collectAsState()
    val listRecordName by viewModel.listRecordName.collectAsState()
    val changing by viewModel.changing.collectAsState()
    val pickupType by viewModel.pickUpType.collectAsState()
    val systemAudioRecording by viewModel.systemAudioRecording.collectAsState()

    // Calculate progress ratio (0.0 - 1.0)
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f


    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.glasses_bg),
            modifier = Modifier.fillMaxSize(),
            contentDescription = null,
            alpha = 0.3f
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "", modifier = Modifier.padding(top = 16.dp))
            // Top: Pickup sound field display
            Text(
                text = "拾音声场：${pickupField.value.sceneName}",
                modifier = Modifier
                    .height(64.dp)
                    .padding(top = 16.dp),
            )

            // Recording control buttons
            Row(
                modifier = Modifier
                    .padding(vertical = 16.dp)
            ) {
                Button(
                    onClick = { viewModel.startAudioStream() },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    enabled = !recording
                ) {
                    Text("开始录音")
                }

                Button(
                    onClick = { viewModel.stopAudioStream() },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    enabled = recording
                ) {
                    Text("停止录音")
                }
            }

            Row(modifier = Modifier.padding(vertical = 16.dp)) {
                Button(
                    onClick = { viewModel.changeAudioSceneId(AudioSceneId.NEAR) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    enabled = (!changing && pickupType != AudioSceneId.NEAR)
                ) {
                    Text("近场")
                }
                Button(
                    onClick = { viewModel.changeAudioSceneId(AudioSceneId.FAR) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    enabled = (!changing && pickupType != AudioSceneId.FAR)
                ) {
                    Text("远场")
                }
                Button(
                    onClick = { viewModel.changeAudioSceneId(AudioSceneId.BOTH) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    enabled = (!changing && pickupType != AudioSceneId.BOTH)
                ) {
                    Text("全景")
                }
            }

            // Audio player
            if (!listRecordName.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    Text("音频播放器", modifier = Modifier.padding(bottom = 16.dp))
                    Row {
                        // Play/Pause button
                        Button(
                            onClick = {
                                if (playState == PlayState.PLAYING) {
                                    viewModel.pausePlayAudio()
                                } else {
                                    viewModel.startPlayAudio()
                                }
                            },
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                if (playState == PlayState.PLAYING) {
                                    "暂停"
                                } else {
                                    "播放"
                                }
                            )
                        }
                        // Stop playback button
                        Button(
                            onClick = { viewModel.stopPlayAudio() },
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            enabled = playState != PlayState.STOPPED
                        ) {
                            Text("停止")
                        }
                    }
                    // Progress bar
                    Slider(
                        value = progress,
                        onValueChange = { /* 进度调节功能需要额外实现 */ },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    )

                    Text("进度: ${formatTime(currentPosition)} / ${formatTime(duration)}")

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            // System Audio recorder
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text("系统音频录音")
                Row(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                ) {
                    Button(
                        onClick = { viewModel.controlSystemAudioRecord(true) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        enabled = !systemAudioRecording
                    ) {
                        Text("开始录音")
                    }
                    Button(
                        onClick = { viewModel.controlSystemAudioRecord(false) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        enabled = systemAudioRecording
                    ) {
                        Text("停止录音")
                    }

                }
            }
        }
    }
}

// Format time display (milliseconds to mm:ss format)
@SuppressLint("DefaultLocale")
fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CXRMSamplesTheme {
        AudioUsageScreen(viewModel = viewModel { AudioUsageViewModel() })
    }
}