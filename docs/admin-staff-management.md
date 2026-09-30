# 매니저 권한 관리

ADMIN은 가입된 활성 USER를 학번으로 조회해 MANAGER로 지정하거나 MANAGER를 USER로 되돌린다. MANAGER는 아래 두 권한을 독립적으로 가지며 하나 이상 선택한다. ADMIN은 두 권한과 매니저 권한 관리를 모두 가진다. USER는 관리자 권한이 없다. 기존 MANAGER의 ADMIN 승격과 ADMIN의 MANAGER 강등은 별도 API로 제공한다. 계정 생성과 이메일 초대는 제공하지 않는다.

| 권한 | 범위 |
| --- | --- |
| `OPERATIONS` 일반 운영 관리 | 축제 기본정보, 테마, 공지·광고, 부스맵, 타임테이블, 번역 관리 |
| `TICKETING` 티켓팅 관리 | 티켓팅 사용 설정·회차, 공연별 티켓 조회·발급·취소, 팔찌 배부·통계·오픈 |

메뉴 및 직접 URL 접근을 분리하고 서버에서도 경로·메서드·서비스 인가를 검사한다. 축제 기본정보와 티켓팅 설정은 저장 API를 분리한다. 하나만 가진 매니저도 해당 설정을 독립적으로 저장할 수 있다.

## API

모든 경로는 ADMIN 전용이며 `/api/admin/staff` 아래에 있다.

| 요청 | 내용 |
| --- | --- |
| `GET /candidates?studentId=...` | trim한 학번의 정확한 일치 조회. 1~255자 |
| `GET ?page=0&size=20&filter=ALL` | 활성 매니저 목록. `ALL`(기본), `ADMIN`, `TICKETING`, `OPERATIONS`, `BOTH`로 서버에서 필터링 후 이름 오름차순으로 페이징하며, 동명이인은 학번 오름차순으로 정렬한다. `TICKETING`·`OPERATIONS`은 해당 권한이 있는 MANAGER와 모든 ADMIN을 포함하며, `BOTH`는 두 권한을 모두 가진 MANAGER와 모든 ADMIN을 포함한다. size 1~100 |
| `PATCH /{userId}/role` | `{"role":"MANAGER","permissions":["TICKETING"]}` 또는 `{"role":"USER","permissions":[]}` |
| `POST /{userId}/promote-admin` | MANAGER를 최고 관리자로 승격한다. USER를 직접 승격할 수 없다. |
| `POST /{userId}/demote-admin` | 최고 관리자를 두 권한을 모두 가진 MANAGER로 강등한다. 자기 자신 또는 마지막 최고 관리자는 강등할 수 없다. |

성공 응답은 `ApiResponse`의 `data`를 사용한다. 회원 데이터는 `id, studentId, name, college, major, role, permissions`이며 역할은 USER/MANAGER/ADMIN이다. 목록은 `items, page, size, totalElements, totalPages, managementEnabled`를 반환한다. 빈 목록의 totalPages는 0이다.

MANAGER 지정에는 `permissions`를 반드시 전달하며 `OPERATIONS`, `TICKETING` 중 1~2개의 중복 없는 값을 받는다. 기존 MANAGER의 권한 조합 변경도 같은 PATCH를 쓴다. USER 회수는 빈 권한 목록으로 저장한다. 최고 관리자 승격·강등은 별도 POST만 사용하며, 두 요청은 활성 최고 관리자 행 전체를 ID 순서로 잠가 마지막 최고 관리자 보호와 stale actor 재검증을 수행한다. ADMIN 응답 권한은 항상 둘 다이다.

후보·목록은 `Cache-Control: no-store`이다. 업무 오류는 nested `error.error`와 `error.message`를 사용한다. 인증 오류는 HTTP 401/403을 기준으로 처리한다.

