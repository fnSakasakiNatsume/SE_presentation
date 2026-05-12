# 时序图 - 签到 & 连续签到统计（Redis BitMap）

## 1. 签到（每月一个 Key，按天置位）

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant C as UserController
    participant S as UserServiceImpl
    participant R as Redis (BitMap)

    U->>C: POST /user/sign
    C->>S: sign()
    S->>S: userId = UserHolder.getUser().id
    S->>S: now = LocalDateTime.now(); 月份后缀 = ":yyyyMM"
    S->>S: key = sign:{userId}:yyyyMM; offset = dayOfMonth - 1
    S->>R: SETBIT {key} {offset} 1
    R-->>S: ok
    S-->>U: Result.ok()
```

## 2. 连续签到天数（从今天往前数 1 的连续位）

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant C as UserController
    participant S as UserServiceImpl
    participant R as Redis

    U->>C: GET /user/sign/count
    C->>S: signCount()
    S->>S: 拼 key = sign:{userId}:yyyyMM；dayOfMonth = N
    S->>R: BITFIELD {key} GET u{N} 0
    R-->>S: List<Long> num (本月截止今天的位串作为十进制)
    alt num == 0
        S-->>U: Result.ok(0)
    else
        loop 直到 (num & 1) == 0
            S->>S: count++; num >>>= 1
        end
        S-->>U: Result.ok(count)
    end
```
