# Start From A Real Scenario:

Let's say we are a technique company, and we have some isolate systems:

1. Business System (mainly serves the end clients with business logics)
2. Search System (providers search service to other systems)
3. Data Mining System (mining data, generating report and providers cleared data for other services)
4. Notification System (notifies users for something happened)

**The workflow will be like this:**

<a href="assets/systems_workflow.png" target="_blank">![EventBus Workflow](assets/systems_workflow.png)</a>

**Then let's list some requirements:**

- the "Business System" needs a Queue to handler some heavy processes.
  Dispatch the reuqest to a Queue, And handler the request on a backend process asynchronously
- If something happened on the "Business System", Then tell other systems  
  For example: A user updated his profile   
  Then tell the "Search System" to updated related data as well  
  And tell the "Data Mining System" for this new event  
  Then tell the "Notification System" to notify the user for the changes by mail or other ways. 
- the "Search System" subscribes all search realtead "Events", And do actions according to the "Event"
- the "Data Mining System" listening on all "Events" happenes on among all systems, And record the "Events" to data warehouse with some kind of unstandable format.
- the "Notification System" listening on "Notify Events" and also emit "Events" like a user's email got bounced. 
- Easily tracking the lifetime of a "Event"
- ...

These requirements cover most of important ports of EventBus, A system distributes "Event"/"Message" among multiple systems with unified format.
Let's go to satisfy these requirements by EventBus.


# Installation

## Requirements

- Java 1.8+ installed
- Sbt
- Zookeeper
- Kafka 0.10+ (only when use Kafka Source/Kafka Sink)
- Cassandra (only when use Cassandra Fallback)

## Install Zookeepr

## Install Kafka

## Install EventBus

### From Source

- clone the code
```bash
git clone https://github.com/thenetcircle/event-bus.git
```

## Setting up EventBus

First of all we need to tell EventBus what to do, Since EventBus supports multiple different sources(called Receiver in EventBus) and targets(called Emitter). We need to point out which of them is using in this scenario.
Let's create a configuration file to describe this, The configuration file is based on [Typesafe Config](https://github.com/typesafehub/config), similar as json.
Let's create a file called application.conf, Put it on root of EventBus directory.

## Launch EventBus
After you set up the configuration, Please make sure the dependencies are also set up properly. Like: Kafka...
Then we can launch EventBus by the way if want, Let's using normal way here.

- package EventBus
```sh
sbt clean compile stage
```

- launch with the configuration
```sh
./target/universal/stage/bin/event-bus -Dconfig.file=./application.conf
```

Now EventBus should be listening on localhost port 8086 expecting for HTTP messages, And transporting messages to Kafka then delivering to localhost port 8087 by HTTP.