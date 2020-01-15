package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import part2_lowlevelserver.HttpsContext

object HighLevelIntro extends App {

  implicit val system = ActorSystem("HighLevelIntro")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route =
    path("home") {
      complete(StatusCodes.OK)
    }

  val pathGetRoute: Route =
    path("home") {
      get {
        complete(StatusCodes.OK)
      }
    }

  val chainedRoute: Route =
    path("myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
        post {
          complete(StatusCodes.Forbidden)
        }
    } ~
      path("home") {
        complete(
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
          |<html>
          | <body>
          |   Hello from high level akka http
          | </body>
          |</html>
          |""".stripMargin
          )
        )
      }
  Http().bindAndHandle(
    chainedRoute,
    "localhost",
    8080,
    HttpsContext.httpsConnectionContext
  )
}
