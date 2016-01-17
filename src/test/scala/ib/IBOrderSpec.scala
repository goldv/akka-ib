package ib

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import ib.IBSessionActor.PlaceOrder
import ib.order.IBOrderActor.{IBOrderRequest, IBOrderStatus}
import ib.order.IBOrderExecutionActor.IBOrderStatusEvent
import ib.order.{IBOrder, IBOrderActor}
import k2.SubscribableDataSource.PublishableEvent
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class IBOrderSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this( ActorSystem("IBOrderSpec") )

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "order executor" must{
    "send order to session actor" in{
      val session = TestProbe()
      val source = TestProbe()

      val orderActor = system.actorOf(Props(new IBOrderActor(session.ref, source.ref)))

      orderActor ! IBOrderRequest("TEST", "service1", 0,IBContract().withConId(0), IBOrder().withTotalQuantity(10))

      session.expectMsg(PlaceOrder(0, "service1",IBContract().withConId(0), IBOrder().withTotalQuantity(10)))
    }
    "publish status to source" in{
      val session = TestProbe()
      val source = TestProbe()

      val orderActor = system.actorOf(Props(new IBOrderActor(session.ref, source.ref)))

      orderActor ! IBOrderRequest("TEST","service1", 1, IBContract().withConId(0), IBOrder().withTotalQuantity(10))
      session.expectMsg(PlaceOrder(1,"service1", IBContract().withConId(0), IBOrder().withTotalQuantity(10)))

      val status = IBOrderStatus(1, "TEST", 0, 5, 10, 0, 0, 1.0, 1, "")
      orderActor ! status

      source.expectMsg(PublishableEvent("order-status/service1/1", IBOrderStatusEvent("TEST", status)))

      val status1 = IBOrderStatus(1, "Filled", 0, 5, 10, 0, 0, 1.0, 1, "")
      orderActor ! status1

      source.expectMsg(PublishableEvent("order-status/service1/1", IBOrderStatusEvent("TEST", status1)))
    }
    "filter duplicate status" in{
      val session = TestProbe()
      val source = TestProbe()

      println(s"source: ${source.ref}")

      val orderActor = system.actorOf(Props(new IBOrderActor(session.ref, source.ref)))

      val status = IBOrderStatus(0, "TEST", 0, 5, 10, 0, 0, 1.0, 1, "")
      orderActor ! status

      source.expectMsg(PublishableEvent("order-status/service1/0", IBOrderStatusEvent("TEST", status)))

      orderActor ! status

      source.expectNoMsg()
    }
  }
}
