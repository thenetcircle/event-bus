/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Beineng Ma <baineng.ma@gmail.com>
 */

package com.thenetcircle.event_bus.tracing

sealed trait TracingStep
sealed trait TracingBeginningStep extends TracingStep
sealed trait TracingMiddleStep extends TracingStep
sealed trait TracingEndingStep extends TracingStep

object TracingSteps {
  case object NEW_REQUEST extends TracingBeginningStep
  case object EXTRACTED extends TracingMiddleStep
  case object RECEIVER_COMMITTED extends TracingEndingStep
  case object PIPELINE_PUSHING extends TracingMiddleStep
  case object PIPELINE_PUSHED extends TracingMiddleStep
  case object PIPELINE_PULLED extends TracingBeginningStep
  case object PIPELINE_COMMITTED extends TracingEndingStep
}
