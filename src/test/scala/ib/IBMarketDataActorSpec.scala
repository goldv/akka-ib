package ib

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import com.ib.client.TickType
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ib.marketdata.IBMarketDataActor
import IBMarketDataActor._
import ib.IBSessionActor.{CancelMarketData, RequestMarketData}

class IBMarketDataActorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this( ActorSystem("IBMarketDataActorSpec") )

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "market data actor" must{
    "pass first subscription onto session" in{
      val session = TestProbe()
      val mdActor = system.actorOf(Props(new IBMarketDataActor(session.ref)))
      val contract = IBContract().withConId(1)

      val subscriber1 = TestProbe()
      val subscriber2 = TestProbe()

      mdActor ! IBMarketDataSubscription(contract, subscriber1.ref)
      session.expectMsg(RequestMarketData(0, contract, "", false, List.empty, subscriber1.ref) )
      mdActor ! IBMarketDataSubscription(contract, subscriber2.ref)
      session.expectNoMsg()
    }
    "pass last unsubscribe to session" in{
      val session = TestProbe()
      val subscriber1 = TestProbe()
      val subscriber2 = TestProbe()

      val mdActor = system.actorOf(Props(new IBMarketDataActor(session.ref)))
      val contract = IBContract().withConId(1)

      mdActor ! IBMarketDataSubscription(contract, subscriber1.ref)
      session.expectMsg(RequestMarketData(0, contract, "", false, List.empty, subscriber1.ref) )
      mdActor ! IBMarketDataSubscription(contract, subscriber2.ref)
      session.expectNoMsg()

      mdActor ! IBMarketDataUnsubscribe(contract, subscriber1.ref)
      session.expectNoMsg()

      mdActor ! IBMarketDataUnsubscribe(contract, subscriber2.ref)
      session.expectMsg(CancelMarketData(0, subscriber2.ref))
    }
    "receive incremental price updates" in {
      val session = TestProbe()
      val subscriber1 = TestProbe()

      val mdActor = system.actorOf(Props(new IBMarketDataActor(session.ref)))
      val contract = IBContract().withConId(1)

      mdActor ! IBMarketDataSubscription(contract, subscriber1.ref)
      session.expectMsg(RequestMarketData(0, contract, "", false, List.empty, subscriber1.ref) )

      mdActor ! IBTickPrice(0, TickType.BID, 1.0, 0)
      subscriber1.expectMsgType[IBMarketDataPrice]

      mdActor ! IBTickPrice(0, TickType.ASK, 2.0, 0)
      subscriber1.expectMsg(IBMarketDataPrice(contract, 1.0, 2.0, 0, 0))

      mdActor ! IBTickSize(0, TickType.BID_SIZE, 100)
      subscriber1.expectMsg(IBMarketDataPrice(contract, 1.0, 2.0, 100, 0))

      mdActor ! IBTickSize(0, TickType.ASK_SIZE, 200)
      subscriber1.expectMsg(IBMarketDataPrice(contract, 1.0, 2.0, 100, 200))
    }
  }

}
