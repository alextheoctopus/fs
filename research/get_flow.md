# GET Request Flow

Краткая цепочка выполнения `get` в сервисе.

## 1. ClientService

Клиент формирует `GetRequest`:

```kotlin
entity_keys = ["patient1", "patient2"]
features = ["f1", "f2"]
```

Затем вызывает gRPC-метод:

```kotlin
stub.get(request)
```

## 2. FeatureStoreService

Запрос попадает в серверный метод:

```kotlin
FeatureStoreService.get(...)
```

Сервис передает запрос в mapper:

```kotlin
mapper.getRequestKeysParser(request)
```

## 3. RedisRequestMapper

Mapper выполняет две основные операции:

```text
loadFromRedis(request)
buildGetResponse(request, payloads)
```

Сначала он берет entity keys из запроса:

```text
patient1, patient2
```

И преобразует их в Redis-ключи:

```text
entity:patient1, entity:patient2
```

## 4. RedisStorage

Mapper вызывает:

```kotlin
storage.getPayloads(redisKeys)
```

Внутри `getPayloads` происходит:

```text
1. Открывается Jedis-соединение.
2. Ключи переводятся в ByteArray.
3. Выполняется Redis MGET.
4. Redis возвращает bytes.
5. bytes преобразуются в RedisPayload через parseFrom.
```

## 5. Build Response

Mapper получает `RedisPayload` для каждой entity и строит `GetResponse`.

Он оставляет только признаки, которые запросил клиент:

```text
f1, f2
```

И собирает ответ в формате:

```text
entity_keys = [patient1, patient2]
columns = {
  f1 -> values for patient1, patient2
  f2 -> values for patient1, patient2
}
```

## 6. Return To Client

`FeatureStoreService` отправляет результат клиенту:

```kotlin
responseObserver.onNext(response)
responseObserver.onCompleted()
```

Клиент получает готовый `GetResponse` как результат:

```kotlin
val response = stub.get(request)
```

## Short Chain

```text
ClientService.get
-> stub.get
-> FeatureStoreService.get
-> RedisRequestMapper.getRequestKeysParser
-> RedisStorage.getPayloads
-> Redis MGET
-> RedisPayload.parseFrom
-> RedisRequestMapper.buildGetResponse
-> GetResponse
```
