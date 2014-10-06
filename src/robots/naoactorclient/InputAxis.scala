package robots.naoactorclient

import android.util.Log

class InputAxis(val name: String, val min: Double = -1.0d, val max: Double = 1.0d) {
  val TAG = "InputAxis"
  private var inverseAxis: InputAxis = null
  private var _value = 0.0d
  def value_=(v: Double) {
    _value = v match {
      case _: Double if v > 0 => math.min(v / max, max)
      case _: Double if v < 0 => math.max(-v / min, min)
      case _ => 0.0d
    }
    if (inverseAxis != null) inverseAxis.value = -v
    Log.v(TAG, name + ": raw=" + v + "  stretched=" + value)
  }
  def value = _value
  def unary_- = {
    if (inverseAxis == null) inverseAxis = new InputAxis("-" + name, -max, -min)
    inverseAxis
  }
}