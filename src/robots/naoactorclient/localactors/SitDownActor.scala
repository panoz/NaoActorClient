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

class SitDownActor extends Actor with LocalActor {
  val TAG = "SitDownActor"
  val parent = context.actorSelection("..")
  private var keepStiffnessOff = false
  def receive = {
    case ma: MainActivity ⇒ mainActivity = ma
    case rem: ActorRef ⇒ remoteActor = rem
    case 'SitDown ⇒ {
      mainActivity.goToPostureInProgress = true
      remoteActor ! RobotRequest("RobotPosture", "goToPosture", "Sit", 1.0d)
    }
    case 'SitDownStiffnessOff ⇒ {
      keepStiffnessOff = true
      self ! 'SitDown
    }
    case res: Success[_] ⇒ res.get match {
      case som: Some[_] ⇒ som.get match {
        case b: Boolean ⇒ {
          if (b) {
            Log.i(TAG, "Posture 'Sit' reached")
//            remoteActor ! RobotRequest("Motion", "setStiffnesses", "Body", 0.0d)
            context.actorSelection("../noReplyActor") ! 'SetBodyStiffnessOff
            mainActivity.hasInitializedPosition = true
            mainActivity.setHeadStiffnessButtonEnabled(true)
            if (!keepStiffnessOff && mainActivity.headStiffnessButton.isChecked) {
              parent ! 'SetHeadStiffnessOn
            } /*else {
              parent ! 'SetHeadStiffnessOff
            }*/
          } else {
            Log.e(TAG, "Moving to posture 'Sit' failed")
          }
          mainActivity.goToPostureInProgress = false
        }
        case _ ⇒
      }
      case None ⇒ Log.d(TAG, "None received")
    }
    case f: Failure[_] ⇒ Log.e(TAG, f.failed.toString)
    case _ ⇒
  }
}