package robots.naoactorclient.localactors

import akka.actor.Actor
import akka.actor.ActorRef
import robots.common.RobotRequest
import scala.util.Success
import scala.util.Failure
import android.util.Log
import akka.actor.actorRef2Scala
import robots.naoactorclient.Actors
import robots.naoactorclient.MainActivity

class NoReplyActor extends Actor with LocalActor {
  val TAG = "NoReplyActor"
  def receive = {
    case rem: ActorRef ⇒ remoteActor = rem

    case SetResolution(res) => {
      remoteActor ! RobotRequest("VideoDevice", "setResolution", res)
      Log.i(TAG, "Set resolution to " + res)
    }
    case 'SetHeadStiffnessOn ⇒ setHeadStiffness(1.0)
    case 'SetHeadStiffnessOff ⇒ setHeadStiffness(0.0)
    case 'SetBodyStiffnessOff ⇒ {
      remoteActor ! RobotRequest("Motion", "setStiffnesses", "Body", 0.0d)
      Log.i(TAG, "Set body stiffness to 0.0")
    }
    case SetHeadAngles(yaw, pitch, speed) => {
      remoteActor ! RobotRequest("Motion", "setAngles", List("HeadYaw", "HeadPitch"), List(yaw, pitch), speed)
      Log.d(TAG, "Motion setAngles HeadYaw/HeadPitch " + yaw + "/" + pitch)
    }
    case SetWalkVelocity(x, y, rotate, frequency) => {
      remoteActor ! RobotRequest("Motion", "setWalkTargetVelocity", y, x, rotate, frequency)
      Log.d("InputControl", "Motion setWalkTargetVelocity y / x / rotate " + y + "/" + x + "/" + rotate)
    }
    case Say(text) => {
      remoteActor ! RobotRequest("TextToSpeech", "say", text)
      Log.i(TAG, "TextToSpeech say " + text)
    }
    case SetLanguage(lang) => {
      remoteActor ! RobotRequest("TextToSpeech", "setLanguage", lang)
      Log.i(TAG, "Set TextToSpeech language to " + lang)
    }
    case res: Success[_] => res.get match {
      case som: Some[_] => Log.e(TAG, som.get.toString)
      case None =>
    }
    case f: Failure[_] => Log.e(TAG, f.failed.toString)
    case _ =>
  }
  private def setHeadStiffness(s: Double) {
    remoteActor ! RobotRequest("Motion", "setStiffnesses", "Head", s)
    Log.i(TAG, "Set head stiffness to " + s)
    Thread.sleep(500)
    context.actorSelection("..") ! 'GetHeadStiffness
  }

}