- 400: INVALID_STUDENT_ID / INVALID_STAFF_ROLE / INVALID_STAFF_TARGET_ID / INVALID_PAGE_REQUEST / INVALID_STAFF_FILTER
- 404: STAFF_TARGET_NOT_FOUND (없거나 탈퇴한 회원)
- 409: STAFF_ROLE_CONFLICT (중복, 자기 자신, 허용되지 않은 역할 전환, 마지막 최고 관리자 보호)
- 503: STAFF_MANAGEMENT_DISABLED

## 기존 DB 준비와 배포

운영은 `ddl-auto: validate`이므로 **백엔드 배포 전에 SQL을 적용**한다. 실제 대상 DB·서버를 확인하고 백업을 먼저 만든다.

```sh
mysqldump -u <user> -p --single-transaction --no-tablespaces <database> > before-manager.sql
mysql -u <user> -p <database> < scripts/add_manager_role.sql
mysql -u <user> -p <database> < scripts/add_manager_permissions.sql
```

비밀번호를 커맨드에 직접 넣지 않는다. 스크립트 실행에 `--force`를 사용하지 않는다. 테이블 변경 외에 마이그레이션용 routine의 생성/실행 권한이 필요하다.

스크립트는 users의 필수 컬럼과 이력 테이블 구조를 모두 사전 검사한 뒤, 기존 ENUM 순서·NULL·default·collation·comment를 보존하여 ROLE_MANAGER를 마지막에 추가하고 `user_role_change_history`를 생성한다. 이력은 scalar 사용자 ID와 변경 전후 역할·시각 및 권한 조합을 저장한다. 권한 분리 전 이력의 권한 조합은 NULL로 유지한다. FK/cascade는 없다. 첫 번째 스크립트는 사용자 역할·기존 데이터를 변경하지 않는다. 두 번째 스크립트는 기존 활성 MANAGER에 두 권한을 초기화하고 tokenVersion을 한 번 증가시킨다. 기존 매니저는 다시 로그인해야 한다.

이미 ROLE_MANAGER가 있더라도 이력 테이블은 별도로 검사/생성한다. 중간 실패 시 완료된 DDL은 유지되므로 오류를 확인한 후 같은 SQL을 재실행한다. VARCHAR, 예상 밖 ENUM/CHECK, 불완전 이력 테이블은 자동 변환하지 않고 중단한다. 이 스크립트는 다른 legacy 스키마 문제를 해결하는 전체 초기화 SQL이 아니다.

1. 권한 변경 요청을 중지하고 DB 백업 후 두 SQL을 순서대로 실행한다. 기존 역할 분포와 권한 초기화 결과를 확인한다. 새 SQL은 NULL 값을 미완료 표시로 사용하며 재실행 시 이미 설정한 권한과 tokenVersion을 보존한다.
2. `STAFF_MANAGEMENT_ENABLED=false`(기본값)로 **모든 BE 인스턴스** 배포 후 validate 기동 확인.
3. 세부 permissions claim을 지원하는 FE 배포. 기존 MANAGER 토큰에 permissions가 없으면 FE는 관리자 기능을 허용하지 않으며 재로그인이 필요하다. ADMIN 조회는 가능하고 변경은 503이어야 한다.
4. 모든 인스턴스 준비 후 `STAFF_MANAGEMENT_ENABLED=true`로 재시작/배포.
5. 검증 계정으로 일반 운영만 / 티켓팅만 / 둘 다를 각각 부여하고 재로그인하여 허용·거부 경로를 확인한다. 일부 권한 변경·전체 회수 후 이전 토큰이 거부되는지도 확인한다.

Docker compose도 `STAFF_MANAGEMENT_ENABLED`를 전달한다. 로컬 예:

```sh
STAFF_MANAGEMENT_ENABLED=true docker compose -f docker-compose.local.yml up -d --build --no-deps app
```

MANAGER가 하나라도 존재하면 역할을 모르는 구버전으로 단순 롤백하지 않는다. 변경 기능 비활성화 후 지원 버전 유지/전진 수정을 우선한다. ENUM 축소·이력 삭제·자동 강등은 수행하지 않는다.

## 세션·동시성

