package com.rokid.cxrmsamples.activities.ttsAndNotification

import androidx.lifecycle.ViewModel
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.utils.ValueUtil

class TTSAndNotificationViewModel: ViewModel() {


    fun sendNotification(iconType: Int, content: String, playTTS: Boolean) {
        when(CxrApi.getInstance().sendGlobalMsgContent(iconType, content, playTTS)){
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                // The notification has been sent successfully.
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                // The notification has failed to be sent.
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                // The notification is waiting for the Glasses to be ready.
            }
            else -> {
                // The notification has failed to be sent.
            }
        }
    }

    fun sendToast(iconType: Int, content: String, playTTS: Boolean) {
        when(CxrApi.getInstance().sendGlobalToastContent(iconType, content, playTTS)){
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                // The toast has been sent successfully.
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                // The toast has failed to be sent.
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                // The toast is waiting for the
            }
            else -> {
                // The toast has failed to be sent.
            }
        }
    }

    fun tts(content: String) {
        when(CxrApi.getInstance().sendGlobalTtsContent(content)){
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                // The TTS has been sent successfully.
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                // The TTS has failed to be sent.
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                // The TTS is waiting for the Glasses to be ready.
            }
            else -> {
                // The TTS has failed to be sent.
            }
        }
    }
}