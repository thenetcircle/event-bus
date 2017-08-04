# EventDispatcher

[![License](https://img.shields.io/github/license/thenetcircle/event-dispatcher.svg)](LICENSE)

## Introduction

EventDispatcher is a system which working as a postman who delivering events/messages/data between different systems/services.   
It supplies data operations (like partition, broadcast, filtering etc, thanks for Akka Stream), And data persistence, at least once semantics and exactly once semantics (needs client support)

## Background

 There is a first version(1.x) of EventDispatcher which running on production for a while already. 
 It's role also a proxy between different services/systems for delivering events.  
 It's based on Akka with push-pull-model, no persistence support.  
 This project is based on goal of the first version. As a upgraded version with more features and safety guarantee.  
 This versoin will base on Akka Stream, Utilizing some grate features from Actor model and Reactive Streams.
 
## Roadmap

### 2.x

1. Refactoring based on Akka-Stream.
2. Supply some data sources and sinks (RedisPubSub, Kafka, AMQP, HTTP).
3. Events persistence
4. Support secondary source (for fallback)
5. At least once delivery guarantee and Exactly once (needs client support)

### 1.x

First version of EventDispatcher based on Akka, Running with some basic requirements.

## TODOs

1. Admin Interface
2. Cluster support
3. Hot deployment
4. Container support