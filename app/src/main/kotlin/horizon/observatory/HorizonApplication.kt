package horizon.observatory

import android.app.Application
import horizon.observatory.core.di.HorizonContainer

class HorizonApplication : Application() {
    lateinit var container: HorizonContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = HorizonContainer(this)
    }
}
