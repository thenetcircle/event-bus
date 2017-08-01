# EventDispatcher

[![License](https://img.shields.io/github/license/thenetcircle/event-dispatcher.svg)](LICENSE)

## Introduction

EventDispatcher is a system which taking care of delivering events/messages/data between different systems(source/sink/...), 
And supply data operations, filters ,loss-protection and delivery guarantee.

### Concepts

#### Story

#### Source

#### Sink

#### Event

## Background

 The first version(1.x) EventDispatcher is based on ActorSystem with push-pull-model to protect fast producer.
  
 It's running on our production system for delivering events from business systems to our backend services and resersed.
  
 We created this repository started from version 2.0.1, Which is based on goal of first version and rewrite code based on akka-stream.
 
## Roadmap

### 2.1.x

1. Rewrite code based on Akka-Stream.
2. Integrate some data sources and sinks (RedisPubSub, Kafka, AMQP, HTTP).
3. Persist data during delivering.
4. Fault tolerant, Retry if could

### 1.x

Old version based on ActorSystem, Delivering events between systems.

## TODOs

1. Admin Interface which includes streams management, streams add/update, streams statistics
2. Cluster support
3. No downtime deployment
4. Container support