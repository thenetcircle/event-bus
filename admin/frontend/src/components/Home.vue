<template>

  <div class="container box content">

    <h2 class="subtitle">The definition of EventBus:</h2>

    <ul>
      <li>A real-time event bus / message bus.</li>
      <li>A central place to manage / trace / monitor events.</li>
      <li>A data collecting / distributing platform.</li>
    </ul>

    <img alt="EventBus Workflow" src="https://thenetcircle.github.io/event-bus/assets/systems_and_eventbus2.png">

    <h2 class="subtitle">Following are some details of the definition:</h2>

    <ul>
      <li>First of all, EventBus intend to be a real-time system. It offers low latency, high throughput ways to transfer data from one side to another side.</li>
      <li>Besides to be a real-time system, It encourages unified format of events/messages (default using ActivityStreams 1.0 format), But not mandatory.</li>
      <li>Along with the unified format, all isolated systems could understand what the event means (at least the semantic), a event could be shared between different systems.<br/>
for example: a user-info-update event, will be used by system-a, system-b, system-c, since they all understand the structure of the event</li>
      <li>Another usage of us is for data collecting.<br/>
Since all events are go through here. We are collecting interested events on this central point to our data warehouse for data mining and meachine learning. And since the format are unified, ETL could be more easier.</li>
    </ul>

    <h2 class="subtitle">Features</h2>
    <ul>
      <li>Low latency and High throughput.</li>
      <li>Based on Reactive Streams, The whole workflow supports backpressure and various stream operations.</li>
      <li>Failure tolerance.</li>
      <li>Logic can be easily composed by various Sources, Sinks, Transforms, Fallbacks, And they are expendable.</li>
      <li>Tracking and Monitoring supported.</li>
    </ul>

    <h2 class="subtitle">Contributing</h2>

    <p>Feedbacks and pull requests are welcome and appreciative. For major changes, please open an
      issue first to discuss what you would like to change.</p>

    <h2 class="subtitle">Change Logs</h2>

    <a href="https://thenetcircle.github.io/event-bus/change_logs/">Click to check Change Logs</a>

  </div>

</template>
