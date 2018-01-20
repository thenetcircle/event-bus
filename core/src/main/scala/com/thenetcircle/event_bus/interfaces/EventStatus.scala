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

package com.thenetcircle.event_bus.interfaces

sealed trait EventStatus

object EventStatus {

  sealed trait Succ extends EventStatus

  sealed trait Norm extends Succ
  case object Norm extends Norm

  case class ToFB(cause: Option[Throwable] = None) extends Succ

  sealed trait InFB extends Succ
  case object InFB extends InFB

  case class Fail(cause: Throwable) extends EventStatus

}
