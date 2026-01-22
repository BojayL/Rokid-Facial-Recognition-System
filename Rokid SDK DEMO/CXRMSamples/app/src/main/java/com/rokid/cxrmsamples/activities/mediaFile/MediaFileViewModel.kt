package com.rokid.cxrmsamples.activities.mediaFile

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.SyncStatusCallback
import com.rokid.cxr.client.extend.callbacks.UnsyncNumResultCallback
import com.rokid.cxr.client.extend.callbacks.WifiP2PStatusCallback
import com.rokid.cxr.client.extend.listeners.MediaFilesUpdateListener
import com.rokid.cxr.client.utils.ValueUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Enum representing the connection status states
 * CONNECTED: Device is connected
 * CONNECTING: Connection attempt is in progress
 * DISCONNECTED: Device is not connected
 */
enum class ConnectionStatus{
    CONNECTED,
    CONNECTING,
    DISCONNECTED
}

/**
 * ViewModel for managing media file transfer functionality
 * Handles connection status, media counts, and synchronization operations
 */
class MediaFileViewModel: ViewModel() {

    private val tag = "MediaFileViewModel"
    
    /** State flow representing the current connection status */
    private val _connected: MutableStateFlow<ConnectionStatus> = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    /** Public state flow for observing connection status */
    val connected = _connected.asStateFlow()

    /** State flow for the number of unsynchronized audio files */
    private val _audioNumber: MutableStateFlow<Int> = MutableStateFlow(0)
    /** Public state flow for observing audio file count */
    val audioNumber = _audioNumber.asStateFlow()
    
    /** State flow for the number of unsynchronized picture files */
    private val _pictureNumber: MutableStateFlow<Int> = MutableStateFlow(0)
    /** Public state flow for observing picture file count */
    val pictureNumber = _pictureNumber.asStateFlow()
    
    /** State flow for the number of unsynchronized video files */
    private val _videoNumber: MutableStateFlow<Int> = MutableStateFlow(0)
    /** Public state flow for observing video file count */
    val videoNumber = _videoNumber.asStateFlow()

    /** State flow indicating if synchronization is in progress */
    private val _syncing: MutableStateFlow<Boolean> = MutableStateFlow(false)
    /** Public state flow for observing synchronization status */
    val syncing = _syncing.asStateFlow()

    /** State flow for lightweight UI messages (info/warn) */
    private val _statusMessage: MutableStateFlow<String> = MutableStateFlow("")
    /** Public state flow for observing status messages */
    val statusMessage = _statusMessage.asStateFlow()

    /** Listener for media file updates */
    private val mediaFilesUpdateListener = MediaFilesUpdateListener { getUnsyncNum() }

    init {
        // Optional: preset video parameters
        CxrApi.getInstance().setVideoParams(60, 30, 1920, 1080, 0)
        setMediaFilesUpdateListener()
    }

    /**
     * Called when the ViewModel is cleared to clean up resources
     */
    override fun onCleared() {
        CxrApi.getInstance().setMediaFilesUpdateListener(null)
        super.onCleared()
    }

    /**
     * Sets the media files update listener
     */
    fun setMediaFilesUpdateListener(){
        CxrApi.getInstance().setMediaFilesUpdateListener(mediaFilesUpdateListener)
    }

    /**
     * Callback for handling the result of getting unsynchronized media file counts
     */
    private val unsyncNumResultCallback =
        UnsyncNumResultCallback { status, audioNum, pictureNum, videoNum ->

            when(status){
                ValueUtil.CxrStatus.RESPONSE_SUCCEED -> {
                    Log.i(tag, "get unsync num succeed")
                    _audioNumber.value = audioNum
                    _pictureNumber.value = pictureNum
                    _videoNumber.value = videoNum
                }
                ValueUtil.CxrStatus.RESPONSE_INVALID -> {
                    Log.e(tag, "get unsync num failed: RESPONSE_INVALID")
                }
                ValueUtil.CxrStatus.RESPONSE_TIMEOUT -> {
                    Log.e(tag, "get unsync num failed: RESPONSE_TIMEOUT")
                }
                else -> {
                    Log.e(tag, "get unsync num failed: unknown error")
                }
            }

        }

