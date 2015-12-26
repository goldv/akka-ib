package ib.order

import akka.actor.{Stash, Actor}
import akka.event.Logging
import ib.order.IBOrderIdGenerator.{OrderIdResponse, OrderIdRequest, NextValidOrderId}

class IBOrderIdGenerator extends Actor with Stash {

  val log = Logging.getLogger(context.system, this)

  var nextValidId: Option[Int] = None

  def receive = {
    case NextValidOrderId(id) =>
      log.info(s"initialising next order id with $id")
      nextValidId = Some(id)
      context.become(initialised)
      unstashAll()
    case OrderIdRequest => stash()
  }

  def initialised: Actor.Receive = {
    case NextValidOrderId(id) =>
      log.info(s"re-initialising next order id with $id")
      nextValidId = Some(id)
      context.become(initialised)
    case OrderIdRequest =>
      nextValidId.foreach( sender() ! OrderIdResponse(_) )
      nextValidId = nextValidId.map(_ + 1)
  }

}

object IBOrderIdGenerator{
  case class NextValidOrderId(id: Int
                             )
  case object OrderIdRequest
  case class OrderIdResponse(id: Int)
}
