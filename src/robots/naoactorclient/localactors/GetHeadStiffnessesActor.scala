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

class GetHeadStiffnessesActor extends Actor with LocalActor {
  val TAG = "GetHeadStiffnessesActor"
  def receive = {
    case ma: MainActivity ⇒ mainActivity = ma
    case rem: ActorRef ⇒ remoteActor = rem
    case 'GetHeadStiffness ⇒ remoteActor ! RobotRequest("Motion", "getStiffnesses", "Head")
    case res: Success[_] ⇒ res.get match {
      case som: Some[_] ⇒ som.get match {
        case l: List[_] ⇒ {
          Log.d(TAG, "List received, size=" + l.size)
          mainActivity.setHeadStiffnesses(l.map(_.toString.toDouble))
        }
        case _ =>
      }
      case None ⇒ Log.d(TAG, "None received")
    }
    case f: Failure[_] ⇒ Log.e(TAG, f.failed.toString)
    case _ ⇒
  }
}