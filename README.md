<!-- language: lang-scala -->
# EventBus

[![Build Status](https://travis-ci.org/thenetcircle/event-bus.svg?branch=master)](https://travis-ci.org/thenetcircle/event-bus)

EventBus is built for distributing unified events/messages(as a language everyone can understand) from multiple different sources to different targets. With abilities of messages persistence, tracing and manipulating.

Visit the [Documention](https://thenetcircle.github.io/event-bus/) for more details.

## Features

- Supplies multiple different sources and targets.
- Based on AkkaStreams, Whole flow supports back-pressure, and various stream operations.
- Each components are decoupled, can run into independent processes.
- Fully tested (unit test, integration test, stress test).
- Whole system is configurable.
- Providers tracing and monitoring.
- Providers manager interface.
- Fault-tolerant

## Contributing
Feedbacks and pull requests are welcome and appreciative. For major changes, please open an issue first to discuss what you would like to change.