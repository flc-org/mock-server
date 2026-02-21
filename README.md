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

Capture audit:

- `GET /capture-audits`
  - filters: `captureKey`, `proxySalt`
  - pagination: `page` (0-based), `size`

Recorded response cache:

- `GET /recorded-responses`
  - filters: `proxySalt`, `urlContains`, `requestBodyContains`, `headersContains`
  - pagination: `page` (0-based), `size`
- `DELETE /recorded-responses` with body:

```json
{
  "ids": [101, 102, 103]
}
```

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

Only the utility jar is published to Maven Central.
Standalone jar is not published; build it from source using the `-Pstandalone` command above.

## Run

Run standalone jar:

```bash
java -jar target/mock-server-0.0.1-SNAPSHOT-standalone.jar
```

Default port is `8099` (can be overridden with Spring Boot properties).

Datasource properties for standalone or utility-jar mode are exposed under:

```yaml
mock-server:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:file:./data/mock-server;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password:
    hikari:
      pool-name: mock-server-hikari
```

## Web UI

Singleton shell:

- `GET /` is the only navigation shell page.
- It swaps views using URL query `?view=` without full-page navigation:
  - `mock-configs`
  - `host-mappings`
  - `capture-audits`
  - `recorded-responses`

Compatibility routes:

- `GET /mock-configs.html`, `GET /host-mappings.html`, `GET /capture-audits.html`, `GET /recorded-responses.html`
- These routes redirect to `/?view=...`.

Reusable web components for utility-jar mode:

- `<mock-config-panel></mock-config-panel>`
- `<host-mapping-panel></host-mapping-panel>`
- `<capture-audit-panel></capture-audit-panel>`
- `<recorded-response-panel></recorded-response-panel>`

Component scripts:

- `/js/components/mock-server-components.js` (register all components once)
- `/js/components/mock-config-panel.js`
- `/js/components/host-mapping-panel.js`
- `/js/components/capture-audit-panel.js`
- `/js/components/recorded-response-panel.js`

Optional component attribute:

- `api-base="/some-prefix"` to call APIs with a base path prefix

Example embedding in a main project UI:

```html
<script type="module" src="/js/components/mock-server-components.js"></script>

<mock-config-panel api-base=""></mock-config-panel>
<host-mapping-panel api-base=""></host-mapping-panel>
<capture-audit-panel api-base=""></capture-audit-panel>
<recorded-response-panel api-base=""></recorded-response-panel>
```
