# margin-server

Spring Boot backend for [Margin](https://margin.chat). Handles REST, WebSocket, auth, storage, subscriptions, and SFU coordination.

## Building

```bash
./mvnw clean package
```

## Running locally

```bash
./mvnw spring-boot:run
```

Dev defaults are in `application-dev.yml`.

## Tests

```bash
./mvnw test
```

## License

Licensed under the GNU GPLv3. See [LICENSE](LICENSE).