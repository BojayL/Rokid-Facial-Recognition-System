package com.rokid.cxrssdksamples.activities.main

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.rokid.cxrssdksamples.activities.keys.KeysActivity
import com.rokid.cxrssdksamples.activities.selfCMD.SelfCMDActivity

enum class UsageType{
    KEYS,
    SELF_CMD
}
class MainViewModel: ViewModel() {

    fun toUsage(context: Context, type: UsageType){
        when(type){
            UsageType.KEYS -> {
                context.startActivity(Intent(context, KeysActivity::class.java))
            }
            UsageType.SELF_CMD -> {
                context.startActivity(Intent(context, SelfCMDActivity::class.java))
            }
        }
    }
}