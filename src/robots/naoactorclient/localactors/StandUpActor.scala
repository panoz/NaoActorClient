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

class StandUpActor extends Actor with LocalActor {
  val TAG = "StandUpActor"
  def receive = {
    case ma: MainActivity ⇒ mainActivity = ma
    case rem: ActorRef ⇒ remoteActor = rem
    case 'StandUp ⇒ {
      mainActivity.goToPostureInProgress = true
      remoteActor ! RobotRequest("RobotPosture", "goToPosture", "Stand", 1.0d)
    }
    case res: Success[_] ⇒ res.get match {
      case som: Some[_] ⇒ som.get match {
        case b: Boolean ⇒ {
          if (b) {
            Log.i(TAG, "Posture 'Stand' reached")
          } else {
            Log.e(TAG, "Moving to posture 'Stand' failed")
          }
          context.actorSelection("..") ! 'GetHeadStiffness
          mainActivity.goToPostureInProgress = false
          mainActivity.hasInitializedPosition = true
        }
        case _ ⇒
      }
      case None ⇒ Log.d(TAG, "None received")
    }
    case f: Failure[_] ⇒ Log.e(TAG, f.failed.toString)
    case _ ⇒
  }
}