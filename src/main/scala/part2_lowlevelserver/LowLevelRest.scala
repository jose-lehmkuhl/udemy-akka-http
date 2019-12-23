package part2_lowlevelserver

import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCodes,
  Uri
}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part2_lowlevelserver.GuitarDB.{
  CreateGuitar,
  FindAllGuitars,
  GuitarCreated
}
import spray.json._
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.concurrent.duration._

case class Guitar(make: String, model: String)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
}
class GuitarDB extends Actor with ActorLogging {
  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("Searching for all Guitars")
      sender() ! guitars.values.toList

    case FindGuitar(id) =>
      log.info(s"search guitar by id $id")
      sender() ! guitars.get(id)

    case CreateGuitar(guitar) =>
      log.info(s"adding guitar $guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
  }
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat = jsonFormat2(Guitar)
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {

  implicit val system = ActorSystem("LowLevelRest")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster"
      |}
      |""".stripMargin
  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )

  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }
  implicit val defaultTimeout = Timeout(2 seconds)

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/api/guitar"), _, _, _) =>
      val guitarsFuture: Future[List[Guitar]] =
        (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
      guitarsFuture.map { guitars =>
        HttpResponse(
          entity = HttpEntity(
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          )
        )
      }

    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture: Future[GuitarCreated] =
          (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }

    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)
}
