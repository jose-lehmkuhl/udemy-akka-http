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

//  Http().bindAndHandle(extractRequestRoute, "localhost", 8080)

  val simpleNestedRoute =
    path("api" / "item") {
      get {
        complete(StatusCodes.OK)
      }
    }

  val compactSimpleNestedRoute = (path("api" / "item") & get) {
    complete(StatusCodes.OK)
  }

  val compactExtractRequestRoute =
    (path("controlEndpoint") & extractRequest & extractLog) { (request, log) =>
      log.info(s"I got the http request: $request")
      complete(StatusCodes.OK)
    }

  val repeatedRoute =
    path("about") {
      complete(StatusCodes.OK)
    } ~
      path("aboutUs") {
        complete(StatusCodes.OK)
      }

  val dryRoute =
    (path("about") | path("aboutUs")) {
      complete(StatusCodes.OK)
    }

  val blogByIdRoute =
    path(IntNumber) { id: Int =>
      complete(StatusCodes.OK)
    }

  val blogByQueryParamRoute =
    parameter('postId.as[Int]) { id: Int =>
      complete(StatusCodes.OK)
    }

  val combinedBlogByIdRoute =
    (path(IntNumber) | parameter('postId.as[Int])) { id: Int =>
      complete(StatusCodes.OK)
    }

  val completeOkRoute = complete(StatusCodes.OK)

  val failWithRoute =
    path("notSupported") {
      failWith(new RuntimeException("Unsupported!"))
    }

  val routeWithRejection =
    path("home") {
      reject
    } ~
      path("index") {
        completeOkRoute
      }

  val getOrPutPath =
    path("api" / "myEndpoint") {
      get {
        completeOkRoute
      } ~
        post {
          complete(StatusCodes.Forbidden)
        }
    }
}
