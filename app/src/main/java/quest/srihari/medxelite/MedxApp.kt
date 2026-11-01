package quest.srihari.medxelite

import android.app.Application

class MedxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Medx.init(this)
    }
}
