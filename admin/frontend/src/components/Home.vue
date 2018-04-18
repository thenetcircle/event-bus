<template>

  <div class="container box content">

    <h1 class="title">Introduction of EventBus</h1>

    <p>EventBus is a messages/events delivering application, It is designed to be a message/event
      hub to transfer data from different sources to different sinks. It is using internally for
      unified data collection(collect data from different sources with specific data format to our
      data warehouse) and async events delivering among services.</p>

    <p>It comes with some implemented sources(http, kafka, redis, etc...), sinks(http, kafka,
      etc...), fallbacks(see the document) and a extendable structure. Also a admin interface to
      easily manage the relationship between them.</p>

    <img alt="EventBus Workflow" src="https://thenetcircle.github.io/event-bus/assets/event-bus-workflow.png">

    <h2 class="subtitle">Features</h2>
    <ul>
      <li>Supplies multiple different sources and sinks.</li>
      <li>Supplies failure demotion(fallback) strategy and recovering ability.</li>
      <li>Based on AkkaStreams, Offers excellent performance and back-pressure ability.</li>
      <li>Stateless, Easier for deployment, high availability and load balance.</li>
      <li>Comes with a simple admin interface for managing and watching whole cluster.</li>
    </ul>

    <h2 class="subtitle">Contributing</h2>

    <p>Feedbacks and pull requests are welcome and appreciative. For major changes, please open an
      issue first to discuss what you would like to change.</p>


  </div>

</template>
