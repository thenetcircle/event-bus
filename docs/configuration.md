## Default Configuration
```json
# Default Settings Of EventBus
event-bus {
  pipeline {
    kafka {
      pipeline {
        akka.kafka.producer {
          #use-dispatcher = "akka.kafka.default-dispatcher"
          kafka-clients {
            client.id = "EventBus-Producer"
          }
        }
        akka.kafka.consumer {
          #use-dispatcher = "akka.kafka.default-dispatcher"
          kafka-clients {
            client.id = "EventBus-Consumer"
          }
        }
      }
      inlet {
        #close-timeout = 60s
        #parallelism = 10
      }
      outlet {
        #group-id = TestGroup
        extract-parallelism = 3
        #topics = []
        #topicPattern = event-*
        #poll-interval = 50ms
        #poll-timeout = 50ms
        #stop-timeout = 30s
        #close-timeout = 20s
        #commit-timeout = 15s
        #wakeup-timeout = 3s
        #max-wakeups = 10
      }
      committer {
        commit-parallelism = 3
        commit-batch-max = 20
      }
    }
  }
  
  transporter {
    #name = TestTransporter
    #entrypoints = [
    #  {
    #    type = http
    #    name = TestEntryPoint1
    #    interface = 127.0.0.1
    #    port = 8080
    #  }
    #]
    #pipeline {
    #  name = DefaultKafkaPipeline
    #  inlet-settings {}
    #}
    transport-parallelism = 1
    commit-parallelism = 10
    #akka.stream.materializer {}
  }

  entrypoint {
    http {
      #name = TestHttpEntryPoint
      #interface = localhost
      #port = 8080
      priority = normal
      // TODO: adjust these default values when doing stress testing
      max-connections = 1000
      pre-connection-parallelism = 10
      event-format = default
      # akka.http.server {} // override "akka.http.server" default settings
    }
  }

  dispatcher {
    #akka.stream.materializer {}
  }

  endpoint {
    http {
      max-retry-times = 10
      request {
        port = 80
        method = POST
        uri = /
      }
      #expected-response-data = OK
      akka.http.host-connection-pool {
        #max-connections = 4
        max-retries = 0
        #max-open-requests = 32
        #pipelining-limit = 1
        #idle-timeout = 30 s
      }
    }
  }
}
```

## Runtime Configuration