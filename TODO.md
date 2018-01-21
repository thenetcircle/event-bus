# Todo
- ~~Add logs~~
- ~~Error Handler~~
- Improve Tracer
- ExecutionContext
- Kafka Settings
- Stream Runner (take care of start, restart services)
- Implicate ChannelResolver
- Implicate Fallbacker
- Integrate Sentry
- Configuration storage
- Controller Api (start new service, restart services, change services, update configuration)
- Admin Interface

- ~~handle extract failed case~~
- ~~rename context to runningContxt~~
- ~~move default config to reference.conf~~
- ~~create BuilderEnvironment~~
- ~~create Config runner and Zookeeper runner~~
- add rfc date parser and change published field format

# Cassandra
file:///Users/baineng/Sources/apache-cassandra-3.11.1/doc/cql3/CQL.html#createTableStmt

create keyspace eventbus_test if not exists
    with replication = {'class': 'SimpleStrategy', 'replication_factor': '2'}  and durable_writes = true;

create table if not exists fallback(
    uuid text, 
    storyname text, 
    eventname text, 
    createdat timestamp, 
    fallbacktime timestamp,
    failedtaskname text, 
    group text, 
    providerid text, 
    providertype text, 
    generatorid text, 
    generatortype text,  
    actorid text, 
    actortype text,
    targetid text, 
    targettype text, 
    body blob, 
    format text, 
    cause text,
    primary key (uuid, storyname, createdat, group, eventname)
)

CREATE MATERIALIZED VIEW fallback_by_storyname AS
    SELECT * FROM fallback WHERE uuid IS NOT NULL AND storyname IS NOT NULL AND createdat IS NOT NULL AND eventname IS NOT NULl AND group IS NOT NULL
    PRIMARY KEY (storyname, createdat, eventname, group, uuid)