package com.sorenson.api_gateway

import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}

import java.io.{FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object BootTlsContext {
  def from(settings: Settings): HttpsConnectionContext = {
    val ks: KeyStore = KeyStore.getInstance("JKS")
    val keystore: InputStream = new FileInputStream(settings.keystorePath.toFile)
    val password: Array[Char] = settings.keystorePassword.toCharArray

    require(keystore != null, "Could not load keystore, keystore is null")
    ks.load(keystore, password)

    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)

    ConnectionContext.httpsServer(() => {
      val engine = sslContext.createSSLEngine()
      engine.setUseClientMode(false)
      engine.setNeedClientAuth(true)
      engine
    })
  }
}
