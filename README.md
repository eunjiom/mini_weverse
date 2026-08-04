# 🥨 mini_weverse

> **"서비스 간 데이터 정합성과 접근 제어를 해결하는 MSA 기반 팬 커뮤니티 아키텍처"**

mini_weverse는 커뮤니티·멤버십·실시간 채팅이 독립된 서비스로 분리된 위버스(Weverse) 클론 플랫폼. 팔로우·멤버십·역할(Role) 등 여러 축의 접근 제어가 게시글 열람권부터 채팅 메시지 가시성, WebSocket 접속 권한까지 서비스 경계를 넘나들며 실시간으로 갈리는 구조로, 트랜잭션 아웃박스와 낙관적 락, DB 유니크 제약을 통해 서비스 간 상태 동기화와 동시성 경합을 제어. 비대칭키(RS256) JWT로 발급자와 검증자 사이의 인증 신뢰 경계를 분리해 실제 운영 가능한 수준의 백엔드 시스템을 지향

---

## 📅 1. 프로젝트 개요

### 🚀 1.1 프로젝트 개요

**전체 기간**
2026-06-29 ~

**플랫폼 핵심 가치**
- 구독 상태에 따른 접근 권한을 서비스 경계를 넘어 정확하게 동기화
- 위버스 DM 방식의 실시간 팬-아티스트 소통 경험 제공

**아키텍처 지향점**
- 서비스별 독립 배포·확장이 가능한 MSA 구조 확립 (공통 모듈은 진짜 중복 코드만 공유)
- 다중 인스턴스로의 수평 확장(scale-out)을 전제로 한 설계

**설계 규모 가정**
- 실 서비스 규모는 유저 1만명으로 가정하고 설계/최적화 여부를 판단 (그 이상을 가정한 과설계는 지양)

**주요 타겟 지표**
- 멤버십 상태 변경 이벤트의 무유실 전달(트랜잭션 아웃박스) 보장
- 동시 요청 상황에서도 중복 구독 기간·중복 처리 방지 (DB 유니크 제약, 낙관적 락)

### 🛠️ 1.2 기술스택

| 구분 | 스택 |
|---|---|
| 언어/런타임 | Java 21, Spring Boot 4.1.0 |
| 빌드 | Gradle (멀티모듈: `common`, `community-service`, `chat-service`, `api-gateway`, `notification-service`) |
| API 게이트웨이 | Spring Cloud Gateway (WebFlux, `spring-cloud-starter-gateway-server-webflux`) |
| 웹/API | Spring Web MVC, Spring Validation |
| 인증/인가 | Spring Security, JWT (jjwt 0.13.0, RS256 비대칭키), Spring Session + Redis (세션), OAuth2 Client(카카오 로그인) |
| 채팅 | Spring WebSocket + STOMP, Redis(멤버십 캐시·일일 메시지 카운터), Jsoup(XSS 방지 sanitize) |
| 알림 | Kafka(KRaft 단일 노드, `notification-events` 토픽), Redis(Sorted Set 기반 알림 저장, TTL 7일) |
| 데이터 | Spring Data JPA, PostgreSQL, `schema.sql` 기반 부분/유니크 인덱스 |
| 모니터링 | Zipkin(분산 트레이싱), Prometheus(메트릭 수집), Grafana(시각화), Micrometer |
| 인프라 | Docker(4개 서비스 전체 컨테이너화), GitHub Actions(CD, 4개 서비스 전체 빌드) |

**스택 선정 이유**
- **Spring Cloud Gateway (WebFlux)**: 모든 트래픽이 지나는 지점이라 요청마다 스레드를 점유하지 않는 논블로킹 방식이 적합해서 선택
- **JWT RS256(비대칭키)**: 발급자(community-service)와 검증자(게이트웨이·chat-service)의 책임 분리 목적. 대칭키였다면 검증하는 서비스도 발급 가능한 키를 들고 있어야 해서 이 분리가 불가능
- **WebSocket + STOMP**: 순수 WebSocket만 쓰면 "이 메시지를 어느 클라이언트들에게 보낼지" 라우팅을 직접 구현해야 함. STOMP는 destination(topic) 기반 pub/sub을 기본 제공해서 채팅방 단위 구독·브로드캐스트를 프로토콜 레벨에서 바로 쓸 수 있어 채택

### 🗄️ 1.3 ERD

**community-service DB**

![community-service ERD](image/커뮤니티erd.png)

<details>
<summary><code>FOLLOW</code> — PK 설계 (Long PK vs 복합키 vs Entity 제외)</summary>

**기술**: Follow는 수정(update)이 없고, 다른 엔티티가 FK로 참조하지도 않고, 객체 동일성/더티체킹 같은 JPA 기능이 전혀 필요 없는 순수 조인 테이블. Entity 없이 INSERT/DELETE/조회를 `JdbcTemplate` 네이티브 쿼리로 직접 처리하고, PK는 DDL에서 (follower_id, artist_id) 복합키로 직접 지정

**트레이드오프**

| 옵션 | 설명 | 인덱스 | 보일러플레이트 | 단점의 성격 |
|---|---|---|---|---|
| Long PK 유지 | `Long id` PK + (follower_id, artist_id) unique constraint | 2개 | 없음 | 영구적(작지만 계속) |
| JPA 복합키 | `@EmbeddedId FollowId` + `Persistable` | 1개 | 많음(FollowId 클래스, equals/hashCode, Persistable) | 영구적(코드에 계속 남음), 실질 이득 없음 |
| **Follow를 JPA 엔티티에서 제외 (선택)** | Entity 없이 INSERT/DELETE/조회를 직접 쿼리로 | 1개 | 없음 | 일회성(패턴이 다르다는 이해 비용) |