역할 및 세부 권한 변경은 활성 대상 행 잠금 안에서 역할·권한·tokenVersion·이력을 한 트랜잭션으로 갱신한다. 기존 logout/reset/withdraw도 같은 User 행 잠금 규칙을 따른다. 실패한 트랜잭션은 캐시 변경을 발행하지 않는다.

모든 운영진 권한 변경은 활성 ADMIN 행을 ID 순서대로 잠근 뒤 요청자의 현재 역할을 재검증한다. 잠근 엔티티는 refresh하여 같은 트랜잭션에서 먼저 읽은 역할이 남지 않게 한다. 최고 관리자는 회원 탈퇴도 409로 거부하며, 다른 최고 관리자가 먼저 MANAGER로 변경해야 탈퇴할 수 있다. 탈퇴 거부는 티켓·대기열 등 탈퇴 부수 효과 전에 수행한다.

관리자 경로에서는 JWT 역할·version을 매 요청 현재 활성 DB 회원과 대조하고 현재 DB 권한으로 인가한다. JWT 발급·재발급에는 현재 권한 목록을 포함한다. 권한 회수 커밋 이후 시작한 요청은 이전 토큰으로 통과하지 못한다. 이미 인가를 통과한 요청의 취소나 일반 학생 티켓팅 전체의 즉시 세션 철회는 보장 범위가 아니다. 변경 대상은 다시 로그인해야 한다.

일반 `/user/reissue`는 Authorization access token(유효한 서명, 만료 허용)과 유효한 refresh token의 subject가 같아야 한다. 불일치는 DB 조회 전에 401이다.

