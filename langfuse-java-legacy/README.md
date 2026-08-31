# langfuse-java

This repository contains an auto-generated Langfuse API client for Java based on our [API specification](https://github.com/langfuse/langfuse/tree/main/fern/apis/server).
See the [Langfuse API reference](https://api.reference.langfuse.com) for more details on the available endpoints.

**Note:** We recommend to solve tracing via the [OpenTelemetry Instrumentation](https://langfuse.com/docs/opentelemetry/get-started) instead of using the Ingestion API directly. You can use the [OpenTelemetry Java SDK](https://github.com/open-telemetry/opentelemetry-java) and export spans to the [Langfuse OTel endpoint](https://langfuse.com/integrations/native/opentelemetry).
This allows for a more detailed and standardized tracing experience without the need to handle batching and updates internally.
Check out our [Spring AI Example](https://langfuse.com/docs/integrations/spring-ai) for more details.

## Langfuse v4: removed endpoints

Langfuse v4 uses an [observations-first data model](https://langfuse.com/docs/v4). The endpoints built on the v3 model are deprecated: Langfuse Cloud serves them until **November 16, 2026**, and they become unavailable in self-hosted deployments as soon as those upgrade to v4.

Those endpoints have been **removed from this client** so that it only exposes the API surface that survives the v4 cutover. The canonical, per-endpoint migration reference is [Migration of deprecated APIs](https://langfuse.com/faq/all/deprecated-api-migration); the [Versions & Compatibility matrix](https://langfuse.com/docs/compatibility) lists which endpoints work against which server version, and [Make your project ready for the upgrade to Langfuse v4](https://langfuse.com/faq/all/upgrade-to-langfuse-v4) is the step-by-step upgrade guide.

| Removed from this client                                                          | Deprecated endpoint                                          | Replacement                                                                                                                                                       |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `client.ingestion().batch(...)`                                                   | `POST /api/public/ingestion`                                 | [OpenTelemetry ingestion](https://langfuse.com/faq/all/deprecated-api-migration#ingestion) — see above                                                            |
| `client.observations().get(...)` / `.getMany(...)`                                | `GET /api/public/observations`, `/observations/{id}`         | `client.observationsV2().getMany(...)` ([Observations API v2](https://langfuse.com/faq/all/deprecated-api-migration#observations))                                |
| `client.trace().get(...)` / `.list(...)`                                          | `GET /api/public/traces`, `/traces/{id}`                    | `client.observationsV2().getMany(...)` filtered by `traceId` ([Traces](https://langfuse.com/faq/all/deprecated-api-migration#traces))                             |
| `client.sessions().get(...)` / `.list(...)`                                        | `GET /api/public/sessions`, `/sessions/{id}`                | `client.observationsV2().getMany(...)` filtered by `sessionId` ([Sessions](https://langfuse.com/faq/all/deprecated-api-migration#sessions))                       |
| `client.metrics().metrics(...)`                                                   | `GET /api/public/metrics`                                    | `client.metricsV2().metrics(...)` ([Metrics API v2](https://langfuse.com/faq/all/deprecated-api-migration#metrics))                                               |
| `client.scoreV2().get(...)` / `.getById(...)`                                      | `GET /api/public/v2/scores`, `/v2/scores/{id}`              | [Scores API v3](https://langfuse.com/faq/all/deprecated-api-migration#scores) — **not generated yet**, call `GET /api/public/v3/scores` directly for now          |
| `client.datasets().getRuns(...)` / `.getRun(...)` / `.deleteRun(...)`              | `GET`/`DELETE /api/public/datasets/{name}/runs[/{runName}]` | [Experiments API](https://langfuse.com/faq/all/deprecated-api-migration#dataset-runs) — **not generated yet**, call `GET /api/public/experiments` directly for now |
| `client.datasetRunItems().create(...)` / `.list(...)`                              | `POST`/`GET /api/public/dataset-run-items`                   | [Experiment Items API](https://langfuse.com/faq/all/deprecated-api-migration#dataset-runs) / OTel experiment attributes — **not generated yet**                    |

Notes:

- **Score writes are unaffected.** `client.score().create(...)` (`POST /api/public/scores`) and `client.score().delete(...)` stay supported after the v4 cutover. Only score *reads* move to v3.
- **Trace deletion is not deprecated.** `client.trace().delete(...)` and `client.trace().deleteMultiple(...)` are kept; they are also how you delete experiment data now that `DELETE /datasets/{name}/runs/{runName}` is gone.
- **Datasets themselves are not deprecated.** `client.datasets()` still exposes the `/api/public/v2/datasets` endpoints; only the dataset *run* endpoints were removed.
- The `/api/public/v3/scores`, `/api/public/experiments`, and `/api/public/experiment-items` endpoints exist in the Langfuse API but are not part of this client yet. They will be picked up by the next regeneration; until then, call them directly.

## Installation

The recommended way to install the langfuse-java API client is via Maven Central:

```xml
<dependency>
    <groupId>com.langfuse</groupId>
    <artifactId>langfuse-java</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Usage

Instantiate the Langfuse Client with the respective endpoint and your API Keys.

```java
import com.langfuse.client.LangfuseClient;

LangfuseClient client = LangfuseClient.builder()
        .url("https://cloud.langfuse.com") // 🇪🇺 EU data region
        // .url("https://us.cloud.langfuse.com") // 🇺🇸 US data region
        // .url("http://localhost:3000") // 🏠 Local deployment
        .credentials("pk-lf-...", "sk-lf-...")
        .build();
```

An async client is also available via `AsyncLangfuseClient.builder()` with the same configuration options.

Make requests using the clients:

```java
import com.langfuse.client.core.LangfuseClientApiException;
import com.langfuse.client.resources.prompts.types.PromptMetaListResponse;

try {
    PromptMetaListResponse prompts = client.prompts().list();
} catch (LangfuseClientApiException error) {
    System.out.println(error.body());
    System.out.println(error.statusCode());
}
```

## Testing

### Unit tests

Unit tests (deserialization, query string mapping) run without any credentials:

```bash
mvn test
```

### Integration tests

Integration tests connect to a real Langfuse project. They require credentials and are excluded from `mvn test`.

1. Copy `.env.example` to `.env` and fill in your API keys:
   ```bash
   cp .env.example .env
   ```

2. Ensure your Langfuse project contains the following prompts:
   - `test-chat-prompt` — chat type, at least one message with `role` and `content`
   - `test-text-prompt` — text type, non-empty prompt text

3. Run all tests (unit + integration):
   ```bash
   mvn verify
   ```

   Or run only integration tests:
   ```bash
   mvn failsafe:integration-test
   ```

Integration tests skip gracefully when credentials are absent.

## Drafting a Release

Run `./mvnw release:prepare -DreleaseVersion=` with the version you want to create.
Push the changes including the tag.

## Publishing to Maven Central

This project is configured to publish to Maven Central.
To publish to Maven Central, you need to configure the following secrets in your GitHub repository:

- `OSSRH_USERNAME`: Your Sonatype OSSRH username
- `OSSRH_PASSWORD`: Your Sonatype OSSRH password
- `GPG_PRIVATE_KEY`: Your GPG private key for signing artifacts
- `GPG_PASSPHRASE`: The passphrase for your GPG private key

## Updating

1. Ensure that langfuse-java is placed in the same directory as the main [langfuse](https://github.com/langfuse/langfuse) repository.
2. Setup a new Java fern generator using
   ```yaml
      - name: fernapi/fern-java-sdk
        version: 3.38.1
        output:
          location: local-file-system
          path: ../../../../langfuse-java/src/main/java/com/langfuse/client/
        config:
          client-class-name: LangfuseClient
   ```
3. Generate the new client code using `npx fern-api generate --api server`.
4. Manually set the `package` across all files to `com.langfuse.client`.
5. Verify that `LangfuseClientBuilder.setAuthentication()` uses `Basic` auth (not `Bearer`).
6. Adjust Javadoc strings with HTML properties as the apidocs package does not support them.
7. Commit the changes in langfuse-java and push them to the repository.