**선택 이유**: 위 세 옵션 모두 DB 레벨 무결성 보장은 동일하고 복합키의 성능 이점(MySQL 클러스터드 인덱스 근거)은 PostgreSQL(힙 저장 구조)에 적용되지 않아 실질 이득이 없음. 코드에 영구히 남는 보일러플레이트가 없는 3번을 선택

</details>

<details>
<summary><code>MEMBERSHIP_PERIOD</code> — 활성 여부만 볼지, 기간 이력까지 남길지</summary>

**기술**: `MEMBERSHIP`(현재 상태)과 별도로 "실제로 구독 중이었던 기간"을 기록하는 `MEMBERSHIP_PERIOD` 이력 테이블

**트레이드오프**

| 기술 | 장점 | 단점 |
|---|---|---|
| 지금 활성 구독자인지만 확인 | 구현 단순(boolean 캐시 하나로 충분) | 공백 있는 재구독 시, 구독 안 하던 기간에 온 방송 메시지까지 전부 다시 보임 |
| **구독 기간 이력을 별도로 남기고 확인 (선택)** | 실제 구독하지 않았던 기간의 메시지는 재구독해도 안 보임 | 기간 이력 테이블 신설, 조회 쿼리가 복잡해짐, 서비스 간 push 페이로드에 시각 정보 추가 필요 |

**선택 이유**: "구독한 기간만큼만 대화가 보인다"는 요구사항 자체가 정밀한 기간 추적을 요구해서, 현재 상태만 담는 `MEMBERSHIP`만으로는 부족

</details>

**chat-service DB**

![chat-service ERD](image/챗erd.png)

<details>
<summary><code>MEMBERSHIP_PERIOD</code> — 실시간 조회 vs 로컬 복제</summary>

**기술**: community-service와 스키마는 비슷하지만 별도 테이블. community-service가 멤버십 상태를 바꿀 때마다(구독/취소/만료) 아웃박스로 push한 이력을 그대로 복제해서 저장

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 실시간 조회 (매 메시지 조회마다 community-service API 호출) | 구현 단순, 데이터 중복 없음 | 조회마다 네트워크 왕복 발생, community-service 장애 시 채팅 조회까지 같이 막힘 |
| **로컬 복제 (아웃박스 push를 받아 저장, 선택)** | 조회가 로컬 DB만으로 끝나 빠르고, community-service 장애와 분리됨 | 같은 성격의 데이터가 두 서비스 DB에 중복 저장되고, 복제 시점 사이 짧은 지연(eventual consistency) 가능 |

**선택 이유**: 메시지 조회는 채팅 서비스의 핵심 경로라, 매번 원격 호출에 의존하면 지연과 장애 전파 위험이 커서 로컬 복제로 조회 경로를 독립시킴

</details>

### 🏗 1.4 아키텍처

![아키텍처 다이어그램](image/아키텍처%20최종.png)

**MSA 도입**
- **Chat Service를 별도 서비스로 분리**: 채팅은 트래픽 패턴상 스케일 아웃이 잦을 것으로 예상돼, Community Service와 묶여있으면 채팅 부하 때문에 커뮤니티 기능까지 같이 늘려야 하는 낭비가 생김. 독립시켜서 채팅만 따로 확장 가능하게 함
- **로그인/인증은 Community Service에 포함**: 별도 인증 서버로 분리할 만큼 부담이 큰 기능이 아니라고 판단해, Community Service 안에 그대로 둠
- **Notification Service를 별도 서비스로 분리**: 채팅서버가 스케일 아웃될수록 알림 발송량도 같이 늘어나는 구조라, 발행 서비스(Community/Chat)와 분리해 독립적으로 확장 가능하게 함. 발행 쪽과는 REST 직접 호출이 아니라 Kafka로만 연결(N:M 디커플링)
- **다중 서버 구조라 Zipkin 도입**: 서비스가 여러 개로 나뉘면서 요청 하나가 어디서 느려지는지 한 서비스 로그만 봐서는 알 수 없어져서, 분산 트레이싱(Zipkin)으로 병목 지점을 서비스 경계 너머까지 추적

**서비스 간 연결 구조**
- **infrastructure**: User는 API Gateway로만 접근, Gateway가 Community Service·Chat Service·Notification Service로 라우팅. Community Service ↔ Chat Service는 Gateway를 거치지 않고 Internal API로 직접 통신
- **infrastructure ↔ notification**: Community Service·Chat Service는 알림이 발생하면 각자의 아웃박스 테이블에 이벤트를 적재하고, 스케줄러가 5초 주기로 폴링해 Kafka(`notification-events` 토픽)에 발행. Notification Service는 이 토픽만 구독(consume)해 Redis에 저장 — 발행 서비스와 Notification Service는 서로 직접 호출하지 않고 Kafka로만 연결됨
- **infrastructure ↔ 공유 DB**: Community Service는 community DB, Chat Service는 Chat DB를 각자 독립적으로 사용(FK 없음). Notification Service는 자체 DB 없이 Redis만 사용(알림은 TTL 7일짜리 휘발성 데이터라 영구 저장소 불필요). Redis는 세 서비스가 각각 독립적으로 접근(DB들 사이를 잇는 구조 아님)
- **infrastructure ↔ monitoring**: API Gateway, Community Service, Chat Service, Notification Service는 각자 독립적으로 자기 트레이스를 Zipkin에 전송(push, Kafka 발행/소비 구간도 계측에 포함되어 트레이스가 끊기지 않음). 반대로 Prometheus는 이 4개 서비스의 `/actuator/prometheus` 엔드포인트를 하나씩 따로 찾아가 메트릭을 가져옴(pull, 스크레이핑) — 서비스마다 개별로 주고받는 구조라 어느 서비스가 부하 걸렸는지 서비스 단위로 구분 가능
- **monitoring**: Grafana가 Prometheus·Zipkin 양쪽에 조회(query). 메트릭 그래프에서 스파이크가 찍힌 지점(exemplar)을 클릭하면 그 순간의 Zipkin 트레이스로 바로 이동해 원인 확인 가능

