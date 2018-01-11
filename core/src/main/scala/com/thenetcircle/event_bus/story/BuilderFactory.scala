package com.thenetcircle.event_bus.story
import com.thenetcircle.event_bus.interface._
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

class BuilderFactory() {

  private var sourceBuilders: Map[String, ISourceBuilder] = Map.empty
  private var opBuilders: Map[String, IOpBuilder] = Map.empty
  private var sinkBuilers: Map[String, ISinkBuilder] = Map.empty

  def registerBuilder(plotType: String, builder: IBuilder[IPlot]): Unit = {
    builder match {
      case _: ISourceBuilder =>
        sourceBuilders += (plotType.toLowerCase -> builder.asInstanceOf[ISourceBuilder])
      case _: IOpBuilder => opBuilders += (plotType.toLowerCase -> builder.asInstanceOf[IOpBuilder])
      case _: ISinkBuilder =>
        sinkBuilers += (plotType.toLowerCase -> builder.asInstanceOf[ISinkBuilder])
    }
  }

  def getSourceBuilder(sourceType: String): Option[ISourceBuilder] =
    sourceBuilders.get(sourceType.toLowerCase)

  def getOpBuilder(opType: String): Option[IOpBuilder] = opBuilders.get(opType.toLowerCase)

  def getSinkBuilder(sinkType: String): Option[ISinkBuilder] = sinkBuilers.get(sinkType.toLowerCase)

  def buildSource(sourceType: String,
                  configString: String)(implicit context: StoryExecutingContext): Option[ISource] =
    getSourceBuilder(sourceType).map(_builder => _builder.build(configString))

  def buildOp(opType: String,
              configString: String)(implicit context: StoryExecutingContext): Option[IOp] =
    getOpBuilder(opType).map(_builder => _builder.build(configString))

  def buildSink(sinkType: String,
                configString: String)(implicit context: StoryExecutingContext): Option[ISink] =
    getSinkBuilder(sinkType).map(_builder => _builder.build(configString))

}

object BuilderFactory {

  def apply(config: Config): BuilderFactory = {

    val builderFactory = new BuilderFactory()

    config.checkValid(ConfigFactory.defaultReference, "app.supported-builders")

    List("source", "op", "sink").foreach(prefix => {
      config
        .as[List[List[String]]](s"app.supported-builders.$prefix")
        .foreach {
          case plotType :: builderClass :: _ =>
            builderFactory.registerBuilder(
              plotType,
              Class.forName(builderClass).asInstanceOf[Class[IBuilder[IPlot]]].newInstance()
            )
        }
    })

    builderFactory

  }

}
