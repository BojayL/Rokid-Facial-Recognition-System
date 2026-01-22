package com.rokid.cxrssdksamples.activities.selfCMD

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SelfCMDViewModel : ViewModel() {

    private val TAG = "SelfCMDViewModel"

    private val cmdKey = "rk_custom_key"

    private val clientKey = "rk_custom_client"

    private val cxrServiceBridge: CXRServiceBridge = CXRServiceBridge()

    private val _sendStatusString = MutableStateFlow("")
    val sendStatusString = _sendStatusString.asStateFlow()
    private val _receiveStatusString = MutableStateFlow("")
    val receiveStatusString = _receiveStatusString.asStateFlow()

    private val connectionListener = object : CXRServiceBridge.StatusListener {
        override fun onConnected(p0: String?, p1: Int) {
            // 在连接成功后会回调
        }

        override fun onDisconnected() {
            // 应用断开连接回调
        }

        override fun onARTCStatus(p0: Float, p1: Boolean) {
            // 暂时不用
        }

    }

    private val msgCallback = object : CXRServiceBridge.MsgCallback {
        override fun onReceive(name: String?, args: Caps?, bytes: ByteArray?) {
            val received = "name = $name, args = ${args?.let { parseCaps(it) } ?: run { "null" }}"
            Log.i(TAG,received)
            _receiveStatusString.value = received
        }

    }

    init {
        cxrServiceBridge.setStatusListener(connectionListener)
        cxrServiceBridge.subscribe(clientKey, msgCallback)
    }

    /**
     * 发送自定义命令---监听按键
     * @param keyCode 按键码
     * @return 0:成功  -1:失败
     */
    fun sendCustomCMD(keyCode: Int): Int {
        val cap = Caps().apply {
            write("key")
            writeInt32(keyCode)
        }
        val result = cxrServiceBridge.sendMessage(cmdKey, cap)
        if (result == 0) {
            _sendStatusString.value = "发送成功$cmdKey..${cap.at(0).string}..${cap.at(1).int}"
        } else {
            _sendStatusString.value = "发送失败$cmdKey..${cap.at(0).string}..${cap.at(1).int}"
        }

        return result
    }

    private fun parseCaps(caps: Caps): String {
        val strBuilder = StringBuilder("{")
        for (i in 0 until caps.size()) {
            val capsValue = caps.at(i)
            val string = when (capsValue.type()) {
                Caps.Value.TYPE_STRING -> {
                    "string:${capsValue.string}"
                }

                Caps.Value.TYPE_INT32,
                Caps.Value.TYPE_UINT32 -> {
                    "int:${capsValue.int}"
                }

                Caps.Value.TYPE_INT64,
                Caps.Value.TYPE_UINT64 -> {
                    "long:${capsValue.long}"
                }

                Caps.Value.TYPE_FLOAT -> {
                    "float:${capsValue.float}"
                }

                Caps.Value.TYPE_DOUBLE -> {
                    "double:${capsValue.double}"
                }

                Caps.Value.TYPE_OBJECT -> {//Caps 对象
                    parseCaps(capsValue.`object`)
                }

                Caps.Value.TYPE_BINARY -> {
                    capsValue.binary?.let {
                        "binary:${Base64.encode(it.data, it.length)}"
                    } ?: "binary:null"
                }

                else -> {
                    "unknown:null"
                }
            }
            strBuilder.append("${string},")
        }
        if (strBuilder.length > 4) {//如果有值，删除最后一个逗号
            strBuilder.deleteCharAt(strBuilder.length - 1)
        }
        strBuilder.append("}")
        return strBuilder.toString()
    }
}