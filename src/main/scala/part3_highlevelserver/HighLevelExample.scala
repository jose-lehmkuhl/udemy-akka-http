package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout

import scala.concurrent.duration._
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}

import spray.json._

object HighLevelExample extends App with GuitarStoreJsonProtocol {

  implicit val system = ActorSystem("HighLevelExample")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  import GuitarDB._

  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster", 0),
    Guitar("Gibson", "Les Paul", 0),
    Guitar("Martin", "LX1", 0)
  )

  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }

  implicit val timeout = Timeout(2 seconds)

  def toHttpEntity(payload: String) =
    HttpEntity(ContentTypes.`application/json`, payload)

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        parameter('inStock.as[Boolean]) { inStock =>
          complete(
            (guitarDb ? FindAllGuitars)
              .mapTo[List[Guitar]]
              .map(
                _.filter(
                  guitar =>
                    (inStock && guitar.quantity > 0) || (!inStock && guitar.quantity == 0)
                ).toJson.prettyPrint
              )
              .map(toHttpEntity)
          )
        }
      } ~
        (path(IntNumber) | parameter('id.as[Int])) { guitarId =>
          complete(
            (guitarDb ? FindGuitar(guitarId))
              .mapTo[Option[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        } ~
        pathEndOrSingleSlash {
          complete(
            (guitarDb ? FindAllGuitars)
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
    }

  Http().bindAndHandle(simplifiedGuitarServerRoute, "localhost", 8080)
}

//
//  val guitarServerRoute =
//    path("api" / "guitar") {
//      (parameter('id.as[Int]) & get) { guitarId =>
//        val guitarFuture: Future[Option[Guitar]] =
//          (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
//        val entityFuture = guitarFuture.map { guitarOption =>
//          HttpEntity(
//            ContentTypes.`application/json`,
//            guitarOption.toJson.prettyPrint
//          )
//        }
//        complete(entityFuture)
//      } ~
//        get {
//          val guitarsFuture: Future[List[Guitar]] =
//            (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
//          val entityFuture = guitarsFuture.map { guitars =>
//            HttpEntity(
//              ContentTypes.`application/json`,
//              guitars.toJson.prettyPrint
//            )
//          }
//          complete(entityFuture)
//        }
//    } ~
//      (path("api" / "guitar" / IntNumber) & get) { guitarId =>
//        val guitarFuture: Future[Option[Guitar]] =
//          (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
//        val entityFuture = guitarFuture.map { guitarOption =>
//          HttpEntity(
//            ContentTypes.`application/json`,
//            guitarOption.toJson.prettyPrint
//          )
//        }
//        complete(entityFuture)
//      } ~
//      (path("api" / "guitar" / "inventory") & get) {
//        parameter('inStock.as[Boolean]) { inStock =>
//          val guitarsFuture: Future[List[Guitar]] =
//            (guitarDb ? FindAllGuitars)
//              .mapTo[List[Guitar]]
//              .map(
//                _.filter(
//                  guitar =>
//                    (inStock && guitar.quantity > 0) || (!inStock && guitar.quantity == 0)
//                )
//              )
//          val entityFuture = guitarsFuture.map { guitars =>
//            HttpEntity(
//              ContentTypes.`application/json`,
//              guitars.toJson.prettyPrint
//            )
//          }
//          complete(entityFuture)
//        }
//      }
