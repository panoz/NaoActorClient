package robots.naoactorclient;

import akka.actor.Actor
import robots.common.RobotRequest
import scala.util.Success
import scala.util.Failure
import android.util.Log

class GetTextToSpeechLanguagesActor extends Actor {
  val TAG = "GetTextToSpeechLanguagesActor"
  var mainActivity: MainActivity = null
  def receive = {
    case ma: MainActivity => mainActivity = ma
    case op: RobotRequest ⇒ Actors.remoteActor ! op
    case res: Success[_] => res.get match {
      case som: Some[_] => som.get match {
        case l: List[_] => {
          Log.d(TAG, "List received, size=" + l.size)
          mainActivity.setTextToSpeechLanguages(l.map(_.toString).toArray)
        }
        case _ =>
      }
      case None => Log.d(TAG, "None received")
    }
    case f: Failure[_] => Log.e(TAG, f.failed.toString)
  }
}