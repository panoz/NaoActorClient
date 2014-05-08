package robots.naoactorclient;

import akka.actor.Actor
import robots.common.RobotRequest
import scala.util.Success
import scala.util.Failure
import android.util.Log

class GetImageActor extends Actor {
  val TAG = "GetImageActor"
  var mainActivity: MainActivity = null
  def receive = {
    case ma: MainActivity => mainActivity = ma
    case op: RobotRequest ⇒ Actors.remoteActor ! op
    case res: Success[_] => res.get match {
      case som: Some[_] => som.get match {
        case l: Array[_] => {
          Log.d(TAG, "Byte Array received, size=" + l.size)
          mainActivity.setImage(l.asInstanceOf[Array[Byte]])
        }
        case _ =>
      }
      case None => Log.d(TAG, "None received")
    }
    case f: Failure[_] => Log.e(TAG, f.failed.toString)
  }

}