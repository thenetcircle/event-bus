<!-- language: lang-none -->
                                       ____              __    ___         
                                      / __/  _____ ___  / /_  / _ )__ _____
                                     / _/| |/ / -_) _ \/ __/ / _  / // (_-<
                                    /___/|___/\__/_//_/\__/ /____/\_,_/___/

  
[![Build Status](https://travis-ci.org/thenetcircle/event-bus.svg?branch=master)](https://travis-ci.org/thenetcircle/event-bus)
[![License](https://img.shields.io/github/license/thenetcircle/event-bus.svg)](LICENSE)

EventBus likes a postman delivering events/messages from multiple sources to multiple targets, Also supports features like data persistence(by Pipeline, default using Kafka), fallback solution(for example if delivery failed) etc...

## Features

- Supplies multiple sources and sinks (HTTP for now).
- Supplies multiple pipelines (Kafka for now)
- Retry and Fallback
- Supplies tracing and monitoring

## Todo
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