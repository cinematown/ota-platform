# Software Architecture

Last updated: 2026-07-20

## 1. 문서 목적

이 문서는 Firmware OTA Platform의 제품 구조와 구성요소의 책임을 정의한다.
현재 구현 진척도와 바로 다음 작업은 `DEVELOPMENT_STATUS.md`에서 관리한다.
`DESIGN.md`는 UI 디자인 전용 문서다.
Device Client 통합 계약과 플랫폼/장치 개발자의 상세 책임은
[`DEVICE_CLIENT_CONTRACT.md`](DEVICE_CLIENT_CONTRACT.md)에서 정의한다.

문서에서 사용하는 상태는 다음과 같다.

- **현재**: 저장소에 구현되어 있거나 통합 실험으로 확인함
- **결정**: 제품 방향은 확정했지만 구현이 끝나지 않음
- **계획**: 이후 milestone에서 구현할 항목
- **선택 사항**: 실제 요구가 생길 때만 추가함

## 2. 제품 목표와 사용 시나리오

플랫폼은 인터넷에 직접 연결 가능한 MCU 및 Embedded Linux 장치를 표준
LwM2M으로 관제하고, telemetry 수집과 firmware OTA lifecycle을 관리한다.

목표 사용 시나리오는 다음과 같다.

1. 장치 개발자가 제품에 표준 LwM2M Client와 장치별 안전 업데이트 기능을
   통합한다.
2. 사용자는 서버에서 자신이 소유한 장치를 등록하거나 삭제한다.
3. 장치는 고유 endpoint name과 credential로 중앙 LwM2M Server에 직접
   연결하고 등록한다.
4. 같은 제품의 장치 한 대를 새로 추가할 때 서버나 Client 코드를 수정하거나
   다시 배포하지 않는다. 등록과 credential provisioning만 수행한다.
5. 사용자는 UI에서 장치 telemetry를 확인하고, 한 대 또는 여러 대에 firmware
   update를 배포한다.

새로운 제품 모델이 표준 OMA/IPSO Object만 사용한다면 서버 코드 변경 없이
등록할 수 있어야 한다. 새로운 custom Object가 필요한 경우에도 향후 Object
model과 UI metadata를 등록하는 것으로 끝내며 Java 서버를 다시 빌드하지 않는
구조를 목표로 한다.

## 3. 핵심 아키텍처 결정

### 3.1 Direct LwM2M을 기본 경로로 사용

인터넷에 직접 연결 가능한 장치는 중앙 서버에 직접 LwM2M Client로 등록한다.
장치와 서버 사이에 항상 거치는 Gateway를 두지 않는다.

```text
  [Spring Admin UI]                  [hawkBit UI]
 Device / Credential / Object       Firmware / Distribution / Action
          |                                      |
          v                                      v
 [Spring OTA Integration Server] <-- DMF --> [hawkBit]
  |-- Device Registry                         |-- Software Repository
  |-- Credential lifecycle                    |-- Distribution / Rollout
  |-- Embedded Leshan                         |-- Action history
  |-- DMF-LwM2M Adapter                       |-- Artifact storage
  |-- Artifact CoAP(S) proxy                  |
  |-- PostgreSQL                              |-- PostgreSQL / filesystem
          |                                      |
          +----------- RabbitMQ -----------------+
          |
          | LwM2M CoAP/DTLS + Artifact CoAP(S)
          v
 [MCU / Embedded Linux LwM2M Client]
```

이 구조는 개인 custom protocol로 돌아가는 것이 아니다. LwM2M Client와 LwM2M
Server가 직접 통신하는 표준 구조를 사용한다.

### 3.2 Gateway는 선택적 호환 계층

Gateway는 다음과 같은 실제 요구가 생길 때 별도 구성요소로 추가한다.

- BLE, CAN, RS-485 등 비 IP 장치
- LwM2M Client를 탑재할 수 없는 레거시 장치
- 현장망 단절 시 로컬 제어가 필요한 장치
- 여러 장치의 데이터 집계가 반드시 필요한 제한된 uplink

