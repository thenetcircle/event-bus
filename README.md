<!-- language: lang-none -->
            ____              __    ___         
           / __/  _____ ___  / /_  / _ )__ _____
          / _/| |/ / -_) _ \/ __/ / _  / // (_-<
         /___/|___/\__/_//_/\__/ /____/\_,_/___/

--------------------------------------- 
 
[![Build Status](https://travis-ci.org/thenetcircle/event-bus.svg?branch=master)](https://travis-ci.org/thenetcircle/event-dispatcher)
[![License](https://img.shields.io/github/license/thenetcircle/event-bus.svg)](LICENSE)

EventBus is a system which working as a postman who delivering events/messages/data between different systems/services.   
It supplies data operations (like partition, broadcast, filtering etc, thanks for Akka Stream), And data persistence, at least once semantics and exactly once semantics (needs client support)

## Features

- Refactoring based on Akka-Stream.
- Supplies some data sources and sinks (RedisPubSub, Kafka, AMQP, HTTP).
- Event persistence
- Supports secondary pipeline (for fallback)
- At least once delivery guarantee and Exactly once (needs client support)
- Statistics and Monitoring


## TODO
- ~~Add logs~~
- Error Handler
- Improve Tracer
- ExecutionContext
- Kafka Settings
- Stream Runner (take care of start, restart services)
- Implicate ChannelResolver
- Implicate Fallbacker
- Integrate Sentry
- Configuration storage
- Controller Api (start new service, restart services, change services, update configuration)
- Admin Interface