---

## 🔑 2. 주요 기능 및 비즈니스 정책

### 📡 2.1 서비스 간 통신

![MSA 서비스](image/msa%20서비스.png)

### 🔐 2.2 API Gateway

<details>
<summary>게이트웨이 + 서비스 이중 JWT 검증 (defense in depth)</summary>

**주요기능 설명**: 게이트웨이가 `JwtVerifier`(공개키)로 먼저 검증 후 역할 기반 라우팅. 이후 각 서비스도 같은 `JwtVerifier`로 한 번 더 검증

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 게이트웨이만 검증 | 검증 로직이 한 곳에만 있어 단순 | 게이트웨이 설정 실수나 우회 요청이 들어오면 각 서비스가 무방비 |
| **게이트웨이 + 각 서비스 이중 검증 (선택)** | 게이트웨이가 뚫려도 각 서비스가 스스로 방어(defense in depth) | 검증 로직이 여러 곳에 존재(단, `common` 모듈로 통일해 중복 코드는 아님) |

**선택 이유**: 게이트웨이의 인가는 "라우팅 단계에서 걸러주는 1차 방어"일 뿐이라, 각 서비스가 스스로도 검증해야 안전하다고 판단. 발급은 community-service만 개인키로 하도록 RS256(비대칭키)을 채택해, 검증 서비스들은 공개키만 가지고도 이중검증이 가능하게 함

</details>

<details>
<summary>WebSocket 핸드셰이크 검증 통일</summary>

**주요기능 설명**: REST 필터와 WS 핸드셰이크 인터셉터, 진입점은 다르지만 검증 로직 자체는 동일한 `JwtVerifier` 사용

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| REST/WS 각각 별도 검증 로직 | 각 진입점에 맞춰 자유롭게 구현 | 한쪽만 고치고 다른 쪽을 놓치면 우회 가능한 구멍 발생 |
| **공통 컴포넌트로 검증 로직 통일 (선택)** | 검증 규칙이 한 곳에서만 관리돼 우회 경로 생길 여지가 적음 | 진입점(필터/인터셉터)마다 연결하는 코드는 별도로 필요 |

**선택 이유**: 검증 로직이 갈리면 한쪽만 고쳐서 생기는 우회 구멍이 생기기 쉬워, 검증 자체는 공통 컴포넌트로 통일하고 진입점만 따로 둠

</details>

### 🖥️ 2.3 Community Service

<details>
<summary>인증 토큰 관리 (Access/Refresh 분리 + RT Rotation)</summary>

**주요기능 설명**: 일반 유저(`/admin/**` 제외 전체)는 access(JWT, 유효기간 30분)/refresh(JWT, Redis 저장 + 쿠키 전달, 유효기간 7일) 분리 + RT Rotation. **관리자(`/admin/**`)는 이와 별도로 완전히 Session 인증** — `SecurityConfig`에 필터체인 자체가 `adminFilterChain`/`apiFilterChain`으로 분리되어 있어, 관리자는 JWT를 아예 쓰지 않고 세션 쿠키로만 인증

**트레이드오프**

| 기술 | 장점 | 단점 | 적용 대상 |
|---|---|---|---|
| **JWT(Stateless, 일반 유저 선택)** | 서버가 상태를 안 가져 확장에 유리, MSA 구조에 적합 | 로그아웃/강제만료 처리가 번거로움(RT 저장소 필요) | `/admin/**` 제외 전체 |
| **Session(Stateful, 관리자 선택)** | 서버에서 즉시 무효화 가능, 구현 단순 | 서버가 상태를 들고 있어야 해서 확장 시 세션 클러스터링 필요 | `/admin/**` |
| RT 재사용(갱신해도 기존 RT 유지) | 구현 단순 | RT가 탈취되면 만료 전까지 계속 악용 가능 | - |
| **RT Rotation(갱신 시 새 RT 발급, 선택)** | 탈취된 RT가 한 번 쓰이면 무효화되어 피해 범위 축소 | 멀티 디바이스 동시 갱신 시 race condition 가능(멀티 디바이스 미지원이라 리스크로 수용) | `/admin/**` 제외 전체 |

**선택 이유**: 일반 유저는 트래픽이 많고 향후 확장을 고려해 stateless한 JWT를 선택. 반대로 관리자는 소수이고 즉각적인 권한 회수(강제 로그아웃)가 확장성보다 더 중요해 Session을 선택 — 그래서 하나의 방식으로 통일하지 않고 역할별로 필터체인 자체를 분리함. 일반 유저 쪽은 탈취 시 피해를 최소화하기 위해 RT도 재사용이 아닌 Rotation 방식을 채택

</details>

<details>
<summary>회원/게시글/댓글 소프트 삭제</summary>

**주요기능 설명**: 탈퇴/삭제 시 실제로 행을 지우지 않고 `deletedAt`만 채움. `@SQLRestriction("deleted_at IS NULL")` + `@NotFound(action = IGNORE)`로 조회에서 자동 제외

**트레이드오프**

| 기술 | 장점 | 단점 |
|---|---|---|
| Hard Delete | 구현 단순, 저장 공간 절약 | 게시글·댓글 등 연관 데이터 관리 어렵고 복구 불가, FK 제약 위반 위험 |
| **Soft Delete (선택)** | 데이터 복구 가능, 연관 데이터(댓글·팔로우·멤버십 이력) 유지 가능 | 조회 시 삭제 여부를 항상 고려해야 함 |

