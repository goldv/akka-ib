package ib.order

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import akka.persistence.RecoveryCompleted
import ib.IBSessionActor.PlaceOrder
import ib.order.IBOrderActor.{IBOrderRequest, IBOrderStatus}
import ib.order.IBOrderExecutionActor.{IBOrderStatusEvent, RequestSend, RequestSent}
import k2.SubscribableDataSource.PublishableEvent

class IBOrderExecutionActor(request: IBOrderRequest, dataSource: ActorRef, sessionActor: ActorRef) extends Actor{

  val log = Logging.getLogger(context.system, this)

  var orderStatus: Option[IBOrderStatus] = None

  var requestSent = false

  def receive = {
    case RequestSend => {
      if(!requestSent){
        requestSent = true
        sessionActor ! PlaceOrder(request.orderId,request.serviceName,request.contract, request.order)
      }
    }

    case status: IBOrderStatus => if(!orderStatus.contains(status)){
      updateState(status)
      dataSource ! PublishableEvent(topic(status), IBOrderStatusEvent(request.correlationId,status) )
    }
  }

  def updateState(status: IBOrderStatus) = {
    log.info(s"order id: ${request.orderId} updating status with $status")
    orderStatus = Some(status)

    if(isFinal(status)) {
      log.info(s"order $request reached final state stopping.")
      context.stop(self)
    }
  }

  def isFinal(status: IBOrderStatus) = status.status match{
    case "Filled" | "ApiCancelled" | "Cancelled" | "Inactive" => true
    case _ => false
  }

  def topic(status: IBOrderStatus) = s"order-status/${request.serviceName}/${status.orderId}"
}

object IBOrderExecutionActor{
  sealed trait IBOrderState { def isFinal: Boolean }
  trait NonFinalState extends IBOrderState{ lazy val isFinal = false }
  case object FinalState extends IBOrderState{ lazy val isFinal = true }

  case object Created extends NonFinalState

  case object RequestSend
  case object RequestSent

  case class IBOrderStatusEvent(correlationId: String, status: IBOrderStatus)

  def apply(request: IBOrderRequest, session: ActorRef, source: ActorRef) = Props(new IBOrderExecutionActor(request, session, source))
}
