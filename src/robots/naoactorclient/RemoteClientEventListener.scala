package robots.naoactorclient

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Actor
import android.util.Log
import akka.remote.RemoteClientError
import akka.remote.RemoteClientConnected
import akka.remote.RemoteClientDisconnected
import akka.remote.RemoteClientStarted
import akka.remote.RemoteClientShutdown

class RemoteClientEventListener(val jobScheduler: ActorRef) extends
Actor with ActorLogging {
def receive: Receive = {
case event: RemoteClientError => Log.e("Event", "RemoteClientError")//do something
case event: RemoteClientConnected => Log.e("Event", "RemoteClientConnected")//do something
case event: RemoteClientDisconnected => Log.e("Event", "RemoteClientDisconnected")//do something
case event: RemoteClientStarted => Log.e("Event", "RemoteClientStarted")//do something
case event: RemoteClientShutdown => Log.e("Event", "RemoteClientShutdown")//do something
//case event: RemoteClientWriteFailed => //do something
}
}