**선택 이유**: 탈퇴한 유저의 글이 다른 유저의 댓글·팔로우·멤버십 이력과 얽혀 있어, 하드 삭제 시 FK 제약이 깨지거나 이력이 통째로 사라짐. "글은 남고 작성자 표시만 사라지는" 자연스러운 동작을 위해 소프트 삭제 채택

</details>

<details>
<summary>멤버십 만료 처리 (실시간 vs 배치)</summary>

**주요기능 설명**: `MembershipExpirationScheduler`가 매일 자정(00:00)에 만료 대상을 한 번에 처리. 배치와 유저의 갱신 요청이 겹치면 낙관적 락(`@Version`)으로 배치 쪽이 조용히 건너뜀

**트레이드오프**

| 기술 | 장점 | 단점 |
|---|---|---|
| 조회 시점 실시간 계산 | 배치 없이 항상 최신 상태 반영 | 매 조회마다 계산 비용, 다른 로직에서도 매번 재계산 필요 |
| **배치 스케줄러로 주기적 일괄 전환 (선택)** | status 컬럼이 항상 최신값으로 유지되어 다른 로직은 단순 조회만 하면 됨 | 배치 주기(하루 1회) 동안 실제 만료와 반영 사이 지연 가능 |

**선택 이유**: 멤버십 만료는 실시간성이 크리티컬하지 않다고 판단(결제 연동 없는 MVP 특성상 "정확히 만료 시각에" 끊을 필요 없음). 배치 방식이 훨씬 단순하고 부하도 예측 가능

</details>

<details>
<summary>멤버십 변경 알림 (동기 호출 vs 트랜잭션 아웃박스)</summary>

**주요기능 설명**: 구독/만료 처리(DB 트랜잭션)와 chat-service 알림(네트워크 호출)을 분리 — "상태 변경 커밋 + 이벤트 적재"까지만 원자적으로 보장하고, 실제 전송은 스케줄러가 5초마다 폴링하며 재시도

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 동기 호출(상태 변경 트랜잭션 안에서 바로 chat-service 호출) | 구현 단순, 실시간 반영 | chat-service가 그 순간 응답 안 하면 트랜잭션 전체 실패, 분산 트랜잭션 없이는 두 작업을 하나로 묶을 수 없음 |
| **트랜잭션 아웃박스 (선택)** | chat-service 장애/다운타임과 무관하게 이벤트 유실 없음 | 아웃박스 테이블·발행자·폴링 로직 추가 필요, 즉시 반영은 아니고 최대 폴링 주기(5초)만큼 지연 |

**선택 이유**: 향후 서비스를 더 쪼개고 메시지 브로커(Kafka 등) 도입 계획이 있어서, 지금 아웃박스 테이블을 만들어두면 나중에 발행 소스만 REST 호출에서 브로커 발행으로 바꾸면 되고 쓰기 경로(트랜잭션 안에서 이벤트 적재)는 그대로 유지됨

</details>

### 💬 2.4 Chat Service

<details>
<summary>위버스 DM 방식 메시지 가시성</summary>

**주요기능 설명**: 아티스트 메시지는 방을 구독 중인 모두에게 방송(`/topic/rooms/{artistId}/broadcast`), 팬 메시지는 보낸 본인 + 아티스트에게만(`/queue/...`) 전달

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 팬별로 메시지 복제 저장 | 조회 쿼리가 단순해짐(본인 것만 필터 없이 조회) | 팬 수만큼 저장 공간 낭비, 아티스트 메시지 하나가 팬 수만큼 복제됨 |
| **STOMP topic/개인 큐 라우팅만으로 가시성 분리 (선택)** | 저장 구조가 단순한 1개 테이블(`chat_messages`)로 유지됨 | 조회 시 가시성 판단 로직이 쿼리에 들어가야 함 |

**선택 이유**: 메시지를 팬별로 복제 저장하지 않고 STOMP의 라우팅 기능만으로 가시성을 나눠서, 저장 구조를 단순하게 유지

</details>

<details>
<summary>메시지 조회 가시성 판단 기준</summary>

**주요기능 설명**: 단순히 "지금 활성 회원인가"가 아니라 `MembershipPeriod` 기간 이력과 메시지 시각을 대조하는 쿼리(`findVisibleMessages`)로 판단

**트레이드오프**

| 기술 | 장점 | 단점 |
|---|---|---|
| 지금 활성 구독자인지만 확인 | 구현 단순(boolean 캐시 하나로 충분) | 공백 있는 재구독 시, 구독 안 하던 기간에 온 방송 메시지까지 전부 다시 보임 |
| **구독 기간 이력 대조 (선택)** | 실제 구독하지 않았던 기간의 메시지는 재구독해도 안 보임 | 조회 쿼리가 EXISTS 서브쿼리로 복잡해짐, 서비스 간 push 페이로드에 시각 정보 추가 필요 |

**선택 이유**: "구독한 기간만큼만 대화가 보인다"는 요구사항 자체가 정밀한 기간 추적을 요구해서 후자를 선택 (ERD `MEMBERSHIP_PERIOD`와 동일한 판단 기준)

</details>

<details>
<summary>하루 팬 메시지 5건 제한</summary>

