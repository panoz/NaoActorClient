package robots.common

/**
 *  This determines the interpretation of the pictures bytes.
 *  When RGB is chosen, then it will be compressed as JPEG.
 *  Otherwise raw bytes are sent.
 */
object ColorSpace extends Enumeration {
  type ColorSpace = Value
  type Type = Value
  val JPEG, BGR, YUV422, YUV, Yuv, HSY = Value
}