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
3. It resolves downstream host from startup host map by `hostKey`
4. It strips `{proxySalt}` from path and forwards downstream
5. Response is record/replay cached by `(mock_server_config_proxy_salt, request_hash)`

## Mock Config Columns

- `proxy_salt` (primary key)
- `endpoint_desc` (non-null)
- `host_key` (non-null)
- `hash_generation_function` (non-null)

## Host Key Config (Startup)

Configure downstream hosts in `application.yml`:

```yaml
mock-server:
  hosts:
    ueqsHost: https://www.ueqs.com
```

## API

- `GET /mock-server-configs`
- `GET /mock-server-configs/{proxySalt}`
- `POST /mock-server-configs`
- `PUT /mock-server-configs/{proxySalt}`
- `PUT /mock-server-configs/{proxySalt}/hash-generation-function`
- `DELETE /mock-server-configs/{proxySalt}`

### Create/Update Body

```json
{
  "endpointDesc": "ueqsV2mock",
  "hostKey": "ueqsHost",
  "hashGenerationFunction": "spel:#request.path + '|' + #request.queryString"
}
```

`POST` response includes generated `proxySalt`. Use it in requests:

```text
GET /proxy/{proxySalt}/ueqs/v3/123/entitlements?limit=10
```

## Run

```bash
mvn spring-boot:run
```

Default port is `8099`.