**주요기능 설명**: `FanMessageDailyQuota`(Redis 원자적 INCR)로 팬이 한 아티스트에게 보낼 수 있는 메시지 수를 하루 5건으로 제한. 카운터가 이번 요청으로 처음 생긴 경우(count == 1)에만 자정 만료를 설정 — 매 요청마다 TTL을 다시 세팅하면 "마지막 메시지 시각 + 하루"로 만료 시점이 계속 밀려서 자정 초기화가 안 됨

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 무제한 | 구현 불필요, 팬 자유도 높음 | 소수의 팬 메시지가 아티스트 인박스를 도배할 위험 |
| 로컬 카운터(Caffeine) | 구현 단순, 조회 빠름 | chat-service가 다중 인스턴스로 늘어나면 인스턴스마다 카운터가 따로 놀아 실제로는 (인스턴스 수 × 5)건까지 통과됨(재현 테스트로 확인된 문제) |
| **Redis 원자적 INCR (선택)** | 인스턴스가 몇 개든 카운터를 공유해 정확히 5건으로 제한됨 | Redis 네트워크 홉이 로컬 카운터보다 추가됨 |

**선택 이유**: 실제 위버스의 "하트 유사 상호작용" 정책을 단순화해 반영한 제한이지만, chat-service를 다중 인스턴스로 스케일 아웃할 계획이 있어 인스턴스 간 공유가 안 되는 로컬 카운터로는 제한이 무의미해짐 — Redis의 원자적 INCR로 전환해 인스턴스 수와 무관하게 정확한 카운팅 보장

</details>

<details>
<summary>멤버십 확인 방식 (원격 호출 vs 캐시)</summary>

**주요기능 설명**: `MembershipCache`(Redis, 자정 만료)로 활성 여부를 캐싱. community-service와 같은 Redis 서버를 쓰되 DB index로 분리(chat-service: 1, community-service: 0)해 키가 안 섞이게 함. community-service가 상태를 바꿀 때 `markActive`/`markExpired` push로 캐시를 즉시 정정. Redis 조회/저장이 실패하면 예외를 던지지 않고 캐시 미스로 간주해 원격 확인으로 폴백(fail-safe)

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 매번 community-service에 동기 호출 | 항상 최신 상태, 캐시 관리 불필요 | 채팅 지연이 원격 호출 왕복시간에 그대로 종속 |
| 로컬 캐시(Caffeine) | 조회가 가장 빠름(네트워크 홉 없음) | chat-service가 다중 인스턴스로 늘어나면 "A 인스턴스에서 활성 확인됨"이 "B 인스턴스에서는 모름"으로 어긋남(재현 테스트로 확인된 문제) |
| **Redis 공유 캐시 (선택)** | 인스턴스가 몇 개든 같은 상태를 공유해 일관성 유지 | 로컬 캐시보다는 느리고, Redis 자체 장애 가능성을 별도로 처리해야 함 |

**선택 이유**: WS로 메시지를 보낼 때마다 community-service에 동기 호출하면 채팅 지연이 커져서 캐싱이 필요했지만, 로컬 캐시(Caffeine)는 다중 인스턴스 스케일 아웃 계획과 정면으로 충돌해 Redis 공유 캐시로 전환. 단, community-service 호출 자체가 실패한 경우는 캐시하지 않고 그 요청만 실패 처리 — 일시 장애를 "비활성"으로 캐시에 굳혀버리면 진짜 활성 회원이 자정까지 계속 차단될 수 있기 때문

</details>

<details>
<summary>멤버십 push 중복 수신 방어</summary>

**주요기능 설명**: 아웃박스는 최소 1번 전달(at-least-once)이라 같은 이벤트가 두 번 올 수 있음. "바로 저장 시도 후 실패를 잡는" 방식(`DataIntegrityViolationException` catch)과 DB 부분 유니크 인덱스(`uk_membership_periods_open`)로 이중 방어

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 먼저 확인하고 없으면 저장 | 구현이 직관적 | 두 요청이 거의 동시에 오면 둘 다 확인을 통과해버리는 경합 발생 |
| **바로 저장 시도 + 실패 캐치 + DB 유니크 인덱스 (선택)** | 동시 요청에도 DB가 최종 방어선이 되어 중복이 원천 차단됨 | 제약 위반 예외를 의미있는 처리로 변환하는 코드 추가 필요 |

**선택 이유**: 애플리케이션 로직만으로는 동시 요청 경합을 완전히 막을 수 없어서, DB의 부분 유니크 인덱스(같은 팬-아티스트 조합에 "열린" 기간은 하나만)를 실질적인 최종 방어선으로 둠

</details>

### 📊 2.5 Monitoring

<details>
<summary>Zipkin 전송 방식 (HttpClient 센더 vs URLConnectionSender)</summary>

**주요기능 설명**: 3개 서비스에 Actuator+Micrometer 계측을 추가해 Zipkin(분산 트레이싱)·Prometheus(메트릭)를 연동. `docker-compose.monitoring.yml`을 별도로 분리해 모니터링 스택만 독립적으로 기동 가능하게 함

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 기본 HttpClient 센더 | Spring Boot 기본값, 별도 설정 불필요 | 앱 환경에서 `ConnectException` 발생(격리 환경에선 재현 안 돼 원인 100% 확정은 못함, 동시성/부하 조건으로 추정) |
| **URLConnectionSender (선택)** | 실제 앱 환경에서 안정적으로 동작 확인됨 | 기본값이 아니라 명시적으로 교체 필요 |

**선택 이유**: 원인을 완전히 확정하진 못했지만, 워크어라운드로 안정 동작이 검증돼서 채택. 모니터링 스택을 별도 compose 파일로 분리한 것도 서비스 배포와 모니터링 인프라 기동을 독립시키기 위함

</details>

<details>
<summary>메트릭/트레이스 수집 방식 (pull vs push)</summary>

