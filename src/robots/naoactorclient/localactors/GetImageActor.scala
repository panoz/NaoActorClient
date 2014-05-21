package robots.naoactorclient.localactors

import akka.actor.Actor
import robots.common.RobotRequest
import scala.util.Success
import scala.util.Failure
import android.util.Log
import akka.actor.actorRef2Scala
import robots.naoactorclient.Actors
import robots.naoactorclient.MainActivity
import akka.actor.ActorRef

class GetImageActor extends Actor with LocalActor {
  val TAG = "GetImageActor"
  def receive = {
    case ma: MainActivity ⇒ mainActivity = ma
    case rem: ActorRef ⇒ remoteActor = rem
    case 'GetImage ⇒ remoteActor ! RobotRequest("VideoDevice", "getImage")
    case res: Success[_] ⇒ res.get match {
      case som: Some[_] ⇒ som.get match {
        case l: Array[_] ⇒ {
          Log.d(TAG, "Byte Array received, size=" + l.size)
          mainActivity.setImage(l.asInstanceOf[Array[Byte]])
        }
        case _ ⇒
      }
      case None => Log.e(TAG, "None received")
    }
    case f: Failure[_] => Log.e(TAG, f.failed.toString)
    case _ ⇒
  }

}