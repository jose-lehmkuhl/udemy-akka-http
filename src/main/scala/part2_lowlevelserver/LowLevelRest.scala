package part2_lowlevelserver

import akka.pattern.ask
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
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
  FindGuitar,
  GuitarCreated,
  UpdateStock
}
import spray.json._
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.concurrent.duration._

case class Guitar(make: String, model: String, quantity: Int)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case class UpdateStock(id: Int, quantity: Int)
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

    case UpdateStock(id, qtd) =>
      log.info(s"search guitar by id $id")
      val guitar = guitars.get(id)
      guitar match {
        case None => sender() ! guitar
        case Some(guitar) =>
          val newGuitar = Guitar(guitar.make, guitar.model, qtd)
          guitars = guitars + (id -> newGuitar)
          sender() ! Option(newGuitar)
      }

    case CreateGuitar(guitar) =>
      log.info(s"adding guitar $guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
  }
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat = jsonFormat3(Guitar)
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {

  implicit val system = ActorSystem("LowLevelRest")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleGuitar = Guitar("Fender", "Stratocaster", 0)
  println(simpleGuitar.toJson.prettyPrint)

  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster",
      |  "quantity": 0
      |}
      |""".stripMargin
  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])

  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster", 0),
    Guitar("Gibson", "Les Paul", 0),
    Guitar("Martin", "LX1", 0)
  )

  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }

  implicit val defaultTimeout = Timeout(2 seconds)

  def getGuitar(query: Query): Future[HttpResponse] = {

    val guitarId = query.get("id").map(_.toInt)

    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) =>
        val guitarFuture = (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
    }
  }

  def getInventory(query: Query): Future[HttpResponse] = {

    val inStock = query.get("inStock").map(_.toBoolean)

    inStock match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(inStock: Boolean) =>
        val guitarsFuture: Future[List[Guitar]] =
          (guitarDb ? FindAllGuitars)
            .mapTo[List[Guitar]]
            .map(
              _.filter(
                guitar =>
                  (inStock && guitar.quantity > 0) || (!inStock && guitar.quantity == 0)
              )
            )
        guitarsFuture.map { guitars =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          )
        }
    }
  }

  def updateInventory(query: Query): Future[HttpResponse] = {

    val guitarId = query.get("id").map(_.toInt)
    val quantity = query.get("quantity").map(_.toInt)

    val responseFuture = for {
      id <- guitarId
      qtd <- quantity
    } yield {
      val guitarFuture = (guitarDb ? UpdateStock(id, qtd)).mapTo[Option[Guitar]]
      guitarFuture.map(_ => HttpResponse(StatusCodes.OK))
    }

    responseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))
  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, uri @ Uri.Path("/api/guitar"), _, _, _) =>
      val query = uri.query()
      if (query.isEmpty) {
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
      } else getGuitar(query)

    case HttpRequest(
        HttpMethods.POST,
        uri @ Uri.Path("/api/guitar/inventory"),
        _,
        _,
        _
        ) =>
      val query = uri.query()
      if (query.isEmpty) {
        Future(HttpResponse(StatusCodes.NotFound))
      } else {
        updateInventory(query)
      }

    case HttpRequest(
        HttpMethods.GET,
        uri @ Uri.Path("/api/guitar/inventory"),
        _,
        _,
        _
        ) =>
      val query = uri.query()
      if (query.isEmpty) {
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
      } else {
        getInventory(query)
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
