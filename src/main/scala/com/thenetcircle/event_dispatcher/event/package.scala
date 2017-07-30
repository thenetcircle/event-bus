package com.thenetcircle.event_dispatcher

import akka.util.ByteString

package object event {
  type EventBody = ByteString
  type EventContext = Map[String, Any]
}
