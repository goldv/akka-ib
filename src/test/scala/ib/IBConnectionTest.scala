package ib

import akka.actor.{Actor, ActorSystem, Props}
import akka.util.Timeout
import ib.IBSessionActor.{IBConnected, IBDisconnected, IBError}
import ib.execution.IBPosition
import ib.marketdata.IBMarketDataActor
import ib.marketdata.IBMarketDataActor.IBMarketDataPrice
import ib.order.IBOrder
import ib.order.IBOrder.{Market, Sell}
import ib.order.IBOrderExecutionActor.IBOrderStatusEvent

import scala.concurrent.duration._

object IBConnectionTest extends App{

  implicit val system = ActorSystem("ib-test")

  val handler = system.actorOf(Props[IBHandler])

  val connection = IBConnection("localhost", 4001)

  connection.session(handler, "service1")

  Thread.sleep(2000)
}


class IBHandler extends Actor{

  var session: IBSession = _

  val contract = IBContract().withConId(12087792).withExchange("IDEALPRO")

  implicit val ec = context.dispatcher
  implicit val timeout = Timeout(3 seconds)

  def receive = notConnected

  def notConnected: Actor.Receive = {
    case IBConnected(s) =>
      println(s"IB connected")
      session = s
      context.become(connected)

      session.subscribeMarketData( contract)
      session.subscribeOrderStatus()
      session.subscribePositionEvents()
      session.sendOrder("order1",contract, IBOrder().withOrderType(Market).withAction(Sell).withTotalQuantity(40002).withLimitPrice(1.07))
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
