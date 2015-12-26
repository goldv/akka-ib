package ib

import java.util.concurrent.Executors

import akka.actor._
import akka.event.Logging
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.ib.client.{EClientSocket, TagValue}
import ib.IBSessionActor._
import ib.execution.IBExecutionActor
import ib.marketdata.IBMarketDataActor
import ib.marketdata.IBMarketDataActor.IBMarketDataSubscription
import ib.order.IBOrderActor.IBOrderRequest
import ib.order.IBOrderIdGenerator.{OrderIdResponse, OrderIdRequest}
import ib.order.{IBOrderIdGenerator, IBOrderActor, IBOrder}
import ib.reference.IBReferenceDataActor
import k2.SubscribableDataSource
import k2.SubscribableDataSource.{PublishableEvent, Subscribe, UnSubscribe}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

class IBSessionActor(host: String, port: Int, clientId: Int) extends Actor with IBSessionHandling{

  val log = Logging.getLogger(context.system, this)

  val RECONNECT_TIMEOUT = 5

  val listener = new IBListener(orderActor, orderIDGenerator, marketDataActor, referenceDataActor, executionActor, self)
  val eClientSocket = new EClientSocket(listener);

  var subscribers: Map[ActorRef, String] = Map.empty
  var subscribersReverse: Map[String, ActorRef] = Map.empty

  var connectionUp = false

  // Execution context for api calls, the whole api is synchronised at method level so single threaded makes sense here.
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  override def preStart = self ! Connect(host, port, clientId)

  def receive = notConnected orElse session

  def notConnected: Actor.Receive = {
    case c:Connect =>
      connect(c) pipeTo self

    case c @ Connected(true,_) =>
      requestExecutions()

      context.become(connected orElse session)
      connectionUp = true
      log.info(s"IB connection established for host: $host port: $port client: $clientId")
      subscribers.foreach{ case (subscriber, name) => subscriber ! IBConnected(createSession(name, subscriber)) }

    case c @ Connected(false, Some(err)) =>
      log.error(s"IB Connection lost $err attempt re-connect in $RECONNECT_TIMEOUT seconds")
      connectionUp = false
      subscribers.keys.foreach(_ ! IBDisconnected(err))
      context.system.scheduler.scheduleOnce(RECONNECT_TIMEOUT seconds, self, Connect(host, port, clientId))
  }

  def connected: Actor.Receive = {
    case c @ Connected(false, Some(err)) =>
      log.error(s"IB Connection lost $err attempt re-connect in $RECONNECT_TIMEOUT seconds")
      subscribers.keys.foreach(_ ! IBDisconnected(err))
      context.system.scheduler.scheduleOnce(RECONNECT_TIMEOUT seconds, self, Connect(host, port, clientId))
      context.become(notConnected orElse session)
      connectionUp = false

    case ConnectionClosed =>
      context.become(notConnected orElse session)
      self ! Connected(false, Some("connection closed"))

    case rmd: RequestMarketData => requestMarketData(rmd)

    case po: PlaceOrder => placeOrder(po)
 }

  def session: Actor.Receive = {
    case s:SubscribeSessionEvent =>
      log.info(s"adding session subscriber ${s.name}")
      updateSubscribers(s)

      context.watch(s.subscriber)
      if(connectionUp){
        s.subscriber ! IBConnected(createSession(s.name,s.subscriber))
      }

    case Terminated(subscriber) =>
      subscribers = subscribers - subscriber
  }

  def updateSubscribers(s:SubscribeSessionEvent) = {
    subscribers = subscribers + (s.subscriber -> s.name)
    subscribersReverse = subscribersReverse + (s.name -> s.subscriber)
  }

  def placeOrder(order: PlaceOrder) = Future{
    log.info(s"sending order: $order")
    eClientSocket.placeOrder(order.orderId, order.contract.toContract, order.order.toOrder)
  }.recover{
    case err =>
      log.error(err, s"error placing order $order")
      errorEventSource ! PublishableEvent(errorTopic(order.service), IBError(s"error placing order $order") )
  }

  def requestMarketData(rmd: RequestMarketData) = Future{
    log.info(s"SUB: ${rmd.contract.toContract}")
    eClientSocket.reqMktData(rmd.tickerId, rmd.contract.toContract, rmd.genericTickList, rmd.snapshot, rmd.mktDataOptions.asJava)
  }.recover{
    case err =>
      log.error(err, s"error requesting market data $rmd")
      errorEventSource ! PublishableEvent(errorTopic(subscribers(rmd.subscriber)), IBError(s"error requesting market data $rmd") )
  }

