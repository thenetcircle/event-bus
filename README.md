<!-- language: lang-none -->
                                    ____              __      ___         
                                   / __/  _____ ___  / /_    / _ )__ _____
                                  / _/| |/ / -_) _ \/ __/   / _  / // (_-<
                                 /___/|___/\__/_//_/\__/   /____/\_,_/___/

[![Build Status](https://travis-ci.org/thenetcircle/event-bus.svg?branch=master)](https://travis-ci.org/thenetcircle/event-bus)
[![License](https://img.shields.io/github/license/thenetcircle/event-bus.svg)](LICENSE)

EventBus is built for distributing unified events/messages(as a language everyone can understand) from multiple different sources to different targets. With abilities of messages persistence, tracing and manipulating.

[![EventBus Workflow](https://thenetcircle.github.io/event-bus/assets/workflow.png)](https://thenetcircle.github.io/event-bus/assets/workflow.png)

Visit the [Documention](https://thenetcircle.github.io/event-bus/) for more details.

## Features

- Supplies multiple different sources and targets.
- Based on AkkaStream, Whole flow supports back-pressure, and stream operations.
- Each components are decoupled, can run into independent processes.
- Fully tested (unit test, integration test, stress test).
- Whole system is configurable.
- Providers tracing and monitoring.
- Providers manager interface.
- Fault-tolerant

## Contributing
Feedbacks and pull requests are welcome and appreciative. For major changes, please open an issue first to discuss what you would like to change.