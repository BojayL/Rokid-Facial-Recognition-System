package com.rokid.cxrssdksamples.activities.selfCMD

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rokid.cxrssdksamples.theme.CXRSSDKSamplesTheme

class SelfCMDActivity : ComponentActivity() {
    private val viewModel: SelfCMDViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 设置屏幕常亮
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setContent {
            CXRSSDKSamplesTheme {
                SelfCMDScreen(viewModel = viewModel)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        viewModel.sendCustomCMD(keyCode)
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun SelfCMDScreen(viewModel: SelfCMDViewModel) {
    val sendStatusString by viewModel.sendStatusString.collectAsState()
    val receiveStatusString by viewModel.receiveStatusString.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = sendStatusString, color = Color.Green)
        Text(text = receiveStatusString, color = Color.Green)
    }
}

@Preview(showBackground = true)
@Composable
fun SelfCMDScreenPreview() {
    CXRSSDKSamplesTheme {
        SelfCMDScreen(viewModel = viewModel{ SelfCMDViewModel()})
    }
}