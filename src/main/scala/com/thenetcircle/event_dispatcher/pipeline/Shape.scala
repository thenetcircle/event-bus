package com.thenetcircle.event_dispatcher.pipeline

import akka.stream.{ Inlet, Outlet, Shape }
import com.thenetcircle.event_dispatcher.Event

import scala.collection.immutable

final case class PipelineInShape[T <: Event](_inlets: Inlet[T]*) extends Shape {
  def this(ports: Int) = this(Vector.tabulate(ports)(i => Inlet[T](s"in$i")))
  def in(n: Int): Inlet[T] = inlets(n)

  override def inlets: immutable.Seq[Inlet[T]] = _inlets.asInstanceOf[immutable.Seq[Inlet[T]]]
  override def outlets: immutable.Seq[Outlet[_]] = immutable.Seq.empty

  override def deepCopy(): PipelineInShape[T] = PipelineInShape(_inlets.map(_.carbonCopy()): _*)
  override def copyFromPorts(inlets: immutable.Seq[Inlet[_]], outlets: immutable.Seq[Outlet[_]]): Shape = {
    require(inlets.nonEmpty, s"PipelineInShape needs at least one inlet")
    require(outlets.isEmpty, s"PipelineInShape dont't need outlet")
    PipelineInShape(inlets: _*)
  }
}

final case class PipelineOutShape[T <: Event](_outlets: Outlet[T]*) extends Shape {
  def this(ports: Int) = this(Vector.tabulate(ports)(i => Outlet[T](s"out$i")))
  def out(n: Int): Outlet[T] = outlets(n)

  override def inlets: immutable.Seq[Inlet[_]] = immutable.Seq.empty
  override def outlets: immutable.Seq[Outlet[T]] = _outlets.asInstanceOf[immutable.Seq[Outlet[T]]]

  override def deepCopy(): PipelineOutShape[T] = PipelineOutShape(_outlets.map(_.carbonCopy()): _*)
  override def copyFromPorts(inlets: immutable.Seq[Inlet[_]], outlets: immutable.Seq[Outlet[_]]): Shape = {
    require(inlets.isEmpty, s"PipelineInShape dont's need inlet")
    require(outlets.nonEmpty, s"PipelineInShape needs at least one outlet")
    PipelineOutShape(outlets: _*)
  }
}