현재 대상으로 삼는 직접 IP 장치를 위해 LwM2M Gateway Object `/25`, prefixed
Object routing, 하위 장치 전용 protocol을 구현하지 않는다.

### 3.3 장치가 곧 LwM2M endpoint

제품에서 관리하는 단위는 Gateway가 아니라 개별 Device다. 각 Device는 다음
정보로 식별되고 소유권과 연결된다.

```text
User
  -> owns Device
       -> endpointName
       -> credential
       -> deviceProfile
       -> reported LwM2M Objects
```

Gateway Object Instance와 별도의 `deviceId`를 연결하는 계층은 기본 구조에
존재하지 않는다.

## 4. Central Server

상태: **LwM2M, Device Registry와 hawkBit OTA 수직 경로 구현 완료**

### 4.1 Spring OTA Integration Server

Spring은 장치 관리와 protocol integration을 담당한다.

- PostgreSQL Device Registry와 credential lifecycle
- AES-256-GCM 기반 PSK 암호문 저장
- Embedded Leshan LwM2M Server
- LwM2M Registration과 Object Read/Observe/Execute
- hawkBit DMF 명령을 Object 5 명령으로 변환
- Object 5 상태와 결과를 hawkBit Action 상태로 변환
- hawkBit artifact staging과 Device용 CoAP(S) proxy
- Device, credential과 LwM2M Object용 Admin UI

Leshan은 Spring 내부 library이며 별도 제품 서버가 아니다.

### 4.2 hawkBit

hawkBit은 firmware OTA orchestration을 담당한다.

- Software Module과 artifact
- Distribution Set
- Target Action과 상태 이력
- rollout과 배포 정책
- firmware 운영자 UI
- artifact 원본 저장

Spring은 hawkBit 내부 DB를 직접 수정하지 않고 Management API와 DMF 계약을
통해서만 연동한다.

### 4.3 Source of truth

| 데이터 | Source of truth |
|---|---|
| Device, endpoint, enabled 상태 | Spring PostgreSQL |
| Device credential과 보안 정책 | Spring PostgreSQL |
| 실시간 LwM2M Registration | Leshan runtime |
| Object 상태와 telemetry | Spring |
| Firmware metadata와 binary | hawkBit |
| Distribution, Action과 rollout 이력 | hawkBit |
| hawkBit Target | Spring Device의 OTA projection |

Device를 Spring DB에 등록하면 hawkBit Target을 자동 생성한다. LwM2M 등록 시
DB에 존재하고 enabled인 Device만 허용하고 hawkBit에 연결 상태를 전달한다.
hawkBit UI에서 Target을 별도로 수동 등록하지 않는다.

## 5. Device Client와 Device SDK

### 5.1 구현 정책

서버는 특정 Client SDK에 종속되지 않는다. 표준에 맞는 여러 Client가 같은
서버와 상호운용되어야 한다.

- 현재 Linux reference client: C++20 + Eclipse Wakaama C library
- Zephyr 기반 MCU 후보: Zephyr native LwM2M engine
- 다른 MCU/Linux 제품: 자원, 보안, 라이선스, `/5` 완성도에 맞는 conforming
  Client 선택

현재 저장소의 `clients/linux-reference/`는 BMS 장치 하나를 표현하는 직접 연결
Linux reference client다. 서버 상호운용과 protocol lifecycle 검증을 위한
reference implementation이며, 범용 MCU SDK나 STM32 배포 binary가 아니다.

제품 목표 Profile은 LwM2M `1.1`과 Firmware Update Object `/5` version `1.2`다.
현재 Linux reference client는 `/5` version `1.0` Read scaffold이므로 v1.2로
확장해야 한다.

### 5.2 현재 reference client

