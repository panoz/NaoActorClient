package robots.naoactorclient

import android.util.Log

class InputAxis(val name: String, val min: Double = -1.0d, val max: Double = 1.0d) {
  val TAG = "InputAxis"
  private var _value = 0.0d
  def value_=(v: Double) {
    /*if (v>=min && v<=max)*/ _value = v
    Log.v(TAG, name + "=" + v)
  }
  def value = _value
}