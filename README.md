# EventBus

[![Build Status](https://travis-ci.org/thenetcircle/event-bus.svg?branch=master)](https://travis-ci.org/thenetcircle/event-bus)

The definition of EventBus:

1. A realtime event bus / message bus.
2. A central place to manage / trace / monitor events.
3. A data collecting / distributing platform.

<a href="https://thenetcircle.github.io/event-bus/assets/systems_and_eventbus2.png" target="_blank">![EventBus Workflow](https://thenetcircle.github.io/event-bus/assets/systems_and_eventbus2.png)</a>

Following are some details of the definition:

- First of all, EventBus intend to be a realtime system. It offers low latency, high throughput ways to transfer data from one side to another side.
- Besides to be a realtime system, It encourages unified format of events/messages (defualt using [ActivityStreams 1.0](http://activitystrea.ms/specs/json/1.0/) format), But not mandatory.
- Along with the unified format, all isolated systems could understand what the event means (at least the semantic), a event could be shared between different systems.   
   for example: a user-info-update event, will be used by system-a, system-b, system-c, since they all understand the structure of the event
- Another usage of us is for data collecting.   
   Since all events are go through here. We are collecting interested events on this central point to our data warehouse for data mining and meachine learning. And since the format are unified, ETL could be more easier.
   
For more details can visit the [Documention](https://thenetcircle.github.io/event-bus/), And a [Demo](https://thenetcircle.github.io/event-bus/admin/#/event-bus/admin) of the Admin Interface.

## Features

- Low latency and High throughput.
- Based on [Reactive Streams](http://www.reactive-streams.org/), The whole workflow supports [backpressure](https://github.com/ReactiveX/RxJava/wiki/Backpressure) and various stream operations. 
- Failure tolerance.
- Logic can be easily composed by various **Sources**, **Sinks**, **Transforms**, **Fallbacks**, And they are expendable. 
- Tracking and Monitoring supported.

## Contributing
Feedbacks and pull requests are welcome and appreciative. For major changes, please open an issue first to discuss what you would like to change.

## Change Logs
[Click to check Change Logs](https://thenetcircle.github.io/event-bus/change_logs)