**주요기능 설명**: Prometheus는 각 서비스의 `/actuator/prometheus`를 직접 찾아가 가져오고(pull), Zipkin은 각 서비스가 스스로 전송(push)

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 전부 pull로 통일 | 수집 방식이 하나라 관리 단순 | Zipkin은 원래 push 기반이라 억지로 pull화하면 스팬 수집 구조를 다시 짜야 함 |
| **서비스별 표준 방식대로 분리 (선택)** | Prometheus는 pull, Zipkin은 push가 각자의 표준 방식이라 그대로 사용 | 두 가지 수집 방식이 공존해 인프라 구성이 한 가지로 안 통일됨 |

**선택 이유**: 두 도구 다 표준 수집 방식이 이미 정해져 있어서 억지로 통일하지 않음. 서비스마다 개별로 주고받는 구조라 어느 서비스가 부하 걸렸는지 서비스 단위로도 구분 가능

</details>

<details>
<summary>Grafana-Zipkin exemplar 연동</summary>

**주요기능 설명**: 메트릭 그래프의 스파이크 지점(exemplar)을 클릭하면 그 순간의 Zipkin 트레이스로 바로 이동(Prometheus `--enable-feature=exemplar-storage` + Grafana `exemplarTraceIdDestinations`)

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| exemplar 없이 수동 대조 | 추가 설정 불필요 | 메트릭에서 이상 시각을 보고 Zipkin에서 그 시간대를 따로 검색해야 함(수동, 여러 단계) |
| **exemplar 연동 (선택)** | 스파이크 지점 클릭 한 번으로 해당 트레이스 바로 확인 | Prometheus·Grafana 양쪽에 추가 설정 필요 |

**선택 이유**: 장애 원인 분석 시간을 단축하기 위해, 메트릭과 트레이스를 수동으로 대조하는 과정 자체를 없앰

</details>

### 🔔 2.6 Notification Service

<details>
<summary>알림 발행 경로 (REST 직접 호출 vs Kafka 이벤트 발행)</summary>

**주요기능 설명**: 멤버십(2.3)과 동일한 트랜잭션 아웃박스로 이벤트를 적재하되, 최종 발행 목적지만 REST 호출이 아니라 Kafka(`notification-events` 단일 토픽, 타입 필드로 팔로우·멤버십·게시글·댓글·채팅 등 6종 이벤트 구분)로 전환. 같은 유저의 알림 순서가 뒤바뀌지 않도록 `targetUserId`를 파티션 키로 사용

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 발행 서비스가 Notification Service를 REST로 직접 호출 | 새 인프라(Kafka) 불필요, 구현 단순 | 발행 서비스가 늘수록(Community/Chat 등) 각자 Notification Service 주소·재시도 로직을 알아야 함(N:1 결합) |
| **Kafka 이벤트 발행 (선택)** | 발행 서비스는 토픽 하나만 알면 되고, 구독자(소비 서비스)가 늘어나도 발행 쪽은 변경 없음(N:M 디커플링) | Kafka 인프라 추가, 파티션·컨슈머 그룹 등 운영 개념 추가 |

**선택 이유**: 멤버십 아웃박스(2.3) 설계 당시 "발행 소스만 REST에서 브로커로 바꾸면 되도록" 열어둔 구조를, 발행 주체가 Community Service·Chat Service 두 곳으로 늘고 알림 종류도 6종으로 늘어난 시점에 실제로 전환

</details>

<details>
<summary>아웃박스 로직 위치 (서비스별 개별 구현 vs common 모듈 이관)</summary>

**주요기능 설명**: `NotificationOutboxEvent`/`Repository`/`Publisher`/`Processor`를 Community Service·Chat Service 양쪽에 따로 만들지 않고 `common` 모듈에 하나만 두고 두 서비스가 그대로 사용

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| 서비스마다 동일한 아웃박스 클래스를 각자 구현 | 서비스 간 코드 결합 없음 | 완전히 같은 코드(엔티티·리포지토리·발행자·프로세서)가 토씨 하나 안 다르게 중복 |
| **common 모듈로 이관 (선택)** | 코드 중복 제거, 폴링 주기(5초)·배치 크기(100건) 등 정책 변경 시 한 곳만 수정 | common 의존성이 하나 늘어남(배포 독립성 자체는 안 깨짐 — common은 라이브러리로 끼워지는 것일 뿐) |

**선택 이유**: 내용까지 완전히 동일하게 중복된 코드만 common으로 이관한다는 기존 원칙을 그대로 적용 — 멤버십 아웃박스와 로직이 완전히 같아서 `BaseTimeEntity`와 동일한 이유로 common에 둠. 반대로 도메인 엔티티나 서비스별 에러코드처럼 내용이 실질적으로 다른 것은 계속 각 서비스에 남겨둠

</details>

<details>
<summary>알림 저장 방식 (DB 영구 저장 vs Redis 휘발성 저장)</summary>

**주요기능 설명**: 유저별 Redis Sorted Set(`notification:{userId}`, score=발생 시각)에 저장. 쓸 때마다 TTL(기본 7일)보다 오래된 항목은 정리하고, 마지막 알림 후 7일간 활동이 없으면 키 자체가 만료되어 사라짐. 읽음 처리는 알림 건별이 아니라 유저당 `lastReadAt` 값 하나만 관리 — 그 이후 도착한 알림은 전부 안읽음으로 계산. 저장 시 발생 시각은 시스템 기본 타임존(`ZoneId.systemDefault()`) 기준으로 epoch millis 변환 — 처음에 UTC로 임의 고정 변환했다가 `lastReadAt`(`System.currentTimeMillis()` 기준) 값과 시차만큼 어긋나 모든 알림이 항상 미래로 계산되어 영원히 안읽음 처리되던 버그를 검증 중 발견해 수정

