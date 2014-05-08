package robots.serialize
import akka.actor.ActorSystem
import akka.serialization._
import scala.util.Failure
import scala.util.Success
class OptionTrySerializer extends Serializer {
  import OptionTrySerializer.{ system, encode, decode }

  if (system == null) {
    system = ActorSystem("PseudoSystem")
  }

  val serialization = SerializationExtension(system)
  val serializer = serialization.findSerializerFor(classOf[scala.AnyRef])

  // This is whether "fromBinary" requires a "clazz" or not
  def includeManifest: Boolean = false

  // Pick a unique identifier for your Serializer,
  // you've got a couple of billions to choose from,
  // 0 - 16 is reserved by Akka itself
  def identifier = 1234567

  // "toBinary" serializes the given object to an Array of Bytes
  def toBinary(obj: AnyRef): Array[Byte] = {
    val enc = encode(obj).asInstanceOf[AnyRef]
    serializer.toBinary(enc)
  }

  // "fromBinary" deserializes the given array,
  // using the type hint (if any, see "includeManifest" above)
  // into the optionally provided classLoader.
  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = {

    // Put your code that deserializes here
    decode(serializer.fromBinary(bytes)).asInstanceOf[AnyRef]
  }
}

object OptionTrySerializer {
  var system: ActorSystem = null

  def encode(a: Any): Any = {
    a match {
      case s: Some[_] => MySome(encode(s.get))
      case None => MyNone()
      case s: Success[_] => MySuccess(encode(s.get))
      case s: Failure[_] => MyFailure(s.exception)
      case s => s
    }
  }

  def decode(a: Any): Any = {
    a match {
      case s: MySome => Some(decode(s.x))
      case s: MyNone => None
      case s: MySuccess => Success(decode(s.x))
      case s: MyFailure => Failure(s.x)
      case s => s
    }
  }
}

case class MySome(x: Any) extends Serializable
case class MyNone()
case class MySuccess(x: Any)
case class MyFailure(x: Throwable)
