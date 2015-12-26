package ib.execution

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import ib.order.IBOrderActor.{IBOrderServiceMappingRequest, IBOrderServiceMappingResponse}

class IBExecutionActor(orderActor: ActorRef, executionDataSource: ActorRef) extends Actor{

  val log = Logging.getLogger(context.system, this)

  var books: Map[String, ActorRef] = Map.empty

  def receive = {
    case e:IBExecutionEvent => orderActor ! IBOrderServiceMappingRequest(e)
    case e:IBOrderServiceMappingResponse => handleExecution(e)
  }

  def handleExecution(e:IBOrderServiceMappingResponse) = {
    val book = books.getOrElse(e.service, context.actorOf(Props(new IBExecutionBookActor(e.service, executionDataSource))))
    book ! e.execution
  }

}

object IBExecutionActor{

  def apply(orderActor: ActorRef, executionDataSource: ActorRef) = Props(new IBExecutionActor(orderActor, executionDataSource))

}
