package ib.rest

import akka.actor.{Actor, Props}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import ib.IBSession
import ib.IBSessionActor.{IBConnected, IBDisconnected, IBError}
import ib.execution.IBPosition
import ib.marketdata.IBMarketDataActor.IBMarketDataPrice
import ib.order.IBOrderExecutionActor.IBOrderStatusEvent

class IBHttpActor(services: Set[String]) extends Actor{

  implicit val ec = context.dispatcher
  implicit val sys = context.system
  implicit val materializer = ActorMaterializer()

  var session: IBSession = _

  def receive = notConnected

  def notConnected: Actor.Receive = {
    case s:IBSession =>
      session = s
      val httpService = IBHttpService(session, services)
      Http().bindAndHandle(httpService.routes, "localhost", 8080)

    case IBConnected =>
      println(s"IB connected")
      context.become(connected)
  }

  def connected: Actor.Receive = {
    case IBDisconnected(reason) =>
      println(s"connection lost due to $reason")
      context.become(notConnected)

    case event:IBMarketDataPrice => //println(s"event: $event")
    case status: IBOrderStatusEvent => println(s"status: $status")
    case position: IBPosition => println(s"position: $position")
    case IBError(reason) => println(s"error: $reason")
  }

}

object IBHttpActor{
  def apply(services: Set[String]) = Props(new IBHttpActor(services))
}