```text
main.cpp
  -> ReferenceClientApp
       -> wakaama_client_context_t
       -> Wakaama context
       -> LwM2M Object array
       -> UDP socket and server connections

object_bms.c
  -> BMS custom Object /33000

wakaama_hooks.c
  -> Wakaama POSIX UDP platform adapter

reference_client_smoke.cpp
  -> integration and resource-lifetime checks
```

현재 구성한 Object:

| Object | 경로 | 용도 | 상태 |
|---|---|---|---|
| Security | `/0` | Server URI와 security 설정 | 현재 |
| Server | `/1` | Server ID, lifetime, binding | 현재 |
| Device | `/3` | 제조사, 모델, firmware version 등 | 현재 |
| Firmware Update | `/5` | 표준 OTA control과 상태 | Read wiring scaffold |
| BMS | `/33000` | reference BMS telemetry | 현재 |

현재 BMS Object는 `/33000/0/0`에서 voltage `12.7`을 Float로 읽는다.
`/33000`은 하위 장치를 대신 표현하는 Object가 아니라 이 LwM2M endpoint 자체가
제공하는 custom Object다.

### 5.3 자원 소유권과 event loop

현재 `ReferenceClientApp`은 다음 C 자원의 RAII 경계다.

| 자원 | 현재 소유자 | 정리 방식 |
|---|---|---|
| UDP socket fd | `ReferenceClientApp` | `close()` |
| `lwm2m_context_t *` | `ReferenceClientApp` | `lwm2m_close()` |
| LwM2M Object 포인터 | `ReferenceClientApp` | Object별 cleanup 함수 |
| Server connection list | client context를 통한 `ReferenceClientApp` | Wakaama close hook/free |
| `securityObjectP` | 소유하지 않는 alias | Object 배열의 `/0`을 참조 |

`connectionList`는 하위 Device 목록이 아니라 이 Client가 연결한 LwM2M Server
transport session 목록이다.

현재 event loop:

```text
lwm2m_step()
-> Wakaama timeout까지 UDP socket 대기
-> recvfrom()
-> sender에 해당하는 server connection 검색
-> lwm2m_handle_packet()
-> 반복
```

Registration, READY 전환, Read 요청 처리, signal 기반 종료와 Deregistration가
검증됐다.

### 5.4 Device Client 배포 및 책임 경계

플랫폼은 특정 STM32 제품의 완성 firmware를 범용 산출물로 제공하지 않는다.
대신 장치 개발자가 제품 firmware에 통합할 수 있는 Device Integration Kit를
제공한다.

| 플랫폼이 제공할 책임 | 장치 개발자가 제공할 책임 |
|---|---|
| 지원 LwM2M/Object version과 Resource 계약 | 대상 MCU, RTOS, network stack에 Client 통합 |
| endpoint/credential provisioning 규격 | credential의 안전한 저장과 device identity 연결 |
| `/3`, `/5`, custom telemetry model | 실제 장치 정보와 sensor/actuator callback 연결 |
| `/5` 상태 전이와 Update Result mapping | firmware의 flash 저장, hash/signature 검증 |
| update backend callback 경계와 Linux reference | bootloader, A/B slot, reboot, confirmation, rollback |
| Leshan 상호운용 및 실패 시나리오 test | 실제 제품 firmware build와 hardware 검증 |

지원할 STM32 조합은 MCU family만으로 정의하지 않는다. RTOS, network stack,
LwM2M engine, bootloader, flash layout을 하나의 지원 profile로 확정한 뒤 해당
profile용 source component와 sample, 설정 규격, test를 배포한다.

## 6. Device onboarding과 Object model

장치 추가는 세 경우를 구분한다.

| 상황 | 필요한 작업 | 서버 코드 변경 |
|---|---|---|
| 기존 제품의 새 장치 한 대 | endpoint/credential 발급과 사용자 소유권 연결 | 없음 |
| 표준 Object를 사용하는 새 제품 | Device Profile 등록 | 없음 |
| 새로운 custom Object를 쓰는 새 제품 | DDF/XML과 UI metadata 등록 | 목표: 없음 |
| LwM2M을 지원하지 않는 레거시 장치 | 별도 adapter 또는 optional Gateway | adapter 구현 필요 |

