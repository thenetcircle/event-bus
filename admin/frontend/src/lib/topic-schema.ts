export default {
  "type": "array",
  "title": "Topic List",
  "uniqueItems": true,
  "items": {
    "type": "object",
    "title": "Entry",
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
