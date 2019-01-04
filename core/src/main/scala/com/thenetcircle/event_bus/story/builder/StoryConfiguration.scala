package com.thenetcircle.event_bus.story.builder

case class StoryConfiguration(
    name: String,
    status: String,
    settings: String,
    source: String,
    sink: String,
    transforms: Option[String],
    fallback: Option[String]
)