현재 `bms.xml`은 classpath에서 정적으로 로드한다. 향후에는 Object model
registry와 관리 API를 만들어 재빌드 없이 모델을 추가할 수 있어야 한다.

## 7. Telemetry flow

현재 단계:

```text
HTTP 요청
-> 서버가 Device의 /33000/0/0 Read
-> 응답 decode
-> BmsTelemetryStore의 endpoint별 최신값 갱신
-> HTTP 응답
```

제품 목표:

```text
Device Observe/Notify 또는 LwM2M Send
-> validation / normalization
-> 최신값 cache 갱신
-> PostgreSQL history 저장
-> UI에 실시간 event 전달
```

매 화면 새로고침마다 Device에 Read를 보내지 않는다. 실시간 화면과 영속 이력은
같은 수신 event에서 파생하되, 높은 빈도의 telemetry가 LwM2M 처리 thread나
UI 전달을 막지 않도록 저장 작업을 분리하고 batch 정책을 둘 수 있다.

서버는 BMS, Motor, Sensor별 Java Controller를 계속 추가하는 구조를 목표로 하지
않는다. Object ID, Instance ID, Resource ID, timestamp, typed value를 기준으로
범용 수집 경로를 만들고, Device Profile metadata로 UI 의미를 제공한다.

## 8. Firmware OTA

### 8.1 책임 분리

| 구성요소 | 책임 |
|---|---|
| hawkBit | artifact, Distribution Set, Target Action과 rollout |
| Spring DMF Adapter | hawkBit 명령과 LwM2M Object 5 상태 변환 |
| Spring Artifact Proxy | hawkBit artifact staging과 Device용 CoAP(S) 제공 |
| LwM2M Client와 Object 5 | Package URI, Update Execute와 상태 보고 |
| Device application | 다운로드 transport와 staging flash 기록 |
| Bootloader | 설치, boot confirmation과 rollback |
| Device 보안 계층 | signature, anti-rollback과 secure credential 저장 |

LwM2M Client가 있어도 flash와 Bootloader의 안전 업데이트 기능은 장치에
별도로 필요하다.

### 8.2 OTA flow

```text
운영자가 hawkBit UI에 firmware 업로드
→ hawkBit가 artifact와 metadata 저장
→ Software Module을 Distribution Set으로 구성
→ Target에 Action 할당
→ hawkBit가 DMF DOWNLOAD_AND_INSTALL 발행
→ Spring이 artifact를 staging하고 Device용 URI 생성
→ Spring이 /5/0/1 Package URI Write
→ Device가 artifact를 Block2로 다운로드
→ /5/0/3 State=2 보고
→ Spring이 /5/0/2 Update Execute
→ /5/0/3 State=3 보고
→ Device reboot와 Bootloader 설치
→ 새 application이 /5/0/5 Update Result=1 보고
→ Spring이 DMF FINISHED 발행
→ hawkBit가 Distribution Set을 installed로 기록
```
### 8.3 상태 변환

| LwM2M 값 | hawkBit Action 상태 |
|---|---|
| State `1` Downloading | `DOWNLOAD` |
| State `2` Downloaded | `DOWNLOADED` |
| State `3` Updating | `RUNNING` |
| Update Result `1` Success | `FINISHED` |
| Update Result `10` Canceled | `CANCELED` |
| Update Result `11` Deferred | `WARNING`, Action 유지 |
| 그 외 실패 Result | `ERROR` |

현재 완전 검증된 hawkBit Action Type은 Forced다.
Download Only: 다운로드는 가능하지만 완료와 후속 설치 lifecycle 보완 필요
Soft: 사용자 승인과 장치 설치 동의 흐름 미구현
Time Forced: 예약 시각과 강제 전환 정책 미구현

### 8.4 현재 보안 경계

