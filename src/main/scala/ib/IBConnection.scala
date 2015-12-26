package ib

import akka.actor.{ActorRef, ActorSystem, Props}
import IBSessionActor.SubscribeSessionEvent

class IBConnection private(sessionActor: ActorRef) {

  def session(handler: ActorRef, name: String) = sessionActor ! SubscribeSessionEvent(handler, name)

}

object IBConnection{

  def apply(host: String, port: Int, clientId: Int = 0)(implicit system: ActorSystem) = {
    val sessionActor = system.actorOf(Props( new IBSessionActor(host, port, clientId)))
    new IBConnection(sessionActor)
  }
}