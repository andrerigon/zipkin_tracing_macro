package com.rigon.zipkin

import java.util.concurrent.TimeUnit

import com.twitter.finagle.RequestTimeoutException
import com.twitter.finagle.tracing.Annotation
import com.twitter.finagle.tracing.Trace
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util.Duration
import com.twitter.util.Duration._
import com.twitter.util.Future
import org.apache.log4j.Logger

import scala.annotation.StaticAnnotation
import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

@compileTimeOnly("traced macro")
class traced(name: String, protocol: String = "http", timeout: Duration = Duration(100, TimeUnit.MILLISECONDS)) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro traceMacro.impl
}


object traceMacro {

  def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._
    val nullValue = q"null"

    val list = c.macroApplication.children.head.children.head.children.tail

    val id = list.head
    val protocol = if (list.tail.isEmpty) nullValue else list.tail.head
    val timeout = if (list.size == 3) list.last else nullValue

    val fileName = c.enclosingPosition.source.path
    val shortFileName = fileName.substring(fileName.lastIndexOf("/") + 1).replace(".scala", "")
    val result = {
      annottees.map(_.tree).toList match {
        case q"$mods def $name[..$params](...$paramss): $returnType = $expr" :: Nil =>
          println(s"[$shortFileName] Method $name: $returnType will be traced using protocol "
            + s"${if (protocol.equals(nullValue)) Tracing.defaultProtocol else protocol} with timeout "
            + s"${if (timeout.equals(nullValue)) Tracing.defaultTimeout else timeout}")
          q"""
             $mods def $name[..$params](...$paramss): $returnType = {
              import com.rigon.zipkin.Tracing.withTrace
              withTrace($id, $protocol, $timeout) {
                $expr
              }
            }
          """
      }
    }
    c.Expr[Any](result)
  }


}

object Tracing {

  private val traceLog = Logger.getLogger(getClass)

  val defaultTimeout = Duration(100, TimeUnit.MILLISECONDS)
  val defaultProtocol = "http"

  def withTrace[T](id: String, suppliedProtocol: String = "custom", suppliedTimeout: Duration = fromSeconds(1))(block: => Future[T]) = {
    val protocol = if (suppliedProtocol == null) defaultProtocol else suppliedProtocol
    val timeout = if (suppliedTimeout == null) defaultTimeout else suppliedTimeout
    Trace.traceService(id, protocol, Option.empty) {
      Trace.record(Annotation.ClientSend())
      val time = System.currentTimeMillis()
      withTimeout(id, timeout, block) map { res =>
        traceLog.info(s"$id API took ${System.currentTimeMillis() - time}ms to respond")
        Trace.record(Annotation.ClientRecv())
        res
      }
    }
  }

  private def withTimeout[T](id: String = s"${getClass.getSimpleName}", duration: Duration, block: => Future[T]): Future[T] = {
    block.within(
      DefaultTimer.twitter,
      duration, {
        Trace.record(s"$id.timeout")
        new RequestTimeoutException(duration, s"Timeout exceed while accessing using $id")
      }
    )
  }
}