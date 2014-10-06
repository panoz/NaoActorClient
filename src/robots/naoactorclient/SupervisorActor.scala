package robots.naoactorclient

import akka.actor.Actor
import robots.common.RobotRequest
import scala.util.Success
import scala.util.Failure
import android.util.Log
import akka.actor.actorRef2Scala
import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.ReceiveTimeout
import scala.util.Try
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import UtilsScala.extractAndExecute
import Config._

class SupervisorActor extends Actor {
  import context.{ become, stop, system, setReceiveTimeout }
  val TAG = "SupervisorActor"

  private var remoteActor: ActorRef = null

  def notConnected: Receive = {
    case c: Connect => {
      val remActor = context.actorFor(c.address)
      Log.i(TAG, "Connecting to " + c.address)
      val future = ask(remActor, None)(new Timeout(CONNECTING_TIMEOUT))
      Await.ready(future, CONNECTING_TIMEOUT)
      future.value match {
        case s: Some[_] => s.get match {
          case _: Success[_] => {
            remoteActor = remActor
            become(connected, true)
            setReceiveTimeout(ACTORSYSTEM_SHUTDOWN_TIMEOUT)
            sender ! Connected
            Log.i(TAG, "Connection established")
          }
          case _: Failure[_] => {
            sender ! Try { throw new ConnectionFailedException }
            Log.w(TAG, "Connection failed")
          }
        }
        case None =>
      }
    }

    case Shutdown => {
      Log.i(TAG, "ActorSystem Shutdown")
      stop(self)
      system.shutdown
    }

    case Status => sender ! NotConnected

    case _ => sender ! Try { throw new NotConnectedException }
  }

  def connected: Receive = {
    case r: RobotRequest => {
      remoteActor forward r
    }

    case Shutdown => {
      val future = ask(remoteActor, RobotRequest("RobotPosture", "goToPosture", "Sit", 1.0d))(POSTURE_TIMEOUT)
      extractAndExecute(future) {
        case true => {
          Log.i(TAG, "Posture 'Sit' reached")
          remoteActor ! RobotRequest("Motion", "setStiffnesses", "Body", 0.0d)
          systemShutdown
        }
        case false =>
          {
            Log.w(TAG, "Moving to posture 'Sit' failed")
            systemShutdown
          }
          future.onFailure {
            case _ => systemShutdown
          }
      }
    }

    case ReceiveTimeout => {
      Log.w(TAG, "Timeout received")
      self ! Shutdown
    }

    case _: Connect => sender ! Try { throw new AlreadyConnectedException }

    case Status => sender ! Connected

    case _ =>
  }
  def receive = notConnected

  private def systemShutdown {
    Log.i(TAG, "ActorSystem Shutdown")
    stop(self)
    system.shutdown
  }
}

case class Connect(address: String)
object Shutdown
object NotConnected
object Connected
object Status
case class NotConnectedException extends Exception
case class ConnectionFailedException extends Exception
case class AlreadyConnectedException extends Exception
