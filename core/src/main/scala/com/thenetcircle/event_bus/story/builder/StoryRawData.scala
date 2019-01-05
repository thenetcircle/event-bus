package com.thenetcircle.event_bus.story.builder

case class StoryRawData(
    name: String,
    status: String,
    settings: String,
    source: String,
    sink: String,
    transforms: Option[String],
    fallback: Option[String]
)