  def connect(connect: Connect) = Future{
    eClientSocket.eConnect(connect.host, connect.port, connect.clientId)
    if(eClientSocket.isConnected) Connected(true, None)
    else Connected(false, Some(s"could not establish IB connection to host: $host port: $port client: $clientId"))
  }.recover{
    case err => Connected(false, Some(err.getMessage))
  }

  def requestExecutions() = Future{
    eClientSocket.reqPositions()
    eClientSocket.reqAccountUpdates(true,"DU15211")
  }.recover{
    case err => log.error(err, "error requesting executions")
  }

  def requestOpenOrders() = Future{
    log.info(s"requesting open orders")
    eClientSocket.reqOpenOrders()
  }.recover{
    case err => log.error(err, s"error requesting open orders")
  }

  def errorMessage(event: Any) = event match{
    case rmd:RequestMarketData => s"market data request failed for ${rmd.contract}"
    case po:PlaceOrder => s"order request failed for ${po.contract} ${po.order}"
  }

  def errorTopic(service: String) = s"error/$service"
}

object IBSessionActor{

  // session level API requests
  case class Initialise(eSocket: EClientSocket)
  case class Connect(host: String, port: Int, clientId: Int)
  case class SubscribeSessionEvent(subscriber: ActorRef, name: String)
  case object ConnectionClosed

  // session level API events
  case class Connected(isConnected: Boolean, reason: Option[String])

  // broadcasted session events
  case class IBConnected(session: IBSession)
  case class IBDisconnected(reason: String)

  case class IBError(reason: String)

  // API level request messages
  case class RequestMarketData(tickerId: Int, contract: IBContract, genericTickList: String, snapshot: Boolean, mktDataOptions: List[TagValue], subscriber: ActorRef)
  case class CancelMarketData(tickerId: Int, subscriber: ActorRef)

  case class PlaceOrder(orderId: Int, service: String, contract: IBContract, order: IBOrder)

  def apply(host: String, port: Int, clientId: Int = 0,sessionDataSource: ActorRef) = Props(new IBSessionActor(host, port, clientId))
}

trait IBSessionHandling{ this: Actor =>

  val marketDataActor = context.actorOf(Props(new IBMarketDataActor(self)))
  val referenceDataActor = context.actorOf(Props(new IBReferenceDataActor(self)))
  val orderDataSource = context.actorOf(Props(new SubscribableDataSource(None)))
  val orderActor = context.actorOf(Props(new IBOrderActor(self, orderDataSource)))
  val orderIDGenerator = context.actorOf(Props[IBOrderIdGenerator])
  val executionDataSource = context.actorOf(Props(new SubscribableDataSource(None)))
  val executionActor = context.actorOf(Props(new IBExecutionActor(orderActor, executionDataSource)))
  val errorEventSource = context.actorOf(Props(new SubscribableDataSource(None)))

  def createSession(name: String, handler: ActorRef): IBSession = new IBSession{
    def subscribeMarketData(contract: IBContract) = marketDataActor ! IBMarketDataSubscription(contract, handler)

    def subscribeOrderStatus() = orderDataSource ! Subscribe(s"order-status/$name", Some(handler))
    def unsubscribeOrderStatus() = orderDataSource ! UnSubscribe(s"order-status/$name", Some(handler))

    def subscribeErrorEvents() = {}

    def subscribePositionEvents() = executionDataSource ! Subscribe(s"position/$name", Some(handler))

    def sendOrder(correlationId: String, contract: IBContract, order: IBOrder)(implicit ec: ExecutionContext, timeout: Timeout ): Future[Int] = for{
      response <- (orderIDGenerator ? OrderIdRequest).mapTo[OrderIdResponse]
    } yield {
      orderActor ! IBOrderRequest(correlationId, name, response.id, contract, order)
      response.id
    }
  }
}

trait IBSession{
  def subscribeMarketData(contract: IBContract): Unit
  def subscribeOrderStatus(): Unit

  def unsubscribeOrderStatus(): Unit
  def subscribeErrorEvents(): Unit

  def subscribePositionEvents(): Unit

  def sendOrder(correlationId: String, contract: IBContract, order: IBOrder)(implicit ec: ExecutionContext, timeout: Timeout ): Future[Int]
}