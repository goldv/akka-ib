package ib

import java.time.LocalDateTime

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ib.IBSessionActor.PlaceOrder
import ib.execution.{IBExecutionEvent, IBExecution}
import ib.order.IBOrderActor.{IBOrderServiceMappingResponse, IBOrderServiceMappingRequest, IBOrderRequest, IBOrderStatus}
import ib.order.IBOrderExecutionActor.IBOrderStatusEvent
import ib.order.{IBOrder, IBOrderActor}
import k2.SubscribableDataSource.PublishableEvent

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

      orderActor ! IBOrderRequest("TEST","service1", 0, IBContract().withConId(0), IBOrder().withTotalQuantity(10))
      session.expectMsg(PlaceOrder(0,"service1", IBContract().withConId(0), IBOrder().withTotalQuantity(10)))

      val status = IBOrderStatus(0, "TEST", 0, 5, 10, 0, 0, 1.0, 1, "")
      orderActor ! status

      source.expectMsg(PublishableEvent("order-status/service1/0", IBOrderStatusEvent("TEST", status)))

      orderActor ! status

      source.expectNoMsg()
    }
    "retrieve service mapping" in{
      val session = TestProbe()
      val source = TestProbe()

      val orderActor = system.actorOf(Props(new IBOrderActor(session.ref, source.ref)))

      orderActor ! IBOrderRequest("TEST", "service1", 3,IBContract().withConId(0), IBOrder().withTotalQuantity(10))

      session.expectMsg(PlaceOrder(3, "service1",IBContract().withConId(0), IBOrder().withTotalQuantity(10)))

      val e1 = IBExecutionEvent(0,null,IBExecution(3, 0,"",LocalDateTime.now(),"","",0,0,0,0))
      orderActor ! IBOrderServiceMappingRequest(e1)

      expectMsg(IBOrderServiceMappingResponse(e1, "service1"))

      val e2 = IBExecutionEvent(0,null,IBExecution(10, 0,"",LocalDateTime.now(),"","",0,0,0,0))
      orderActor ! IBOrderServiceMappingRequest(e2)

      expectNoMsg()

    }
  }
}
