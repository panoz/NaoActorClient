package robots.naoactorclient.localactors

import akka.actor.Actor
import robots.common.RobotRequest
import scala.util.Success
import scala.util.Failure
import android.util.Log
import akka.actor.actorRef2Scala
import robots.naoactorclient.MainActivity
import akka.actor.Props
import akka.actor.ActorRef
import android.os.Messenger
import akka.actor.ActorLogging
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ReceiveTimeout

class SupervisorActor extends Actor {
  import context.{ become, unbecome, system, setReceiveTimeout }
  val TAG = "SupervisorActor"
  implicit val CONNECTING_TIMEOUT = new Timeout(2, SECONDS)
  val SHUTDOWN_TIMEOUT = Duration(30, SECONDS)

  private var remoteActor: ActorRef = null

  def notConnected: Receive = {
    case c: Connect => {
      val remActor = context.actorFor(c.address)
      val future = akka.pattern.ask(remActor, None)
      import scala.concurrent.ExecutionContext.Implicits.global
      Await.ready(future, 2 seconds)
      future.value match {
        case s: Some[_] => s.get match {
          case _: Success[_] => {
            remoteActor = remActor
            sender ! Connected
            become(connected, true)
            setReceiveTimeout(SHUTDOWN_TIMEOUT)
          }
          case _: Failure[_] => sender ! new ConnectionFailedException
        }
        case None =>
      }
    }
    case Shutdown => {
      Log.i(TAG, "ActorSystem Shutdown")
      system.shutdown
    }

    case _ => sender ! NotConnectedException
  }

  def connected: Receive = {
    //    case mes: Messenger ⇒ {
    //      replyMessenger = mes
    //    }

    case r: RobotRequest => {
      remoteActor forward r
    }

    case Shutdown => {
      remoteActor ! RobotRequest("RobotPosture", "goToPosture", "Sit", 1.0d)
      Log.i(TAG, "ActorSystem Shutdown")
      system.shutdown
    }

    case ReceiveTimeout => {
      Log.w(TAG, "Timeout received")
      self ! Shutdown
    }

    case _: Connect => sender ! new AlreadyConnectedException

    case _ =>
  }
  def receive = notConnected
}

case class Connect(address: String)
case object Shutdown
case object Connected
case class NotConnectedException extends Exception
case class ConnectionFailedException extends Exception
case class AlreadyConnectedException extends Exception
