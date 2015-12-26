package ib.order

import akka.actor.{Props, ActorRef}
import akka.event.Logging
import akka.persistence.{RecoveryCompleted, PersistentActor}
import ib.IBSessionActor.PlaceOrder
import ib.order.IBOrderActor.{IBOrderRequest, IBOrderStatus}
import ib.order.IBOrderExecutionActor.{IBOrderStatusEvent, RequestSent, RequestSend}
import k2.SubscribableDataSource.PublishableEvent

class IBOrderExecutionActor(request: IBOrderRequest, dataSource: ActorRef, sessionActor: ActorRef) extends PersistentActor{

  val log = Logging.getLogger(context.system, this)

  override def persistenceId = s"ib-order-${request.orderId}"

  var orderStatus: Option[IBOrderStatus] = None

  var requestSent = false

  def receiveCommand = {
    case RequestSend => persist(RequestSent){ _ =>
      if(!requestSent){
        requestSent = true
        sessionActor ! PlaceOrder(request.orderId,request.serviceName,request.contract, request.order)
      }
    }

    case status: IBOrderStatus => if(!orderStatus.contains(status)){
      persist(status){state =>
        updateState(status)
        dataSource ! PublishableEvent(topic(status), IBOrderStatusEvent(request.correlationId,status) )
      }
    }
  }

  def receiveRecover = {
    case RecoveryCompleted =>
      orderStatus.foreach(status => dataSource ! PublishableEvent(topic(status), IBOrderStatusEvent(request.correlationId,status) ))

    case status: IBOrderStatus => updateState(status)

    case RequestSent => requestSent = true
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
