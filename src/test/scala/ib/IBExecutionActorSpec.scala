package ib

import java.time.LocalDateTime

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ib.execution.{IBPosition, IBExecution, IBExecutionEvent, IBExecutionBookActor}
import k2.SubscribableDataSource.PublishableEvent

class IBExecutionActorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this( ActorSystem("IBExecutionActorSpec") )

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val contract1 = IBContract().withConId(1)
  val contract2 = IBContract().withConId(2)
  val contract3 = IBContract().withConId(3)


  "execution book" must{

    "publish position update for single sell execution" in{
      val source = TestProbe()
      val executor = system.actorOf(Props(new IBExecutionBookActor("test1", source.ref)))

      val event = generateExecution(contract1,"SLD", 2, 2, 1.5, 1.5)

      executor ! event

      source.expectMsg(PublishableEvent("position/test1",  IBPosition(contract1, -2, -3, 0)))
    }
    "publish position update for single buy execution" in{
      val source = TestProbe()
      val executor = system.actorOf(Props(new IBExecutionBookActor("test2", source.ref)))

      val event = generateExecution(contract2,"BOT", 2, 2, 1.5, 1.5)

      executor ! event

      source.expectMsg(PublishableEvent("position/test2",  IBPosition(contract2, 2, 3, 0)))
    }

    "publish position update for multiple buy execution" in{
      val source = TestProbe()
      val executor = system.actorOf(Props(new IBExecutionBookActor("test3", source.ref)))

      val event = generateExecution(contract3,"BOT", 2, 2, 1.5, 1.5)
      executor ! event
      source.expectMsg(PublishableEvent("position/test3",  IBPosition(contract3, 2, 3, 0)))

      val event1 = generateExecution(contract3,"BOT", 2, 2, 1.5, 1.5)
      executor ! event1
      source.expectMsg(PublishableEvent("position/test3",  IBPosition(contract3, 4, 6, 0)))

      val event2 = generateExecution(contract3,"SLD", 2, 2, 1.5, 1.5)
      executor ! event2
      source.expectMsg(PublishableEvent("position/test3",  IBPosition(contract3, 2, 3, 0)))
    }
  }


  def generateExecution(contract: IBContract, side: String, cumQty: Int, qty: Int, price: Double, avgPrice: Double) = {
    val execution = IBExecution(1,1,"",LocalDateTime.now(),"",side,price,cumQty,qty,avgPrice)
    IBExecutionEvent(1,contract,execution)
  }

}
