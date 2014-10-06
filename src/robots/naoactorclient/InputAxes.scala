package robots.naoactorclient

import Math.PI

object InputAxes {
  object Sensors {
    val azimuth = new InputAxis("Azimuth")
    val pitch = new InputAxis("Pitch", -PI / 4, PI / 4)
    val roll = new InputAxis("Roll", -PI / 4, PI / 4)
  }

  object Touchable {
    object Push {
      val xWalkArea = new InputAxis("xWalkArea")
      val yWalkArea = new InputAxis("yWalkArea")
      val xRotateArea = new InputAxis("xRotateArea")
    }
    object Scroll {
      val xMove = new InputAxis("xMove")
      val yMove = new InputAxis("yMove")
    }
  }
}