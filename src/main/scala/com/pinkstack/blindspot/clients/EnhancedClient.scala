package com.pinkstack.blindspot.clients

import zio.Config.Secret
import zio.ZLayer
import zio.http.*
import zio.System
import zio.http.ClientSSLConfig.{Default, FromTrustStoreFile}
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object EnhancedClient:
  private def liveClient: ZLayer[Any, Throwable, Client] =
    for
      rawProxyUrl   <- ZLayer.fromZIO(System.env("HTTP_PROXY").map(_.flatMap(URL.decode(_).toOption)))
      proxyUser     <- ZLayer.fromZIO(System.env("HTTP_PROXY_USER"))
      proxyPass     <- ZLayer.fromZIO(System.env("HTTP_PROXY_PASS"))
      proxyCertPath <- ZLayer.fromZIO(System.env("HTTP_PROXY_CERT_PATH"))
      client        <-
        rawProxyUrl.get match
          case Some(proxyUrl) =>
            val proxyConfig                                             = Proxy(url = proxyUrl)
            val (authenticatedProxy: Proxy, sslConfig: ClientSSLConfig) =
              (proxyUser.get, proxyPass.get, proxyCertPath.get) match
                case (Some(user), Some(pass), None)           =>
                  proxyConfig.credentials(Credentials(user, Secret(pass))) -> ClientSSLConfig.Default
                case (Some(user), Some(pass), Some(certPath)) =>
                  proxyConfig.credentials(Credentials(user, Secret(pass))) -> ClientSSLConfig.FromCertFile(certPath)
                case _                                        =>
                  proxyConfig -> ClientSSLConfig.Default

            println(s"Using proxy: $authenticatedProxy w/ ${sslConfig}")
            Client.default
              .update(_.proxy(authenticatedProxy))
              .update(_.ssl(sslConfig))
          case None           => Client.default
    yield client

  def live = liveClient
