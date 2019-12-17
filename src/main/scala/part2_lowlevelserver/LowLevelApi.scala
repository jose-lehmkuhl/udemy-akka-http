package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCode,
  StatusCodes,
  Uri
}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._
object LowLevelApi extends App {

  implicit val system = ActorSystem("LowLevelServerAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val serverSource = Http().bind("localhost", 8000)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection form: ${connection.remoteAddress}")
  }

  val serverBindingFuture = serverSource.to(connectionSink).run()
  serverBindingFuture.onComplete {
    case Success(binding) =>
      println("Server binding successful")
      binding.terminate(2 seconds)
    case Failure(ex) => println(s"server binding failure: $ex")
  }

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
  val httpSinkConnectionHandler = Sink.foreach[IncomingConnection] {
    connection =>
      connection.handleWithSyncHandler(requestHandler)
  }

  //  Http().bind("localhost", 8080).runWith(httpSinkConnectionHandler)
  //  Http().bindAndHandleSync(requestHandler, "localhost", 8080)

  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      Future(
        HttpResponse(
          StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   Hello from Akka HTTP!
              | </body>
              |</html>
              |""".stripMargin
          )
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(
        HttpResponse(
          StatusCodes.NotFound,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
                                                                 |<html>
                                                                 | <body>
                                                                 |   oops
                                                                 | </body>
                                                                 |</html>
                                                                 |""".stripMargin
          )
        )
      )
  }

  val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection] {
    connection =>
      connection.handleWithAsyncHandler(asyncRequestHandler)
  }

  //  Http().bind("localhost", 8080).runWith(httpAsyncConnectionHandler)

  //  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8080)

  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] =
    Flow[HttpRequest].map {
      case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
        HttpResponse(
          StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   Hello from Akka HTTP!
              | </body>
              |</html>
              |""".stripMargin
          )
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

  //  Http().bind("localhost", 8082).runForeach { connection =>
  //    connection.handleWith(streamsBasedRequestHandler)
  //  }

  //  Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8082)

  val streamsBasedRequestHandlerExercise: Flow[HttpRequest, HttpResponse, _] =
    Flow[HttpRequest].map {
      case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
        HttpResponse(
          entity =
            HttpEntity(ContentTypes.`text/html(UTF-8)`, "Hello from Akka HTTP!")
        )
      case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
        HttpResponse(
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   <div style="color: red;">
              |    A proper html
              |   </div>
              | </body>
              |</html>
              |""".stripMargin
          )
        )

      case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
        HttpResponse(
          StatusCodes.Found,
          headers = List(Location("http://google.com"))
        )
      case request: HttpRequest =>
        request.discardEntityBytes()

        HttpResponse(
          StatusCodes.NotFound,
          entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "Nops")
        )

    }

  val bindingFuture =
    Http().bindAndHandle(streamsBasedRequestHandlerExercise, "localhost", 8083)

  bindingFuture
    .flatMap(binding => binding.unbind())
    .onComplete(_ => system.terminate())
}