## 검증 명령

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew --no-daemon test
python3 scripts/tests/test_manager_migration.py
python3 scripts/tests/test_manager_permissions_migration.py
```

마이그레이션 테스트는 Docker `danzzan-mysql`의 MySQL 8.4에서 무작위 이름의 일회용 DB만 생성·정리한다. 컨테이너명을 바꾸려면 MYSQL_TEST_CONTAINER를 지정한다. 실제 회원 DB는 사용하지 않는다.

실제 row lock/rollback 검증은 `StaffManagementMySqlIntegrationTest`로 실행한다. `STAFF_MYSQL_TEST_URL`, `STAFF_MYSQL_TEST_USER`, `STAFF_MYSQL_TEST_PASSWORD`를 비공개 환경 변수로 설정한다. DB 이름은 반드시 `staff_integration_test_`로 시작하는 **전용 빈 DB**여야 하며 테스트는 create-drop으로 테이블을 생성·삭제한다. 일반 `gradlew test`에서는 환경 변수가 없으면 이 테스트가 건너뛰어진다.

## 최초 역할 계층 구현 검증 기록 (2026-09-28, 권한 세분화 이전)

- 전체 Gradle: 343개 중 336개 통과, 7개 skip, 실패 0. opt-in MySQL 6개는 별도 실행하여 전부 통과.
- MySQL 8.4 마이그레이션 7개 통과: 양쪽 ENUM 순서, 속성/데이터 보존, 재실행, 부분 적용, 선행 스키마 누락, 불완전 이력, 예상 밖 역할/VARCHAR 거부.
- 60개 관리자 method/path의 ADMIN/MANAGER/USER/미인증 접근 행렬 통과.
- FE 361개 테스트·lint·앱/테스트 typecheck·cycles(0)·build 통과.
- localhost 기존 DB 백업 후 스키마 반영 및 새 BE validate 기동. 실제 회원 역할 변경 없이 브라우저 목록/미가입 조회 확인.
- 로컬 앱 재시작 시 기존 시작 루틴인 DeepL 용어집 동기화도 실행됐다. 별도의 수동 번역/backfill은 하지 않았다.
- 브라우저에서 실제 회원 부여/회수는 실행하지 않았다. 변경 트랜잭션은 테스트 전용 MySQL, HTTP 인가는 MockMvc 행렬로 검증했다.


## 축제 설정 API 분리

- `PUT /admin/festival/settings`: `schoolName`, `festivalName`, `startDate`, `endDate`만 저장. `OPERATIONS` 필요.
- `PUT /admin/festival/ticketing-settings`: `ticketingEnabled`, `ticketingRounds`, `confirmedTicketCancelRoundIds` 저장. `TICKETING` 필요.
- `GET /festival/settings` 공개 응답은 유지한다. 티켓팅 설정 전 축제 기본정보가 필요하다.
- 구 FE의 통합 저장 요청으로 새 티켓팅 설정을 저장할 수 없으므로 BE와 FE를 함께 갱신한다. 부분 권한을 모르는 구 BE로 롤백하면 접근 범위가 넓어질 수 있으므로 롤백하지 않는다.

## 권한 세분화 검증 기록 (2026-09-28)

- 전체 Gradle: 347개 중 340개 통과, 7개 skip, 실패 0. opt-in MySQL 6개는 전용 DB에서 별도 실행하여 모두 통과했다.
- MySQL 8.4 마이그레이션: 기존 역할 마이그레이션 7개와 세부 권한 마이그레이션 10개 모두 통과. 잘못된 스키마에서 변경 없이 중단, 재실행·부분 적용 복구, 회수된 권한 보존, tokenVersion 중복 증가 방지를 포함한다.
- FE: 100개 테스트 파일의 371개 테스트 통과. lint, 앱·테스트 typecheck, 순환 의존성 검사(0개), build 통과.
- 독립 리뷰에서 서버 권한 분류·현재 DB 인가·세션 무효화·축제 설정 저장 분리·SQL 복구를 확인했다. FE 로그인 시 MANAGER의 권한 claim 누락을 거부하도록 보완하고 재검증했다.
- localhost가 사용하는 festival_test DB를 백업한 뒤 SQL을 적용하고 새 BE의 validate 기동 및 /health 정상 응답을 확인했다. ADMIN 7명·MANAGER 1명·USER 1명의 역할을 보존했으며 기존 MANAGER는 두 권한을 모두 유지한다.
- 로컬 백업: `/private/tmp/danzzan-permission-backup-20260928185001.sql` (2,506,309 bytes, 권한 600). 임시 경로이므로 장기 보관용 백업은 아니다.
- 실제 브라우저에서 기존 MANAGER의 두 권한 표시, 개별 체크 해제, 권한 미선택 시 저장 비활성화를 확인했다. 미리보기 후 취소했으며 계정 권한 변경은 제출하지 않았다. 변경 저장·회수는 자동 테스트로 검증했다.
- 이번 반영은 로컬 DB·앱에 한정한다. 외부 운영 배포와 커밋·푸시는 포함하지 않았다.

## 최고 관리자 지정·회수 검증 기록 (2026-09-28)

- 전체 Gradle: 360개 중 348개 통과, 12개 skip, 실패 0. 건너뛴 테스트 중 opt-in MySQL 11개는 별도 전용 DB에서 모두 통과했다. 나머지 1개는 기존 skip이다.
- 실제 MySQL 검증에는 승격·강등의 역할/권한/버전 저장, 동시 교차 강등, 미리 읽은 회원의 중복 승격 및 승격 직후 탈퇴 차단, 이력 저장 실패 시 롤백을 포함한다. 일회용 DB와 계정은 실행 후 정리했다.
- FE: 101개 파일의 383개 테스트, lint, 테스트 typecheck 및 앱 typecheck를 포함한 production build 통과.
- 독립 리뷰에서 서버 인가·행 잠금·최소 관리자 유지·탈퇴 우회 차단·감사 이력·세션 처리와 FE 확인창/취소 동작을 확인했다.
- localhost 백엔드를 새 이미지로 재기동하고 `/health` 정상 응답을 확인했다. 이번 기능에는 추가 스키마 변경이 없다.
- 브라우저에서 실제 더보기 메뉴와 지정·회수 확인창을 키보드로 열고 취소했다. 실제 회원의 승격·강등은 제출하지 않았다.
- 외부 운영 배포와 커밋·푸시는 포함하지 않았다.
