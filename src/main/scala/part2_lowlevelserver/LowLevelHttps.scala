package part2_lowlevelserver

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCodes
}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object HttpsContext {
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keystoreFile: InputStream =
    getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  val password = "akka-https".toCharArray
  ks.load(keystoreFile, password)

  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(ks, password)

  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(
    keyManagerFactory.getKeyManagers,
    trustManagerFactory.getTrustManagers,
    new SecureRandom
  )

  val httpsConnectionContext: HttpsConnectionContext =
    ConnectionContext.https((sslContext))
}
object LowLevelHttps extends App {

  implicit val system = ActorSystem("LowLevelHttps")
  implicit val materializer = ActorMaterializer()

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity =
          HttpEntity(ContentTypes.`text/html(UTF-8)`, """
                                                        |<html>
                                                        | <body>
                                                        |   Hello from Akka HTTP!
                                                        | </body>
                                                        |</html>
                                                        |""".stripMargin)
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, """
                                                               |<html>
                                                               | <body>
                                                               |   oops
                                                               | </body>
                                                               |</html>
                                                               |""".stripMargin)
      )
  }

  val httpsBinding = Http().bindAndHandleSync(
    requestHandler,
    "localhost",
    8443,
    HttpsContext.httpsConnectionContext
  )
}
