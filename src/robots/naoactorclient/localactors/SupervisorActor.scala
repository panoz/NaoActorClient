package robots.naoactorclient.localactors

import akka.actor.Actor
import robots.common.RobotRequest
import scala.util.Success
import scala.util.Failure
import android.util.Log
import akka.actor.actorRef2Scala
import robots.naoactorclient.Actors
import robots.naoactorclient.MainActivity
import akka.actor.Props
import akka.actor.ActorRef

class SupervisorActor extends Actor with LocalActor {
  val TAG = "SupervisorActor"
  val getHeadStiffnessesActor = context.actorOf(Props[GetHeadStiffnessesActor], "getHeadStiffnessesActor")
  val getImageActor = context.actorOf(Props[GetImageActor], "getImageActor")
  val getTextToSpeechLanguagesActor = context.actorOf(Props[GetTextToSpeechLanguagesActor], "getTextToSpeechLanguagesActor")
  val noReplyActor = context.actorOf(Props[NoReplyActor], "noReplyActor")
  val sitDownActor = context.actorOf(Props[SitDownActor], "sitDownActor")
  val standUpActor = context.actorOf(Props[StandUpActor], "standUpActor")

  def receive = {
    case ma: MainActivity ⇒ {
      mainActivity = ma
      getHeadStiffnessesActor ! ma
      getImageActor ! ma
      getTextToSpeechLanguagesActor ! ma
      sitDownActor ! ma
      standUpActor ! ma
    }
    case rem: ActorRef ⇒ {
      remoteActor = rem
      getHeadStiffnessesActor ! rem
      getImageActor ! rem
      getTextToSpeechLanguagesActor ! rem
      sitDownActor ! rem
      standUpActor ! rem
      noReplyActor ! rem

    }
    case 'GetHeadStiffness ⇒ getHeadStiffnessesActor ! 'GetHeadStiffness
    case 'SetHeadStiffnessOn ⇒ noReplyActor ! 'SetHeadStiffnessOn
    case 'SetHeadStiffnessOff ⇒ noReplyActor ! 'SetHeadStiffnessOff
    case 'GetImage ⇒ getImageActor ! 'GetImage
    case x: SetResolution ⇒ noReplyActor ! x
    case 'GetTextToSpeechLanguages ⇒ getTextToSpeechLanguagesActor ! 'GetTextToSpeechLanguages
    case 'SitDown ⇒ sitDownActor ! 'SitDown
    case 'StandUp ⇒ standUpActor ! 'StandUp
    case x: SetHeadAngles => noReplyActor ! x
    case x: SetWalkVelocity => noReplyActor ! x
    case x: Say => noReplyActor ! x
    case x: SetLanguage => noReplyActor ! x
    case _ ⇒
  }
  def shutdown {
    sitDownActor ! 'SitDown
  }
}

case class SetResolution(resolution: robots.common.Resolution.Value)
case class SetHeadAngles(yaw: Double, pitch: Double, speed: Double)
case class SetWalkVelocity(x: Double, y: Double, rotate: Double, frequency: Double)
case class Say(text: String)
case class SetLanguage(lang: String)