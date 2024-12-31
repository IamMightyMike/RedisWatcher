# Redis Watchdog

Redis Watchdog is a Vert.x-based monitoring application I use at my current project for tracking changes in a Redis database in real-time during UAT tests. It listens to key events (HSET, HDEL, etc.) and publishes detailed updates via the Vert.x EventBus. 

The reason why it only handles HSET and HDEL events at the moment is that, at said project we are only using Redis hashkeys. My aim is to eventually implement the handling of other Redis key and event types.

Designed for simplicity and performance, this project demonstrates integration with Redis, real-time event handling, and JSON-based messaging for event-driven architectures.
