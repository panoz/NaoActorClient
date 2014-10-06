package robots.naoactorclient

import android.util.Log

class RobotAxis(val name: String, val min: Double = -1.0d, val max: Double = 1.0d) {
  val TAG = "RobotAxis"
  private var _value = 0.0d
  def value_=(v: Double) {
    _value = UtilsJava.between(min, v, max)
    Log.v(TAG, name + "=" + v)
  }
  def value = {
    _value
  }
}
