package ib.marketdata

import akka.actor.{Actor, ActorRef, Terminated}
import akka.event.{EventBus, Logging}
import com.ib.client.TickType
import ib.IBContract
import ib.IBListener.{IBEvent, IBTickerIdEvent}
import ib.IBSessionActor.{CancelMarketData, RequestMarketData}
import ib.marketdata.IBMarketDataActor._

import scalaz.Scalaz._
import scalaz._

class IBMarketDataActor(sessionActor: ActorRef) extends Actor with CacheUpdates{

  val log = Logging.getLogger(context.system, this)

  val eventBus = new MarketDataEventBus
  val genericTickList = ""

  var tickerId = 0

  var tickerIds = Map.empty[IBContract, Int]
  var priceCache = Map.empty[Int, IBMarketDataPrice]
  var contractCache = Map.empty[Int, IBContract]

  def receive = {
    case IBMarketDataSubscription(contract, subscriber) => handleSubscribe(contract, subscriber)

    case IBMarketDataUnsubscribe(contract, subscriber) => handleUnSubscribe(contract, subscriber)

    case Terminated(subscriber) => handleTerminated(subscriber)

    case tick : IBTickPrice => handleTick(tick)
    case tick : IBTickSize => handleTick(tick)
  }

  def handleTerminated(subscriber: ActorRef) = for{
    contracts <- eventBus.subscriptionsFor(subscriber)
    contract <- contracts
  } handleUnSubscribe(contract, subscriber)

  def handleUnSubscribe(contract: IBContract, subscriber: ActorRef) = {
    log.info(s"UNSUB $contract")
    if(eventBus.unsubscribe(subscriber, contract)){
      tickerIds.get(contract).foreach( sessionActor ! CancelMarketData(_, subscriber))
      tickerIds - contract
    }
  }

  def handleSubscribe(contract: IBContract, subscriber: ActorRef) = {
    log.info(s"SUB $contract")

    context.watch(subscriber)

    if(eventBus.subscribe(subscriber, contract)){
      sessionActor ! RequestMarketData(tickerId, contract, genericTickList, false, List.empty, subscriber )
      tickerIds += contract -> tickerId
      contractCache += tickerId -> contract
      tickerId += 1
    }
  }

  def handleTick(tp: IBTickerIdEvent) = {
    val (cache, price) = tp match {
      case tp: IBTickPrice => updatePriceCachePrice(tp).run((priceCache, contractCache))
      case ts: IBTickSize => updatePriceCacheSize(ts).run((priceCache, contractCache))
    }
    priceCache = cache._1
    eventBus.publish(price)
  }
}

object IBMarketDataActor{
  sealed trait IBSubscription
  case class IBMarketDataSubscription(contract: IBContract, subscriber: ActorRef)
  case class IBMarketDataUnsubscribe(contract: IBContract, subscriber: ActorRef)

  // messages from IB
  case class IBTickPrice(tickerId: Int, field: Int, price: Double, canAutoExecute: Int) extends IBTickerIdEvent
  case class IBTickSize(tickerId: Int, field: Int, size: Int) extends IBTickerIdEvent
  case class IBTickGeneric(tickerId: Int, field: Int, value: Double) extends IBTickerIdEvent
  case class IBTickString(tickerId: Int, field: Int, value: String) extends IBTickerIdEvent

  sealed trait IBMarketDataEvent extends IBEvent
  case class IBMarketDataPrice(contract: IBContract, bid: Double, ask: Double, bidSize: Int, askSize: Int ) extends IBMarketDataEvent
  case class IBMarketDataTrade(contract: IBContract, last: Double)
}

class MarketDataEventBus extends EventBus {
  import scala.collection.mutable.{HashMap, MultiMap, Set}

  type Event = IBMarketDataEvent
  type Classifier = IBContract
  type Subscriber = ActorRef

  val subscribers = new HashMap[IBContract, Set[ActorRef]] with MultiMap[IBContract, ActorRef]
  val subscribersReverse = new HashMap[ActorRef, Set[IBContract]] with MultiMap[ActorRef, IBContract]

  override def subscribe(subscriber: ActorRef, to: IBContract): Boolean = {
    val wasEmpty = !subscribers.get(to).exists(_.nonEmpty)

    subscribers.addBinding(to, subscriber)
    subscribersReverse.addBinding(subscriber, to)

    wasEmpty && subscribers(to).size == 1
  }

  override def publish(event: IBMarketDataEvent): Unit = for{
    subs <- subscribers.get(event.contract)
    sub <- subs
  } sub ! event

  override def unsubscribe(subscriber: ActorRef, from: IBContract): Boolean = {
    subscribers.removeBinding(from, subscriber)
    subscribersReverse.removeBinding(subscriber, from)

    !subscribers.get(from).exists(_.nonEmpty)
  }

  def subscriptionsFor(subscriber: ActorRef) = subscribersReverse.get(subscriber)

  override def unsubscribe(subscriber: ActorRef): Unit = {}
}

trait CacheUpdates{
  import scalaz.State

  type PriceCache = Map[Int,IBMarketDataPrice ]
  type TradeCache = Map[Int, IBMarketDataTrade]
  type ContractCache = Map[Int, IBContract]

  type PriceContractCache = (PriceCache, ContractCache)

  def defaultPrice(c: IBContract) = IBMarketDataPrice(c, Double.NaN, Double.NaN, 0, 0)

  def updatePriceCachePrice(tp: IBTickPrice) = updatePriceCache(tp)(updateTickPrice)

  def updatePriceCacheSize(tp: IBTickSize) = updatePriceCache(tp)(updateTickSize)

  def updatePriceCache[T <: IBTickerIdEvent](event: T)( update: (T, IBMarketDataPrice) => IBMarketDataPrice): State[(PriceCache,ContractCache), IBMarketDataPrice] = for {
    contract <- gets{ cache: PriceContractCache => cache._2(event.tickerId) }
    price <- gets{ cache: PriceContractCache  => update(event, cache._1.getOrElse(event.tickerId, defaultPrice(contract))) }
    _ <- modify{ cache: PriceContractCache => (cache._1.updated(event.tickerId, price), cache._2)}
  } yield price

  def updateTickPrice(tp: IBTickPrice, price: IBMarketDataPrice) = tp.field match{
    case TickType.ASK => price.copy(ask = tp.price)
    case TickType.BID => price.copy(bid = tp.price)
    case _ => price
  }

  def updateTickSize(ts: IBTickSize, price: IBMarketDataPrice) = ts.field match {
    case TickType.ASK_SIZE => price.copy(askSize = ts.size)
    case TickType.BID_SIZE => price.copy(bidSize = ts.size)
    case _ => price
  }
}