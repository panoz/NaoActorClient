package robots.naoactorclient

import akka.util.Timeout
import scala.concurrent.duration._

object Config {
  val LOCAL_ACTOR_SYSTEM = "NaoActorClient"
  val REMOTE_ACTOR_SYSTEM = "NaoActor"
  val REMOTE_ACTOR_PORT = 2552
  val REMOTE_ACTOR_NAME = "nao"
  
  implicit val MESSAGE_TIMEOUT = Timeout(5 seconds)
  val POSTURE_TIMEOUT = Timeout(10 seconds)
  val CONNECTING_TIMEOUT = Duration(2, SECONDS)
  val SUPERVISOR_ISALIVE_TIMEOUT = Timeout(1 seconds)
  val ACTORSYSTEM_SHUTDOWN_TIMEOUT = Duration(15, SECONDS)

  val VIDEO_ENABLED = false
  val VIDEO_RESOLUTION = robots.common.Resolution.QVGA
  val FRAMES_PER_SECOND = 5

  val OLD_NAOQI_VERSION = false // Only for emulator! Don't use this with real robot!

  val SCROLL_SENSITIVITY = 500
  val SENSOR_DELAY = android.hardware.SensorManager.SENSOR_DELAY_NORMAL

  val ROBOT_MOTION_REFRESH_RATE = 500 // in ms
  val HEAD_SPEED = 1.0d
  val WALK_SPEED = 1.0d

  object Keys {
    val NAOACTOR_ADDRESS_PREF_KEY = "naoactor_adress"
    val ACTORREF_STATE_KEY = "Actor"
    val HAS_INITIALIZED_POSITION_STATE_KEY = "HasInitializedPosition"
    val HEAD_STIFFNESS_BUTTON_ENABLED_KEY = "HeadStiffnessButtonEnabled"
  }
}