export default {
  "type": "array",
  "title": "Topic & Events Mapping",
  "uniqueItems": true,
  "items": {
    "type": "object",
    "title": "Mapping",
    "properties": {
      "topic": {
        "type": "string",
        "title": "Topic",
        "required": true
      },
      "patterns": {
        "type": "array",
        "uniqueItems": true,
        "title": "Event Patterns",
        "items": {
          "type": "string",
          "title": "pattern"
        },
        "required": true
      }
    }
  }
}