**트레이드오프**

| 방식 | 장점 | 단점 |
|---|---|---|
| PostgreSQL에 알림별 row + read 플래그 저장 | 영구 보관, 알림 건별 읽음 처리 가능 | 알림은 최근 며칠만 의미 있는 휘발성 정보인데 영구 저장소를 쓰는 건 과함, 알림마다 읽음 UPDATE 필요 |
| **Redis Sorted Set + TTL, lastReadAt 단일값 (선택)** | 알림 성격(최근 N일만 유효)에 맞게 자동 만료, 읽음 처리가 값 하나만 갱신하면 돼서 단순 | 알림 하나만 골라 읽음 처리하는 기능은 불가(전체 unread 개수만 계산) |

**선택 이유**: 이번 프로젝트 범위에서 "알림 개별 읽음 처리" 요구사항이 없고, 알림은 최근 것만 의미 있는 휘발성 데이터라 TTL이 있는 Redis가 더 적합하다고 판단

</details>

---

## 🧪 3. 테스트 및 성능 검증

**📌 테스트 사양**
- 서버 사양: 외부 Ubuntu 24.04.3 LTS, 총 메모리 15GB
- 배포 환경: 온프레미스 단일 인스턴스(서비스당 컨테이너 1개) — AWS 등 클라우드 스케일 아웃은 아직 미적용 상태에서 측정
- Docker 컨테이너 기반 / 앱 서비스 4개(community/chat/notification/gateway) 각각 CPU 최대 사용량 2.0, 메모리 최대 사용량 1GB 제한 (인프라·모니터링 컨테이너는 미적용)
- 관측 도구: k6(클라이언트 응답/처리량) · Zipkin(분산 트레이싱, 샘플링 30%) · Grafana(인프라 자원)

---

### 3.1 부하·병목·인프라 종합 테스트

**📌 테스트 구성**
- 테스트 도구: k6, Zipkin, Grafana
- 테스트 시나리오: 회원가입→로그인→탐색→팔로우→구독→글쓰기 6단계 반복, VU 0→50(30s)→50유지(60s)→50→100(30s)→100유지(60s)→100→0(30s), 총 3분 30초
- 추가 테스트: 병목으로 확인된 회원가입/로그인만 떼어내 1300VU로 별도 재현해, 더 큰 규모에서도 같은 CPU 병목 패턴이 유지되는지 확인
- 테스트 목표: 처리량/응답시간 프로파일 확인 + 병목 구간 특정 + 자원 사용률(CPU/메모리) 확인
- 성공 기준: p(95)<1000ms, 실패율<5%

**테스트 결과**

| 테스트 항목 | 30VU | 100VU (4회 재현) | 1300VU |
|---|---|---|---|
| p(95) | 484ms | 2.59~3.02s | 4.2s |
| 실패율 | 0% | 0% | 0% |
| 판정 | ✅ 통과 | ❌ 임계값(1s) 초과 | ❌ 임계값(1s) 초과 |

![1300VU 엔드포인트별 응답시간](image/bcrypt-bottleneck-chart.svg)

![Grafana CPU/로드/스레드](image/모니터링2.png)

**📌 설명**
- signup/login만 다른 구간보다 6~10배 느림 — 나머지는 100VU에서도 전부 1초 안쪽
- Zipkin: `secured request` 구간이 자식 span 없이 순수 연산시간(전체의 99%+)으로 확인 — DB/네트워크 대기가 아니라 BCrypt 해싱 자체의 CPU 비용
- Grafana: CPU 100% 포화, 로드평균 30.7(2코어 기준 15배), 메모리/GC/스레드는 전부 정상 — k6·Zipkin·Grafana 3중으로 CPU 병목 확정
- 1300VU까지 올려도 평균 응답시간 2.8s·CPU 95~100%로 같은 CPU 병목 패턴이 유지될 뿐, 실패율은 여전히 0% — 서버가 죽지 않고 순차 처리됨을 확인

**결론**: 이 병목은 BCrypt가 무차별 대입 공격을 막기 위해 의도적으로 느리게 설계된 결과이므로 **수정하지 않기로 결정**. CPU 2코어 제한은 테스트용이지 실제 배포 스펙이 아니며, 1300VU까지 늘려도 실패율 0%가 유지되는 것을 확인해 유저 1만명 규모에서는 충분히 감당 가능하다고 판단

---

### 3.2 동시 멤버십 취소 — 락 방식 비교 테스트

**📌 테스트 구성**
- 테스트 도구: k6(`k6-concurrent-cancel.js`)
- 테스트 시나리오: 이미 구독 중인 유저 1명의 멤버십 1건에 취소 요청을 VU 100개가 동시 전송(더블클릭·클라이언트 재시도로 같은 row에 중복 요청이 몰리는 상황 재현). 비관적 락(현재 코드)·낙관적 락+재시도·Redis 분산락 3방식을 코드만 바꿔가며 동일 시나리오로 각 3회씩 비교
- 테스트 목표: 이중취소 방지(Lost Update 없음) 확인 + 방식별 응답시간 비교
- 성공 기준: 취소 성공 정확히 1건(나머지 99건은 이미 취소됨으로 정상 거부), 500 에러 0건

![락 방식별 응답시간 비교](image/lock-comparison-chart.svg)

**테스트 결과 (3회 평균, 로컬 Docker 환경)**

| 항목 | 비관적 락(현재) | 낙관적 락+재시도 | Redis 분산락 |
|---|---|---|---|
| 취소 성공 | 1건 | 1건 | 1건 |
| 500 에러 | 0건 | 0건 | 0건 |
| 평균 응답시간 | 580ms | 781ms | 585ms |
| p95 | 931ms | 880ms | 698ms |

