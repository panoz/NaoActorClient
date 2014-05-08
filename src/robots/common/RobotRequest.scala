package robots.common

/**
 * This case class defines the message format for robot requests
 */
@SerialVersionUID(666l)
case class RobotRequest(val module:String, val method:String, val params:List[_]) {
  override def toString = "RobotRequest(" + module + "," + method + "," + params + ")"
}

object RobotRequest {
  def unapply(x:Any):Option[(String, String, List[_])] = x match {
    case r:RobotRequest => Some((r.module, r.method, r.params))
    case _ => None
  }
  
  def apply(module:String, method:String, params:Any*) = new RobotRequest(module, method, params.toList)
}
