# 时序图 - 商铺查询（缓存击穿 - 逻辑过期）& GEO 附近商铺

## 1. 根据 ID 查询商铺（逻辑过期 + 异步重建）

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant C as ShopController
    participant S as ShopServiceImpl
    participant CC as CacheClient
    participant R as Redis
    participant POOL as 线程池 CACHE_REBUILD_EXECUTOR
    participant DB as MySQL

    U->>C: GET /shop/{id}
    C->>S: queryById(id)
    S->>CC: queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, getById, ttl)
    CC->>R: GET cache:shop:{id}
    R-->>CC: json (RedisData)
    alt 缓存为空
        CC-->>S: null
        S-->>U: Result.fail("店铺不存在")
    else 缓存存在
        CC->>CC: 反序列化 RedisData -> Shop + expireTime
        alt 未过期
            CC-->>S: shop
            S-->>U: Result.ok(shop)
        else 已逻辑过期
            CC->>R: SETNX lock:shop:{id} 1 EX 10  (互斥锁)
            alt 拿到锁
                R-->>CC: true
                CC->>POOL: submit(异步重建)
                POOL->>DB: SELECT * FROM tb_shop WHERE id=?
                DB-->>POOL: shop
                POOL->>R: SET cache:shop:{id} = RedisData(shop, newExpire)
                POOL->>R: DEL lock:shop:{id}
            else 未拿到锁
                R-->>CC: false
            end
            CC-->>S: 旧的 shop（短暂返回脏数据）
            S-->>U: Result.ok(shop)
        end
    end
```

## 2. 更新商铺（先写库，再删缓存）

```mermaid
sequenceDiagram
    autonumber
    actor A as 管理员
    participant C as ShopController
    participant S as ShopServiceImpl
    participant DB as MySQL
    participant R as Redis

    A->>C: PUT /shop {id, ...}
    C->>S: update(shop)  «@Transactional»
    S->>DB: UPDATE tb_shop SET ... WHERE id=?
    S->>R: DEL cache:shop:{id}
    S-->>A: Result.ok()
```

## 3. 按类型 + 坐标查询附近商铺

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant C as ShopController
    participant S as ShopServiceImpl
    participant R as Redis (GEO)
    participant DB as MySQL

    U->>C: GET /shop/of/type?typeId=&current=&x=&y=
    C->>S: queryShopByType(typeId, current, x, y)
    alt x or y == null
        S->>DB: page(eq type_id, current, size)
        DB-->>S: List<Shop>
        S-->>U: Result.ok(records)
    else 带坐标
        S->>S: from = (current-1)*size; end = current*size
        S->>R: GEOSEARCH shop:geo:{typeId} FROMLONLAT x y BYRADIUS 5000m WITHDIST LIMIT end
        R-->>S: GeoResults<shopId, distance>
        S->>S: 跳过前 from 个，收集 ids 与 distance map
        S->>DB: SELECT * FROM tb_shop WHERE id IN (...) ORDER BY FIELD(id, ...)
        DB-->>S: List<Shop>
        S->>S: shop.setDistance(distanceMap.get(id))
        S-->>U: Result.ok(shops)
    end
```