**📌 설명**
- 세 방식 모두 100개 동시 요청 중 정확히 1건만 성공, 나머지 99건은 이미 취소된 상태로 정상 거부되어 중복 취소(Lost Update) 없음을 확인
- 평균 응답시간 기준 비관적 락과 Redis 분산락이 비슷하고, 낙관적 락이 가장 느림 — 버전 충돌로 실패한 요청이 재시도되며 응답시간이 늘어남

**결론**: **비관적 락 유지** — 정합성(3방식 모두 500 에러 0건·중복 취소 0건 실측 확인)은 동일. Redis 분산락이 p95(698ms)로 셋 중 가장 빨랐지만, 이 정도 동시성(한 row에 100개 이상 몰림)은 실제로는 한 유저가 취소 버튼을 더블클릭하는 수준(2~3개 동시 요청)에서만 발생해 그 차이가 체감되지 않는 상황. 이런 경합에는 기존 DB 락만으로 충분히 방어되므로, Redis 인프라를 추가로 들이는 비용 없이 비관적 락을 유지하기로 결정

---

### 3.3 아웃박스 발행 지연 측정

**📌 테스트 구성**
- 테스트 도구: k6(`k6-chat-to-notification.js`, 2초 간격 폴링·최대 20초) + Zipkin
- 테스트 시나리오: 팬 회원가입→로그인→구독→아티스트 방송 메시지 수신까지, 아웃박스→Kafka→Redis 저장 전 구간의 실제 지연 측정
- 테스트 목표: 폴링 주기(5초) 기준 예상 지연(5초+α) 검증

**테스트 결과**

| 상황 | 지연 |
|---|---|
| 백로그 없음 | 7034ms |
| 소형 백로그(5건) | 약 32분 |
| 대형 백로그(24,221건) | 20초 테스트 창 초과 |

![알림이 실제로 처리된 순간](image/알림이%20실제로%20처리된%20순간.png)

**📌 설명**
- 백로그 없을 때는 가설(5초 폴링+α)과 일치
- 아웃박스 발행자가 ID 순차 처리(FIFO, 5초마다 100건) 구조라, 앞에 밀린 PENDING이 있으면 새 이벤트가 그만큼 뒤로 밀림
- 대규모 트래픽(한 아티스트에 이미 다수의 팬이 구독 중인 상황)을 가정해 fan-out 지연을 측정 — 구독자 2만명대 규모에서 지연이 20초 이상 벌어지는 것을 실측으로 확인

**결론**: 아웃박스+Kafka 설계 자체는 의도대로 정상 동작. 다만 지금은 온프레미스 단일 인스턴스에서 아웃박스 발행자가 5초 폴링+FIFO 순차 처리로만 동작해, 대규모 fan-out(구독자 2만명대) 상황에서는 지연이 커질 수 있음을 실측으로 확인 — AWS 전환과 함께 발행자를 스케일 아웃할 예정이라 이 지연은 개선될 것으로 예상 (→ "4. 프로젝트 진행 현황" 참고)

---

## 🚧 4. 프로젝트 진행 현황

**구현 완료**
- [x] community-service 구현
  회원가입/로그인(카카오 OAuth 포함)·팔로우·유료 멤버십(구독/갱신/취소)·게시글/댓글, 관리자 세션 인증까지 구현
- [x] api-gateway 구현
  Spring Cloud Gateway(WebFlux) 단일 진입점, JWT 1차 검증 + 역할 기반 라우팅 인가, WebSocket 핸드셰이크 프록시
- [x] chat-service 구현
  WebSocket/STOMP 기반 위버스 DM 방식 실시간 채팅(아티스트 방송/팬 개인 메시지), 멤버십 기간 이력 기반 메시지 가시성, XSS 방어
- [x] notification-service 구현
  트랜잭션 아웃박스 + Kafka 기반 이벤트 알림(팔로우·멤버십·게시글·댓글·채팅 6종), Redis TTL 저장
- [x] Zipkin·Prometheus·Grafana 모니터링 구축
  분산 트레이싱 + 메트릭 수집·시각화, Grafana에서 메트릭 스파이크 클릭 시 해당 Zipkin 트레이스로 바로 이동
- [x] Swagger(OpenAPI) 문서화
  4개 서비스 전부 springdoc 기반 자체 Swagger UI 노출

**테스트 진행**
- [x] Zipkin 기반 병목 테스트
  BCrypt 해싱으로 인한 CPU 포화 발견 → k6·Zipkin·Grafana 3중 검증 후 의도된 보안 설계로 판단해 미조치 결정
- [x] k6 기반 동시성·부하 테스트
  멤버십 이중취소 락 3방식(비관/낙관/Redis) 비교 실측 → 비관적 락 유지 결정, 아웃박스 fan-out 지연 실측 → 구독자 2만명대에서 20초 이상 지연 확인

**테스트 결과 기반 업데이트 예정**
- [ ] Service Discovery(Eureka) 도입
  AWS 전환에 따른 Auto Scaling 등을 대비해, 인스턴스가 늘어나도 자동 등록/탐색되도록 게이트웨이에 서비스 디스커버리 적용 예정
- [ ] AWS 전환 + 아웃박스 발행자 스케일아웃
  fan-out 지연 실측 결과에 따라, 아웃박스 발행자를 다중 인스턴스로 확장해 폴링 처리량 개선 예정

---

## 🔗 외부 문서

- 노션: https://app.notion.com/p/Mini_weverse-3a5ed78be0878023becbfa96780b639e?source=copy_link
- Swagger: https://ndolphin.com/mini-weverse/swagger-ui/index.html?urls.primaryName=community-service
