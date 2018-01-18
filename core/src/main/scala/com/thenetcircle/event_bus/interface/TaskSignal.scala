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

package com.thenetcircle.event_bus.interface

sealed trait TaskSignal

object TaskSignal {

  sealed trait NoSignal extends TaskSignal
  case object NoSignal extends NoSignal

  sealed trait SkipSignal extends TaskSignal
  case object SkipSignal extends SkipSignal

  sealed trait ToFallbackSignal extends TaskSignal
  case object ToFallbackSignal extends ToFallbackSignal

}
