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

package com.thenetcircle.event_bus.transporter.entrypoint
import akka.stream.scaladsl.Source
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.EventFormat.DefaultFormat
import com.thenetcircle.event_bus.extractor.Extractor

trait EntryPointSettings

abstract class EntryPoint(
    settings: EntryPointSettings
)(implicit val extractor: Extractor[DefaultFormat]) {

  def port: Source[Source[Event, _], _]

}
