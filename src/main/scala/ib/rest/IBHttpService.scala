package ib.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import ib.execution.IBPosition
import ib.{IBContract, IBSession}
import spray.json._



import scala.concurrent.{Future, ExecutionContextExecutor, ExecutionContext}

case class Test(id: String)

object Protocols extends SprayJsonSupport with DefaultJsonProtocol{

  implicit object SearchRequestJsonFormat extends JsonFormat[IBContract] {
    def read(value: JsValue) = value match {
      case _ =>
        throw new DeserializationException("SearchRequest expected")
    }

    def write(obj: IBContract) = obj.conId match {
      case Some(conid) =>JsObject( "conid" -> JsNumber(conid))
      case None => JsObject( "conid" -> JsNumber(1))
    }
  }

  implicit val ibPositionFormat = jsonFormat4(IBPosition.apply)
  //implicit val tformat = jsonFormat1(Test)

}

trait IBHttpService {

  import Protocols._

  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer


  def session: IBSession

  val routes = logRequestResult("akka-http-microservice") {

    pathPrefix("position") {
      get{
        complete{
          session.getPositions().map(_.toArray)
        }
      }
    }
  }

}

object IBHttpService{
  def apply(_session: IBSession)(implicit _system:ActorSystem, _executor: ExecutionContextExecutor, _materializer: Materializer) = new IBHttpService {
    override def session: IBSession = _session
    override implicit def executor: ExecutionContextExecutor = _executor
    override implicit val materializer: Materializer = _materializer
    override implicit val system: ActorSystem = _system
  }
}