현재 Linux reference의 LwM2M control plane은 DTLS-PSK를 사용한다.
STM32 reference의 LwM2M control과 모든 artifact download는 아직 NoSec CoAP다.

목표 경계:
```text
LwM2M control  → CoAPS + Device credential
Artifact pull  → CoAPS + Device identity/action 권한 검사
Firmware 적용 → Device signature와 anti-rollback 검증
```

DTLS-PSK는 통신 상대와 전송 구간을 보호한다. Firmware signature는 binary의
출처와 무결성을 장치에서 검증하며 서로 대체할 수 없다.

## 9. Persistence와 artifact storage

### 9.1 Spring PostgreSQL

Spring PostgreSQL은 장치 관리 데이터를 저장한다.

- Device와 endpoint
- enabled 상태와 향후 ownership
- credential identity와 lifecycle 상태
- AES-256-GCM으로 암호화된 PSK
- 향후 DMF Action 멱등성 receipt
- 향후 telemetry latest/history와 audit

Firmware binary, Software Module, Distribution Set과 rollout은 Spring DB에
중복 저장하지 않는다.

### 9.2 hawkBit 저장소

hawkBit 전용 PostgreSQL은 다음 metadata를 소유한다.

- Software Module
- artifact filename, size와 hash
- Distribution Set
- Target와 Action
- rollout과 상태 이력

실제 firmware binary는 현재 Docker named volume인
`hawkbit-artifact-data`에 저장된다.

```text
hawkBit container: /app/artifactrepo
```

Spring의 /tmp/ota-hawkbit-artifacts는 Device 전송을 위한 임시 staging
cache이며 artifact 원본 저장소가 아니다. 삭제되더라도 hawkBit에서 다시
다운로드할 수 있어야 한다.

### 9.3 저장소 확장

현재 local filesystem 기반 hawkBit artifact storage는 단일 서버와
포트폴리오 검증에 사용한다.

제품 규모에서 필요한 경우 hawkBit artifact repository 확장 지점을 통해
MinIO 또는 S3-compatible storage로 교체한다. CDN은 장치 수와 지역이 늘어날
때 추가하며 현재 milestone에는 포함하지 않는다.

### 9.4 일관성 경계

Spring은 hawkBit DB를 직접 조회하거나 수정하지 않는다.

```text
관리 작업     → hawkBit Management API
배포 명령/상태 → RabbitMQ DMF
```
두 PostgreSQL 사이의 분산 transaction을 만들지 않는다. Spring은
endpoint, actionId, softwareModuleId만 connector 처리와 멱등성을 위해
보관하고, OTA 이력의 원본은 hawkBit으로 유지한다.

## 10. Security와 신뢰성

### 10.1 현재 상태

- Linux reference의 LwM2M control은 DTLS-PSK 적용
- 서버 PSK는 PostgreSQL에 AES-256-GCM 암호문으로 저장
- master key는 Git에서 제외된 로컬 파일로 관리
- STM32 reference의 LwM2M control은 NoSec CoAP
- Artifact Proxy는 NoSec CoAP
- STM32 firmware 검증은 CRC32와 vector 검사까지 구현
- 제품용 signature와 anti-rollback은 미구현
- Spring과 hawkBit은 RabbitMQ credential과 Target Token을 사용

현재 STM32 NoSec 경로는 실제 보드 기능 검증용이며 제품 보안 완료 상태가 아니다.

### 10.2 Device 등록과 DTLS 정책

Device Registry가 endpoint와 허용된 보안 모드의 source of truth다.

```text
LwM2M Registration
→ endpoint가 DB에 존재하는지 검사
→ enabled 상태 검사
→ Device security mode 검사
→ PSK Device이면 DTLS identity와 DB credential 검증
→ 통과한 Registration만 관리
```

NOSEC는 명시적으로 설정된 개발 Device에만 허용한다. 제품 Device의 기본
모드는 PSK 또는 이후 지원할 certificate 기반 모드다.

PSK는 네트워크로 전달하거나 직접 비교하지 않는다. DTLS handshake에서
identity로 활성 credential을 찾고 복호화한 PSK로 인증한다.

