package part3_highlevelserver

import akka.actor.{ActorSystem, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import spray.json._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class Person(pin: Int, name: String)

trait PersonJsonProtocol extends DefaultJsonProtocol {
  implicit val personJson = jsonFormat2(Person)
}

object HighLevelExercise extends App with PersonJsonProtocol {

  implicit val system = ActorSystem("HighLevelExercise")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  /**
    * Exercise:
    *
    * - GET /api/people: retrieve All the people you have registered
    * - GET /api/people/pin: retrieve the person with that PIN, return as JSON
    * - GET /api/people?pin=X (same)
    * - POST /api/people with a JSON payload denoting a Person, add that person to database
    */
  var people = List(Person(1, "Alice"), Person(2, "Bob"), Person(3, "Charlie"))

  val personServerRoute =
    pathPrefix("api" / "people") {
      get {
        (path(IntNumber) | parameter('pin.as[Int])) { pin =>
          complete(
            HttpEntity(
              ContentTypes.`application/json`,
              people.find(_.pin == pin).toJson.prettyPrint
            )
          )
        } ~
          pathEndOrSingleSlash {
            complete(
              HttpEntity(
                ContentTypes.`application/json`,
                people.toJson.prettyPrint
              )
            )
          }
      } ~ (post & pathEndOrSingleSlash & extractRequest & extractLog) {
        (req, log) =>
          //TODO3
          val entity = req.entity
          val strictEntityFuture = entity.toStrict(2 seconds)
          val personFuture = strictEntityFuture.map(
            _.data.utf8String.parseJson.convertTo[Person]
          )

          onComplete(personFuture) {
            case Success(person) =>
              log.info(s"Got person $person")
              people = people :+ person
              complete(StatusCodes.OK)
            case Failure(ex) =>
              failWith(ex)
          }
//          personFuture.onComplete {
//            case Success(person) =>
//              log.info(s"Got person $person")
//              people = people :+ person
//            case Failure(ex) =>
//              log.warning(s"Failed fatching person from entity: $ex")
//          }
//
//          complete(
//            personFuture
//              .map(_ => StatusCodes.OK)
//              .recover({
//                case _ => StatusCodes.InternalServerError
//              })
//          )
      }
    }

  Http().bindAndHandle(personServerRoute, "localhost", 8080)
}
