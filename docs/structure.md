# Configuration

## Config Files:

|File Path|Description|
|---------|-----------|
|core/src/main/resources/application.conf|Application config file|
|core/src/main/resources/reference.conf|Application default settings, Can be overrided by application.conf|
|core/src/main/resources/akka.conf|Akka config|
|core/src/main/resources/logback.xml|Log config|
|admin/backend/src/main/resources/application.conf|Admin config file|

## Environment Variables

|Variable Name|Description|Default Value|
|-------------|-----------|-------------|
|EB_APPNAME|Application Name||
|EB_ENV|Application Environment, One of following: dev/development, test, prod/production |dev|
|EB_RUNNERNAME|Runner name (Only for runner)||
|EB_LOGLEVEL|Logging Level|DEBUG|
|EB_LOGREF|STDOUT, FILE| STDOUT |

## Default Task Settings

### Sources

#### Http Source

```json
{
  interface = "0.0.0.0"
  port = 8000
  format = ActivityStreams
  succeeded-response = ok
  # server settings will override the default settings of akka.http.server
  server {
    # max-connections = 1024
    # ...
  }
}
```

#### Kafka Source

```json
{
  # bootstrap-servers = ""
  # group-id = ""
  # Will use either "topics" or "topic-pattern"
  # topics = []
  # topic-pattern = event-* # supports wildcard if topics are defined will use that one 

  max-concurrent-partitions = 100
  commit-max-batches = 20

  # Properties defined by org.apache.kafka.clients.consumer.ConsumerConfig
  # can be defined in this configuration section.
  properties {
    # Disable auto-commit by default
    "enable.auto.commit" = false
  }
}
```


### Sinks

#### Http Sink

```json
{
  # the default request could be overrided by info of the event
  default-request {
    method = POST
    # uri = "http://www.google.com"
  }
  min-backoff = 1 s
  max-backoff = 30 s
  random-factor = 0.2
  max-retrytime = 12 h
  concurrent-retries = 1
  # pool settings will override the default settings of akka.http.host-connection-pool
  pool {
    # max-connections = 4
    # min-connections = 0
    # max-open-requests = 32
    # pipelining-limit = 1
    # idle-timeout = 30 s
    # ...
  }
}
```

#### Kafka Sink

```json
{
  # bootstrap-servers = ""
  default-topic = "event-default"
  use-event-group-as-topic = true
  # Tuning parameter of how many sends that can run in parallel.
  parallelism = 100
  # How long to wait for `KafkaProducer.close`
  close-timeout = 60 s
  # Fully qualified config path which holds the dispatcher configuration
  # to be used by the producer stages. Some blocking may occur.
  # When this value is empty the dispatcher configured for the stream
  # will be used.
  use-dispatcher = "akka.kafka.default-dispatcher"
  # Properties defined by org.apache.kafka.clients.producer.ProducerConfig
  # can be defined in this configuration section.
  properties {
  }
}
```

### Fallback

#### Cassandra Fallback

```json
{
  contact-points = []
  port = 9042
  parallelism = 3
}
```

### Transforms

#### TNC Topic Resolver

```json
{
  contact-points = []
  port = 9042
  parallelism = 3
}
```