package ib.execution

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import ib.execution.IBExecutionActor.PositionRequest

class IBExecutionActor(orderActor: ActorRef, executionDataSource: ActorRef) extends Actor{

  val log = Logging.getLogger(context.system, this)

  val orderRefPattern = """(\w+)\.\d+""".r

  var books: Map[String, ActorRef] = Map.empty

  def receive = {
    case e:IBExecutionEvent => handleExecution(e)
    case pr: PositionRequest => books.get(pr.service).foreach( _ forward pr)
  }

  def handleExecution(e:IBExecutionEvent) = {
    log.info(s"execution $e")
    parseOrderRef(e.execution.orderRef) match{
      case Some(service) => routeToBook(service, e)
      case None => log.error(s"unable to parse orderRef ${e.execution.orderRef}")
    }
  }

  def routeToBook(service: String, e:IBExecutionEvent) = {
    log.info(s"routing execution $e to $service")
    getExecutionBook(service) ! e
  }

  def getExecutionBook(service: String) = books.get(service) match{
    case Some(book) => book
    case None =>
      val book = context.actorOf(Props(new IBExecutionBookActor(service, executionDataSource)))
      books += service -> book
      book
  }

  def parseOrderRef(orderRef: String) = orderRef match{
    case orderRefPattern(or) => Some(or)
    case _ => None
  }

}

object IBExecutionActor{

  def apply(orderActor: ActorRef, executionDataSource: ActorRef) = Props(new IBExecutionActor(orderActor, executionDataSource))

  case class PositionRequest(service: String)
  case class PositionResponse(positions: Seq[IBPosition])

}
