package it.pagopa.iso_android

import android.app.Application
import com.google.android.gms.security.ProviderInstaller
import it.pagopa.io.wallet.proximity.ProximityLogger
import javax.net.ssl.SSLContext

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            ProviderInstaller.installIfNeeded(applicationContext)
            val sslContext: SSLContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, null, null)
            sslContext.createSSLEngine()
        }catch (e: Exception){
            ProximityLogger.e("App","Unable to create provider cause: ${e.message}")
        }
    }
}