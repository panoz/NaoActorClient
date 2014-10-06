package robots.naoactorclient

import Math.PI

object NaoAxes {
  val MIN_PITCH = -38.5d / 180 * PI
  val MAX_PITCH = 29.5d / 180 * PI

  val MIN_YAW = -119.5d / 180 * PI
  val MAX_YAW = 119.5d / 180 * PI

  val moveX = new RobotAxis("moveX")
  val moveY = new RobotAxis("moveY")
  val rotate = new RobotAxis("rotate")
  val headPitch = new RobotAxis("headPitch", MIN_PITCH, MAX_PITCH)
  val headYaw = new RobotAxis("headYaw", MIN_YAW, MAX_YAW)
}
