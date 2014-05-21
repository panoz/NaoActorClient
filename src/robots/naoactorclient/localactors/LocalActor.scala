package robots.naoactorclient.localactors

import robots.naoactorclient.MainActivity
import akka.actor.ActorRef

trait LocalActor {
  protected var mainActivity: MainActivity = null
  protected var remoteActor: ActorRef = null
}