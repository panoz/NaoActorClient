package robots.naoactorclient

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

object UtilsScala {
  def extractAndExecute(fut: Future[Any])(func: Function1[Any, Unit], funcNone: Function1[Any, Unit] = { case _ => }) = {
    fut.foreach(s1 => s1 match {
      case s2: Success[_] => s2.foreach(o => o match {
        case some: Some[_] => func(some.get)
        case None => funcNone()
      })
      case _ =>
    })
  }
}