### 10.3 Credential provisioning

동일한 identity와 PSK가 서버와 장치에 각각 provisioning되어야 한다.

-서버: 암호화된 PSK와 lifecycle metadata
-장치: secure storage 또는 제조 provisioning 영역
-Linux reference 환경변수: 장치 secure storage를 흉내 내는 개발 입력

서버에서 PSK를 rotation해도 장치의 PSK가 자동 변경되지는 않는다. 실제
in-band rotation은 LwM2M Bootstrap 또는 별도 provisioning 절차가 필요하다.

### 10.4 Artifact 보호

목표 Artifact Proxy는 CoAPS를 사용한다.

```text
DTLS identity
→ DB의 ACTIVE credential 검증
→ identity와 endpoint 연결
→ endpoint에 할당된 action인지 확인
→ 해당 artifact만 다운로드 허용
```

CoAPS는 인증과 전송 구간을 보호한다. Device는 이와 별도로 firmware
signature와 anti-rollback 정책을 검증해야 한다.

### 10.5 남은 신뢰성 항목

-PostgreSQL 기반 DMF Action 멱등성과 재시작 복구
-credential과 Leshan SecurityStore 장애 복구
-전원 차단 중 firmware 적용 rollback
-staged rollout과 오류 임계치
-Admin 인증, 권한과 audit log

## 11. Milestones

### Milestone 1: LwM2M Client Foundation

상태: **완료**

- Linux Wakaama reference client
- Object 3, 5와 custom Object 33000
- Registration, Read, Observe와 Deregistration
- DTLS-PSK와 tinyDTLS lifecycle 검증

### Milestone 2: Spring LwM2M Server와 Persistence

상태: **완료**

- Embedded Leshan lifecycle
- PostgreSQL Device Registry
- AES-256-GCM credential 저장
- create, rotate와 revoke
- Device와 LwM2M Object Admin UI

### Milestone 3: Firmware OTA Vertical Slice

상태: **완료**

- Device Integration Kit와 Object 5 version 1.2
- Linux simulated install와 restart recovery
- STM32F429ZI flash staging과 EVSE_BOOT 통합
- hawkBit artifact, Distribution Set과 DMF 연동
- hawkBit UI에서 실제 STM32 `0.2.0 → 0.3.0` Forced OTA

### Milestone 4: Device와 Action Lifecycle

상태: **진행 예정**

구체적인 순서는 `DEVELOPMENT_STATUS.md`의 `이후 작업 순서`를 따른다.

- DMF Action 멱등성과 재시작 복구
- Device Registry 기반 LwM2M Authorizer
- hawkBit Target projection
- hawkBit Action Type 의미 연결

### Milestone 5: Product Security와 Fleet 확장

상태: **계획**

- Artifact CoAPS와 endpoint/action 권한
- 실제 Device DTLS provisioning
- firmware manifest와 signature 계약
- Admin 인증과 audit
- telemetry history와 staged rollout
- optional Gateway는 비 IP 장치 요구가 생길 때만 추가

## 12. 아키텍처 원칙

- 직접 연결 가능한 Device는 LwM2M Server에 직접 연결한다.
- 표준 LwM2M Object를 우선하고 제품 고유 데이터만 custom Object로 둔다.
- 장치 한 대 추가는 provisioning 작업이며 코드 변경 작업이 아니다.
- Client SDK는 교체 가능하고 서버는 특정 SDK에 종속되지 않는다.
- firmware 전달과 update 제어를 분리한다.
- Spring DB는 Device와 credential을, hawkBit은 firmware와 배포 이력을 소유한다.
- Spring staging cache는 artifact 원본 저장소로 사용하지 않는다.
- 실제 flash/bootloader 구현 전 simulated backend로 정상, 실패, rollback을 검증한다.
- 학습 단계에서는 작은 단위로 구현하고 매 단계 build와 실행 결과를 확인한다.