    /**
     * Callback for monitoring synchronization status
     */
    private val syncStatus = object : SyncStatusCallback{
        /**
         * Called when synchronization starts
         */
        override fun onSyncStart() {
            // Todo when sync start
        }

        /**
         * Called when a single file has been synchronized
         * @param filename The name of the synchronized file
         */
        override fun onSingleFileSynced(filename: String?) {
            // Todo when sync single file
            Log.i(tag, "sync single file, name = $filename")
            getUnsyncNum()
        }

        /**
         * Called when synchronization fails
         */
        override fun onSyncFailed() {
            _syncing.value = false
            Log.e(tag, "sync failed")
        }

        /**
         * Called when synchronization finishes successfully
         */
        override fun onSyncFinished() {
            _syncing.value = false
            Log.i(tag, "sync finished")
            getUnsyncNum()
        }

    }

    /**
     * Initiates connection to the device via WiFi P2P
     */
    fun connect(){
        _connected.value = ConnectionStatus.CONNECTING
        // Handle initWifiP2P immediate return to avoid UI stuck in CONNECTING
        when (CxrApi.getInstance().initWifiP2P( object : WifiP2PStatusCallback {

            /**
             * Called when connection is established
             */
            override fun onConnected() {
                _connected.value = ConnectionStatus.CONNECTED
                _statusMessage.value = "Wi-Fi P2P connected"
            }

            /**
             * Called when device is disconnected
             */
            override fun onDisconnected() {
                disconnect()
//                _connected.value = ConnectionStatus.DISCONNECTED
            }

            /**
             * Called when connection attempt fails
             * @param errorCode The error code indicating failure reason
             */
            override fun onFailed(errorCode: ValueUtil.CxrWifiErrorCode?) {
//                _connected.value = ConnectionStatus.DISCONNECTED
                disconnect()
            }
        })) {
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                _statusMessage.value = "Connecting..."
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                _connected.value = ConnectionStatus.DISCONNECTED
                _statusMessage.value = "Connect request failed"
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                // Glasses busy, inform user and restore UI
                _connected.value = ConnectionStatus.DISCONNECTED
                _statusMessage.value = "Device busy, do not need try agin"
            }
            else -> {
                _connected.value = ConnectionStatus.DISCONNECTED
                _statusMessage.value = "Connect request unknown status"
            }
        }
    }

    /**
     * Disconnects from the device and cleans up the connection
     */
    fun disconnect(){
        CxrApi.getInstance().deinitWifiP2P()
        _connected.value = ConnectionStatus.DISCONNECTED
        _statusMessage.value = "Disconnected"
    }

    /**
     * Retrieves the number of unsynchronized media files
     */
    fun getUnsyncNum(){
        when(CxrApi.getInstance().getUnsyncNum(unsyncNumResultCallback)){
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {// request succeed
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                Log.e(tag, "get unsync num failed")
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                // Glasses busy, inform user and restore UI
                Log.e(tag, "get unsync num failed: REQUEST_WAITING")
            }
            else -> {
                Log.e(tag, "get unsync num unknown status")
            }
        }
    }

    /**
     * Starts synchronization of media files
     * @param mediaType Array of media types to synchronize
     */
    @SuppressLint("SdCardPath")
    fun startSync(mediaType: Array<ValueUtil.CxrMediaType>){

        // Ensure local target directory exists before sync, avoid startSync失败
        val file = File("/sdcard/Download/Rokid/Media/")
        if (!file.exists()){
            file.mkdirs()
        }

        when(CxrApi.getInstance().startSync("/sdcard/Download/Rokid/Media/", mediaType, syncStatus)){
            true -> {
                Log.i(tag, "start sync succeed")
                _syncing.value = true
            }
            false -> {
                Log.e(tag, "start sync failed")
            }
        }

    }

    /**
     * Starts synchronization of a single media file
     * @param filePath Path to the file to synchronize
     * @param mediaType Media type of the file to synchronize
     */
    fun startSyncSingle(filePath: String, mediaType: ValueUtil.CxrMediaType){
        CxrApi.getInstance().syncSingleFile("/sdcard/Download/Rokid/Media/", mediaType,  "/sdcard/Download/Rokid/Media/test.jpg", syncStatus)
    }

    /**
     * Stops the ongoing synchronization process
     */
    fun stopSync(){
        CxrApi.getInstance().stopSync()
        _syncing.value = false
    }

}