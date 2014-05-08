package robots.common

/**
 *  QQVGA =  160x120 px
 *  QVGA  =  320x240 px
 *  VGA   =  640x480 px
 *  VGA4  = 1280x960 px
 */
object Resolution extends Enumeration {
  type Resolution = Value
  type Type = Value
  val QQVGA, QVGA, VGA, VGA4 = Value
}