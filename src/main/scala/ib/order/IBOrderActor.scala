package ib.order

import akka.actor.{Actor, ActorRef, Terminated}
import akka.event.Logging
import ib.IBContract
import ib.order.IBOrderActor.{IBOrderRequest, IBOrderStatus}
import ib.order.IBOrderExecutionActor.RequestSend

class IBOrderActor(sessionActor: ActorRef, dataSource: ActorRef) extends Actor{

  val log = Logging.getLogger(context.system, this)

  var orderState = OrderBookState(Map.empty, Map.empty)

  var reconciledOpenOrders: Set[Int] = Set.empty

  def receive: Actor.Receive = {
    case status:IBOrderStatus =>
      log.info(s"status $status")
      orderState.getExecutor(status.orderId).foreach(_ ! status)

    case request:IBOrderRequest => handleOrderRequest(request)

    case Terminated(executionActor) =>
      orderState = orderState.removeExecutor(executionActor)
  }

  def handleOrderRequest(request:IBOrderRequest) = {
    orderState = updateState(orderState, request)
  }

  def updateState(state: OrderBookState, request: IBOrderRequest): OrderBookState = {
    val executionActor = context.actorOf(IBOrderExecutionActor(request, dataSource, sessionActor))
    context.watch(executionActor)
    executionActor ! RequestSend
    orderState.add(request.orderId, executionActor -> request)
  }
}

object IBOrderActor{
  case class IBOrderRequest(correlationId: String, serviceName: String, orderId: Int, contract:IBContract, order: IBOrder)
  case class IBOrderStatus(orderId: Int, status: String, filled: Int, remaining: Int, avgFillPrice: Double, permId: Int, parentId: Int, lastFillPrice: Double, clientId: Int, whyHeld: String)
  case class IBOpenOrder(orderId: Int, contract: IBContract, order: IBOrder, status: String)
}

case class OrderBookState(orders: Map[Int,(ActorRef,IBOrderRequest)], ordersReverse: Map[ActorRef, Int]){
  def add(orderId: Int, actor: (ActorRef,IBOrderRequest)) = OrderBookState(orders + (orderId -> actor), ordersReverse + (actor._1 -> orderId) )
  def removeExecutor(actor: ActorRef) = {
    (for{
      orderId <- ordersReverse.get(actor)
    } yield OrderBookState(orders - orderId, ordersReverse - actor)) getOrElse this
  }
  def getExecutor(orderId: Int) = orders.get(orderId).map(_._1)

  def getRequests = orders.values.map(_._2).toSet

  def getOrderIds = orders.keySet

  def getServiceName(orderId: Int) = orders.get(orderId).map(_._2.serviceName)
}

object OrderBookState{
  def empty = OrderBookState(Map.empty, Map.empty)
}