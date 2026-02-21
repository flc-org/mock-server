# Mock Server

Spring Boot record-and-replay proxy for integration tests.

## Simple Model

To create a mock config, client sends:

- `endpointDesc`
- `hostKey`
- `hashGenerationFunction` (required)

Server generates a unique `proxySalt` and stores it.

At runtime:

1. Request comes to `/proxy/{proxySalt}/...`
2. Server finds config by `proxySalt` (primary key)
3. It resolves downstream host from `host_mapping` table by `hostKey`
4. It strips `{proxySalt}` from path and forwards downstream
5. Response is record/replay cached by `(mock_server_config_proxy_salt, request_hash)`

## Mock Config Columns

- `proxy_salt` (primary key)
- `endpoint_desc` (non-null)
- `host_key` (non-null)
- `hash_generation_function` (non-null)

## Host Mapping Columns

- `host_key` (primary key)
- `host_name` (non-null)

## API

Host mapping CRUD:

- `GET /host-mappings`
- `GET /host-mappings/{hostKey}`
- `POST /host-mappings`
- `PUT /host-mappings/{hostKey}`
- `DELETE /host-mappings/{hostKey}`

Mock config CRUD:

- `GET /mock-server-configs`
- `GET /mock-server-configs/{proxySalt}`
- `POST /mock-server-configs`
- `PUT /mock-server-configs/{proxySalt}`
- `PUT /mock-server-configs/{proxySalt}/hash-generation-function`
- `DELETE /mock-server-configs/{proxySalt}`

### Create/Update Body

```json
{
  "endpointDesc": "serviceAMock",
  "hostKey": "serviceAHost",
  "hashGenerationFunction": "spel:#request.path + '|' + #request.queryString"
}
```

### Host Mapping Create/Update Body

```json
{
  "hostKey": "serviceAHost",
  "hostName": "https://api.service-a.example"
}
```

`POST` response includes generated `proxySalt`. Use it in requests:

```text
GET /proxy/{proxySalt}/service-a/v1/resources/123?limit=10
```

## Build Modes

Utility jar (for embedding in another Spring Boot app, downstream dependencies expected from main app):

```bash
mvn clean package
```

Standalone executable jar (bundled dependencies + main method):

```bash
mvn clean package -Pstandalone
```

This creates:

- `target/mock-server-0.0.1-SNAPSHOT.jar` (utility jar)
- `target/mock-server-0.0.1-SNAPSHOT-standalone.jar` (standalone executable jar)

## Run

Run standalone jar:

```bash
java -jar target/mock-server-0.0.1-SNAPSHOT-standalone.jar
```

Default port is `8099` (can be overridden with Spring Boot properties).
