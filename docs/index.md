EventBus is a messages/events delivering application, It is designed to be a message/event hub to transfer data from different sources to different sinks.
It is using internally for unified data collection(collect data from different sources with specific data format to our data warehouse) and async events delivering among services.

It comes with some implemented sources(http, kafka, redis, etc...), sinks(http, kafka, etc...), fallbacks(see the document) and a extendable structure. Also a admin interface to easily manage the relationship between them.


<a href="assets/event-bus-workflow.png" target="_blank">![EventBus Workflow](assets/event-bus-workflow.png)</a>


## Features

- Supplies multiple different sources and sinks.
- Supplies failure demotion(fallback) strategy and recovering ability.
- Based on AkkaStreams, Offers excellent performance and back-pressure ability.
- Stateless, Easier for deployment, high availability and load balance.
- Comes with a simple admin interface for managing and watching whole cluster.

## Contributing
Feedbacks and pull requests are welcome and appreciative. For major changes, please open an issue first to discuss what you would like to change.