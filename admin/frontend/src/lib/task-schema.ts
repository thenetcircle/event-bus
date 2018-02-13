const sourceSchema: any = {
  'http': {
    "title": "Http Source",
    "type": "object",
    "properties": {
      "interface": {
        "type": "string",
        "default": "0.0.0.0",
        "required": true
      },
      "port": {
        "type": "integer",
        "default": 8000,
        "minimum": 1024,
        "maximum": 65535,
        "required": true
      },
      "format": {
        "type": "string",
        "enum": [
          "ActivityStreams"
        ]
      },
      "succeeded-response": {
        "type": "string",
        "default": "ok"
      },
      "server": {
        "type": "object",
        "title": "Server",
        "properties": {
          "max-connections": {
            "type": "integer",
            "default": 1024
          },
          "request-timeout": {
            "type": "string",
            "default": "10 s",
            "description": "possible units: (s)econd, (m)inute, (h)ours"
          },
          "idle-timeout": {
            "type": "string",
            "default": "60 s",
            "description": "possible units: (s)econd, (m)inute, (h)ours"
          },
          "bind-timeout": {
            "type": "string",
            "default": "1 s",
            "description": "possible units: (s)econd, (m)inute, (h)ours"
          },
          "linger-timeout": {
            "type": "string",
            "default": "1 min",
            "description": "possible units: (s)econd, (m)inute, (h)ours"
          }
        }
      }
    }
  },

  'kafka': {
    "title": "Kafka Source",
    "type": "object",
    "properties": {
      "bootstrap-servers": {
        "type": "string",
        "title": "Bootstrap Servers",
        "default": "",
        "required": true
      },
      "group-id": {
        "type": "string",
        "title": "Group Id",
        "default": ""
      },
      "topics": {
        "type": "array",
        "title": "Subscribed Topics",
        "uniqueItems": true,
        "items": {
          "type": "string",
          "title": "topic"
        },
        "required": true
      },
      "topic-pattern": {
        "type": "string",
        "title": "Subscribed Topic Pattern",
        "description": "supports regex pattern, if topics is set, will use it instead",
        "default": "",
        "required": true
      },
      "max-concurrent-partitions": {
        "type": "integer",
        "default": 100,
        "required": true
      },
      "commit-timeout": {
        "type": "string",
        "default": "15 s"
      },
      "use-dispatcher": {
        "type": "string",
        "default": "akka.kafka.default-dispatcher"
      },
      "max-connections": {
        "type": "integer",
        "default": 1024,
        "required": true
      },
      "properties": {
        "type": "object",
        "title": "Kafka Consumer Properties",
        "properties": {
          /*"enable.auto.commit": {
            "type": "boolean",
            "title": "Enable auto commit?",
            "default": false
          }*/
        }
      }
    }
  }
}

const transformSchema: any = {
  'tnc-topic-resolver': {
    "title": "TNC Topic Resolver Transform",
    "type": "object",
    "properties": {
      "default-topic": {
        "type": "string",
        "title": "Default Topic",
        "description": "supports substitutes: {app_name}, {app_env}, {story_name}",
        "default": "event-{app_name}-default",
        "required": true
      },
      "use-cache": {
        "type": "boolean",
        "title": "Use Cache",
        "descript": "If cache the events topics",
        "default": false,
        "required": true
      }
    }
  },
  'tnc-dino-forwarder': {
    "title": "TNC Dino Forwarder",
    "type": "object",
    "properties": {
    }
  }
}

const sinkSchema: any = {
  'http': {
    "title": "Http Sink",
    "type": "object",
    "properties": {
      "default-request": {
        "type": "object",
        "title": "Server",
        "properties": {
          "method": {
            "type": "string",
            "enum": [
              "POST", "GET"
            ]
          },
          "uri": {
            "type": "string",
            "description": "when event does not include url info, use this default one"
          }
        },
        "required": true
      },
      "min-backoff": {
        "type": "string",
        "default": "1 s",
        "required": true
      },
      "max-backoff": {
        "type": "string",
        "default": "30 s",
        "required": true
      },
      "random-factor": {
        "type": "number",
        "default": 0.2
      },
      "max-retrytime": {
        "type": "string",
        "default": "12 h",
        "required": true
      },
      "concurrent-retries": {
        "type": "integer",
        "default": 1,
        "required": true
      },
      "pool": {
        "type": "object",
        "title": "Http Connection Pool",
        "properties": {
          "max-connections": {
            "type": "integer",
            "default": 4
          },
          "min-connections": {
            "type": "integer",
            "default": 0
          },
          "max-retries": {
            "type": "integer",
            "default": 1
          },
          "max-open-requests": {
            "type": "integer",
            "default": 32
          },
          "pipelining-limit": {
            "type": "integer",
            "default": 1
          },
          "idle-timeout": {
            "type": "string",
            "default": "30 s",
            "description": "possible units: (s)econd, (m)inute, (h)ours"
          }
        }
      }
    }
  },

  'kafka': {
    "title": "Kafka Sink",
    "type": "object",
    "properties": {
      "bootstrap-servers": {
        "type": "string",
        "title": "Bootstrap Servers",
        "default": "",
        "required": true
      },
      "default-topic": {
        "type": "string",
        "default": "event-default",
        "required": true
      },
      "use-event-group-as-topic": {
        "type": "boolean",
        "default": true,
        "required": true
      },
      "parallelism": {
        "type": "integer",
        "default": 100,
        "required": true
      },
      "close-timeout": {
        "type": "string",
        "default": "60 s"
      },
      "use-dispatcher": {
        "type": "string",
        "default": "akka.kafka.default-dispatcher"
      },
      "properties": {
        "type": "object",
        "title": "Kafka Producer Properties",
        "properties": {
        }
      }
    }
  }
}

const fallbackSchema: any = {
  'cassandra': {
    "type": "object",
    "properties": {
      "contact-points": {
        "type": "array",
        "uniqueItems": true,
        "items": {
          "type": "string",
          "title": "host"
        },
        "required": true
      },
      "port": {
        "type": "integer",
        "default": 9042,
        "required": true
      },
      "parallelism": {
        "type": "integer",
        "default": 3,
        "required": true
      }
    }
  }
}

export default {
  'source': sourceSchema,
  'transform': transformSchema,
  'sink': sinkSchema,
  'fallback': fallbackSchema
} as any
