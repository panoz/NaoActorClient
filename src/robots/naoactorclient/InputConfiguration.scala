package robots.naoactorclient

import NaoAxes._
import InputAxes._
import DeviceMode._

object InputConfiguration {
  val config_table = Map(
    moveX -> -Touchable.Push.xWalkArea,
    moveY -> -Touchable.Push.yWalkArea,
    rotate -> -Touchable.Push.xRotateArea,
    headPitch -> -Touchable.Scroll.yMove,
    headYaw -> Touchable.Scroll.xMove)

  val config_handheld = Map(
    moveX -> -Touchable.Push.xWalkArea,
    moveY -> -Touchable.Push.yWalkArea,
    rotate -> -Touchable.Push.xRotateArea,
    headPitch -> Sensors.pitch,
    headYaw -> -Sensors.azimuth)

  def currentConfig(deviceMode: DeviceMode) = deviceMode match {
    case Handheld => config_handheld
    case Table => config_table
  }
}
