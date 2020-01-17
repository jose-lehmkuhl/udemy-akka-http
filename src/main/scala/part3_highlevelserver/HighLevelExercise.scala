package part3_highlevelserver

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object HighLevelExercise extends App {

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
  case class Person(pin: Int, name: String)
  var people = List(Person(1, "Alice"), Person(2, "Bob"), Person(3, "Charlie"))
}
