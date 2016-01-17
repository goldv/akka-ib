package ib.execution

import akka.actor.{ActorRef, Actor}
import akka.event.Logging
import akka.persistence.{RecoveryCompleted, PersistentActor}
import ib.IBContract
import k2.SubscribableDataSource.PublishableEvent

class IBExecutionBookActor(service: String, executionDataSource: ActorRef) extends PersistentActor{

  override def persistenceId = s"execution-book-$service"

  val log = Logging.getLogger(context.system, this)

  var position: Map[IBContract, IBPosition] = Map.empty

  def receiveCommand: Actor.Receive = {
    case e:IBExecutionEvent => persist(e)(handleExecutionEvent)
  }

  def receiveRecover: Actor.Receive = {
    case e:IBExecutionEvent => updatePosition(e)
    case RecoveryCompleted => position.foreach{ case (_, p) => executionDataSource ! PublishableEvent(s"position/$service", p) }
  }

  def handleExecutionEvent(e: IBExecutionEvent) = executionDataSource ! PublishableEvent(topic(e), updatePosition(e))

  def updatePosition(e: IBExecutionEvent) = {
    val positionUpdate = position.get(e.contract).map(_.update(e)).getOrElse(IBPosition(e))
    position = position.updated(e.contract, positionUpdate)
    positionUpdate
  }

  def topic(s: IBExecutionEvent) = s"position/$service"
}

case class IBPosition(contract:IBContract, position: Int, cost: Double, realizedPNL: Double){

  def update(execution: IBExecutionEvent) = {
    val fillQty = IBPosition.getQuantity(execution)
    val closingQty = if(sign(position) != sign(fillQty)) Math.min(Math.abs(position), Math.abs(fillQty)) * sign(fillQty) else 0
    val openingQty = if(sign(position) == sign(fillQty)) fillQty else fillQty - closingQty

    val newPosition = position + fillQty
    val costDelta = if(position != 0) openingQty * execution.execution.price + closingQty * cost / position else openingQty * execution.execution.price
    val realizedPNLDelta = if(position != 0) closingQty * (cost / position - execution.execution.price) else 0

    this.copy(
      position = newPosition,
      cost = cost + costDelta,
      realizedPNL = realizedPNL + realizedPNLDelta
    )
  }

  def sign(qty: Int) = if(qty >= 0) 1 else -1
}

object IBPosition{

  def getQuantity(execution: IBExecutionEvent) = execution.execution.side match{
    case "BOT" => execution.execution.shares
    case "SLD" => -execution.execution.shares
  }

  def apply(e: IBExecutionEvent): IBPosition = IBPosition(e.contract,0, 0, 0).update(e)
}
