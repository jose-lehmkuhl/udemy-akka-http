package part3_highlevelserver

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpRequest,
  StatusCodes
}
import akka.stream.ActorMaterializer

object DirectivesBreakdown extends App {

  implicit val system = ActorSystem("DirectivesBreakdown")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  val simpleHttpMethodRoute =
    post {
      complete(StatusCodes.Forbidden)
    }

  val simplePathRoute =
    path("about") {
      complete(
        HttpEntity(
          ContentTypes.`application/json`,
          """
            |<html>
            | <body>
            |   Hello from the about page!
            | </body>
            |</html>
            |""".stripMargin
        )
      )
    }

  val complexPathRoute =
    path("api" / "myEndpoint") {
      complete(StatusCodes.OK)
    }

  val dontConfuse =
    path("api/myEndpoint") {
      complete(StatusCodes.OK)
    }

  val pathEndRoute =
    pathEndOrSingleSlash {
      complete(StatusCodes.OK)
    }

//  Http().bindAndHandle(complexPathRoute, "localhost", 8080)

  val pathExtractionRoute =
    path("api" / "item" / IntNumber) { itemNumber: Int =>
      println(s"Ive got a number in my path $itemNumber")
      complete(StatusCodes.OK)
    }

  val pathMultiextractRoute =
    path("api" / "order" / IntNumber / IntNumber) { (id, inventory) =>
      println(s"I've got 2 numbers in my path: $id, $inventory")
      complete(StatusCodes.OK)
    }

//  Http().bindAndHandle(pathMultiextractRoute, "localhost", 8080)

  val queryParamExtractRoute =
    path("api" / "item") {
      parameter('id.as[Int]) { itemId: Int =>
        println(s"item id = $itemId")
        complete(StatusCodes.OK)
      }
    }

  val extractRequestRoute =
    path("controlEndpoint") {
      extractRequest { httpRequest: HttpRequest =>
        extractLog { log: LoggingAdapter =>
          log.info(s"I got the http request: $httpRequest")
          complete(StatusCodes.OK)
        }
      }
    }

  Http().bindAndHandle(extractRequestRoute, "localhost", 8080)

}
