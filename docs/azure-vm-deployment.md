# Azure VM 시연 배포 기록

> 공개 문서의 `${...}`는 실제 값 대신 사용하는 변수입니다. 실행 전 [비공개 운영 값 사용 안내](azure-operations-values.md)를 읽고 본인 환경의 값을 설정하세요.

기준일: **2026-10-14 KST**. 이 문서는 `${VM_NAME}`에서 실제로 실행 중인 단짠 시연 환경을 기록한다. 최초 배포는 합성 데이터가 있는 `danzzan_demo`로 진행했다. 10월 14일 로컬 `festival_test`를 별도 `danzzan_fall_demo`로 복원하고 앱 연결을 전환했다. 로컬 원본과 기존 Azure DB는 보존했으며 Blob은 변경하지 않았다.

## 현재 상태

| 항목 | 상태 |
| --- | --- |
| 접속 주소 | `https://danzzan.koreacentral.cloudapp.azure.com` |
| 공개 서비스 | FE/Nginx만 TCP 80·443 공개. BE, MySQL, Redis, Kafka는 Compose 내부 네트워크만 사용 |
| TLS | Let's Encrypt 인증서 발급 완료. 현재 인증서 만료일은 **2027-01-12** |
| 컨테이너 | `frontend`, `app`, `mysql`, `redis`, `kafka`, `kafka-init` 실행 구성 |
| BE 저장소 인증 | VM의 시스템 할당 관리 ID. 비공개 `images` 컨테이너와 사용자 위임 SAS를 사용 |
| 현재 DB | `danzzan_fall_demo`: 로컬 `festival_test`의 40개 테이블·36,174행 복원, 스키마·데이터 해시 일치 및 앱 기동 확인 |
| 이전 시연 DB | `danzzan_demo` 보존. 합성 계정 2개와 공지 1건은 이전 DB에만 있음 |
| Redis / Kafka | Redis DB 1, 소비자 그룹 `ticket-issue-fall-demo-20261014`. 이전 Redis DB 0과 Kafka 데이터 보존 |

비밀번호, JWT 비밀값, DB 비밀번호, Azure 토큰·SAS·계정 키, 외부 API 키는 이 문서나 Git에 기록하지 않는다.

## DNS 이름 변경 기록 (2026-10-14)

기존 `danzzan-demo-2026.koreacentral.cloudapp.azure.com`에서 **`danzzan.koreacentral.cloudapp.azure.com`**으로 변경했다. Azure 이름 가용성 API의 `available: true`를 확인한 뒤 기존 `${PUBLIC_IP_NAME}`의 DNS label만 `danzzan`으로 갱신했다. 공인 IP `${VM_PUBLIC_IP}`와 VM·데이터 볼륨·Blob 저장소는 그대로다. 이전 이름은 Azure DNS에서 해제되므로 이전 주소의 리디렉션을 보장하지 않는다.

새 이름으로 Let's Encrypt 인증서를 발급하고 Nginx의 `server_name`·인증서 경로 및 BE의 `CORS_ALLOWED_ORIGINS`를 변경했다. FE 이미지를 다시 빌드하고 `app`·`frontend`만 재생성했다. MySQL·Redis·Kafka 컨테이너와 기존 게시물·이미지는 변경하지 않았다.

검증 결과:

- 새 DNS → 기존 공인 IP 확인, 새 인증서를 신뢰하는 HTTPS 요청 성공.
- 홈페이지·관리자 로그인 경로·공지·API health HTTP 200 및 HTTP → 새 HTTPS 301 확인.
- 관리자/일반 합성 계정의 새 Origin 로그인 성공. 내부 BE preflight의 새 Origin 허용 헤더와 기존/임의 Origin의 HTTP 403 확인.
- 실제 브라우저에서 새 주소의 공지 목록 표시 확인.
- 새 인증서의 `certbot renew --cert-name danzzan.koreacentral.cloudapp.azure.com --dry-run --no-random-sleep-on-renew` 성공, Nginx 검사·reload 후크 별도 성공.

이전 도메인 갱신 설정은 `/etc/letsencrypt/renewal-retired/`로 옮겨 자동 갱신 대상에서 제외했고, 이전 인증서·개인 키는 삭제하지 않았다. 이전 Compose/Nginx 사본은 VM의 `${BACKUP_DNS_CHANGE_BACKUP_20261014_DIR}/`에 보관했다. 이름을 되돌리려면 이전 DNS label의 가용성을 다시 확인해야 한다.

로컬 비공개 시연 로그인 안내의 URL도 갱신했다. 주소 변경으로 브라우저 로그인 상태가 이어지지 않을 수 있으므로 새 주소에서 다시 로그인한다. 카카오 앱 관리자는 **새 짧은 주소**를 JavaScript SDK 도메인에 등록해야 한다.

## 실제 구성

배포 루트는 VM의 `${DEPLOY_ROOT}/Danzzan-BE`이며, Compose 파일은 `docker-compose.azure.yml`이다. `frontend`가 정적 FE를 제공하고 `/api/` 요청을 내부 `app:8080`으로 프록시한다. `app`은 MySQL·Redis·Kafka와 같은 Compose 네트워크에만 연결한다.

```text
브라우저 ─ HTTPS ─ frontend (Nginx)
                      └─ /api/ ─ app (Spring Boot)
                                      ├─ mysql:3306
                                      ├─ redis:6379
                                      ├─ kafka:9092
                                      └─ Azure Blob (관리 ID)
```

호스트에는 80과 443만 게시한다. `3306`, `6379`, `8080`, `9092`, `9093` 및 `9094`는 공개하지 않는다. Kafka는 outbox/소비자 처리에 필요하므로 실행 구성에서 제거하지 않았다.

Compose의 `mysql_data`, `redis_data`, `kafka_data` 명명 볼륨은 VM OS 영구 디스크(`/dev/sda1`)에 있다. `/mnt` 임시 디스크에는 데이터를 두지 않는다. `docker compose down -v`는 이 볼륨을 삭제하므로 시연 환경에서 실행하지 않는다.

## 최초 배포 DB와 합성 데이터의 범위 (현재는 이전 DB)

`deploy/azure/schema.sql`은 새 `danzzan_demo` 데이터베이스만 대상으로 테이블을 준비하고, `deploy/azure/demo-seed.sql`을 배포 전 비밀 디렉터리에 복사해 MySQL 초기화에 사용했다. 두 SQL 파일은 대상 DB가 `danzzan_demo`가 아니면 중단하도록 보호한다.

생성된 합성 데이터는 다음으로 제한한다.

| 구분 | 값 | 용도 |
| --- | --- | --- |
| 관리자 | 학번 `90000001` | 관리 기능 시연 |
| 일반 사용자 | 학번 `90000002` | 일반 사용자 흐름 시연 |
| 공지 | 1건 | 새 DB에서만 보이는 합성 공지 |

로컬의 `deploy/azure/secrets/demo-login.txt`(권한 `600`)에는 생성 당시의 시연 계정 로그인 정보가 있으며 Git에 포함하지 않는다. 서버에는 실행용 `app.env`·`mysql.env`, FE 공개 키 빌드 설정 및 BCrypt 해시가 들어간 seed 파일을 둔다. 계정 평문 비밀번호 목록은 전송하지 않았다. 스키마 정의만 참고했으며 기존 NHN/로컬/운영 데이터베이스의 데이터 행, 기존 게시물, 기존 이미지 참조는 이 DB에 복사하지 않았다.

현재 서비스는 위 합성 계정 대신 이관한 로컬 DB의 계정을 사용한다. `demo-login.txt`의 합성 계정 정보는 이전 DB 복구 시에만 유효하다. 현재 DB 이관·검증·복구 절차는 [azure-db-migration.md](azure-db-migration.md)를 참고한다. 새 VM에서는 기본 초기화 SQL만 실행하면 현재 DB가 생기지 않으므로 별도 복원과 권한 부여가 필요하다.

## 최초 배포 당시 이미지 검증

실제 브라우저와 API에서 다음 흐름을 확인했다.

1. 관리자 인증 및 권한 확인
2. 새 DB의 합성 공지에 테스트 이미지를 multipart로 업로드
3. 비공개 Blob의 짧은 읽기 SAS로 브라우저 이미지 조회
4. SAS 만료 후 `/images/read-url`을 통한 URL 갱신

이미지 Blob은 `images` 컨테이너에만 생성됐으며, 기존 Blob이나 기존 게시물은 변경하지 않았다. 원본 5분 SAS가 만료된 뒤 Blob 요청이 HTTP 403이 되는 것을 확인했고, 만료 URL로 BE `/images/read-url`을 요청해 새 SAS를 발급한 뒤 PNG 조회 HTTP 200을 확인했다. 실제 브라우저의 Azure Blob 이미지도 `naturalWidth=8`로 로드됐다.

임시 공지 **ID 2 한 건**과 해당 테스트 Blob도 정리했다. API 삭제 뒤 정확한 ID와 UUID 제목을 함께 확인하여 해당 테스트 공지와 이미지 참조 행만 물리 삭제했고, VM 관리 ID로 새 테스트 Blob 한 개를 HTTP 202로 삭제했다. Blob soft delete 3일 설정은 유지된다. seed DB는 공지 1행·계정 2행·이미지 참조 0행으로 돌아갔다. Azure `images` 컨테이너의 활성 Blob 이름과 ETag도 전후 모두 0개로 완전히 일치했다.

MySQL·Redis·Kafka를 실제로 재시작한 뒤에도 MySQL 행 수, Redis AOF 테스트 키, Kafka 독립 테스트 topic의 메시지 보존을 각각 확인했다. 검증용 Redis 키와 Kafka topic은 이후 정리했다. 이 최초 배포 검증 당시에는 VM 할당 취소 후 재시작을 시험하지 않았다. 이후 2026-10-22 수행한 점검과 최종 상태는 [VM 중지·재시작 안내](azure-vm-stop-start.md)를 참고한다.

## HTTPS와 갱신

인증서 대상 FQDN은 `danzzan.koreacentral.cloudapp.azure.com`이다. Nginx는 HTTP-01 경로 `/.well-known/acme-challenge/`를 호스트의 `/var/www/certbot`에서 제공한다. Certbot은 webroot 방식을 사용하며, 성공적으로 새 인증서가 배포될 때만 `deploy/azure/renew-nginx.sh`가 Nginx 설정 검사 후 reload한다.

Certbot은 연락처 이메일 없이 등록됐고 systemd timer가 활성화되어 있다. `certbot renew --dry-run`은 webroot 갱신 시뮬레이션으로 최종 성공했다. 이 dry-run은 deploy hook을 기본 실행하지 않으므로, `deploy/azure/renew-nginx.sh`의 Nginx 설정 검사와 reload도 별도로 수동 성공을 확인했다. 운영 중 인증서나 갱신 설정을 다시 만들기 위해 반복 발급하지 않는다.

Nginx 컨테이너가 Certbot의 `live/` 심볼릭 링크가 가리키는 인증서 파일을 읽을 수 있도록 `/etc/letsencrypt`를 읽기 전용으로 마운트한다. 읽기 전용은 컨테이너의 변경을 막을 뿐 private key 읽기 권한을 없애지는 않는다. 따라서 해당 컨테이너 이미지·Compose 접근 권한을 제한하고, 이 경로를 다른 서비스에 마운트하지 않는다.

## 배포·재배포 절차

다음 명령은 VM에서 실행하는 운영 절차다. VM에는 allowlist 기반 소스 아카이브만 전송했고 `.git` 디렉터리가 없으므로 VM에서 `git status`를 실행해 배포 버전을 판별할 수 없다. 배포할 브랜치·커밋·로컬 변경은 전송 전에 개발 컴퓨터의 Git 작업 트리에서 확인한다. VM에서는 아카이브 내용과 비밀 파일 권한을 확인한다. 현재 일반 VM 사용자는 `docker` 그룹에 넣지 않았으므로 Docker 명령에 `sudo`를 붙인다.

외부 서비스 환경변수 분리 이후에는 아래의 `external-services.env`도 반드시 준비한다. 기존 VM에 새 Compose만 먼저 복사하면 이 파일이 없어 Compose 실행이 실패한다. 새 비공개 파일을 먼저 준비하고 Compose를 반영한다.

```bash
: "${DEPLOY_ROOT:?Set DEPLOY_ROOT from the private operations guide}"
cd "${DEPLOY_ROOT}/Danzzan-BE"
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml config >/dev/null
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml up -d --build
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml ps
```

`app.env`, `external-services.env`, `mysql.env`, `frontend-build.env` 및 배포 시 사용하는 seed 파일은 `deploy/azure/secrets/`에 둔다. 디렉터리 권한은 `700`, 환경 파일 권한은 `600`으로 유지한다. MySQL 컨테이너가 초기화 SQL을 읽어야 하므로 Compose에 바인드한 seed SQL 파일은 `644`다. 이 파일에는 평문 비밀번호를 넣지 않는다.

단순 서비스 재시작은 볼륨을 지우지 않는다.

```bash
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml restart
```

중지 후 다시 올릴 때도 같은 볼륨을 유지한다.

```bash
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml stop
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml start
```

`down -v`, `volume rm`, MySQL 초기화 파일 변경 후 기존 볼륨을 지우는 작업은 새 DB와 합성 데이터도 삭제한다. 그런 초기화는 별도의 대상 확인 후에만 한다.

## 실행 중 확인 항목

```bash
: "${DEPLOY_ROOT:?Set DEPLOY_ROOT from the private operations guide}"
cd "${DEPLOY_ROOT}/Danzzan-BE"
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml ps
curl -fsS https://danzzan.koreacentral.cloudapp.azure.com/ >/dev/null
curl -fsSI https://danzzan.koreacentral.cloudapp.azure.com/ | head
sudo certbot certificates
```

로그에 비밀값이나 SAS URL이 보이면 로그를 외부 공유하지 않는다. 현재 DB에는 로컬에서 복원한 게시물·계정이 있으므로 쓰기 검증에는 별도 테스트 데이터를 사용한다. 이번 DB 전환 후에는 조회 API만 확인했으며 로그인·업로드·발권은 재검증하지 않았다.

## 외부 서비스 최초 반영 검증 (2026-10-14)

이전 배포에서는 Octomo가 테스트 값이고 OpenAI·DeepL·메일 자격 증명이 비어 있었다. 2026-10-14 사용자가 입력한 `external-services.env`를 VM에 반영하고 새 app 컨테이너에 모든 항목이 동일하게 주입됐는지 값을 출력하지 않고 비교했다. `DB_PASSWORD`, `JWT_SECRET`, `CODE_ENCRYPTION_SECRET` 및 기존 DB 연결은 유지했다.

| 서비스 | 이번 확인 결과 | 아직 실행하지 않은 검증 |
| --- | --- | --- |
| SMTP | TLS 연결·로그인 성공 | 실제 메일 발송·수신 |
| Octomo | 입력값의 컨테이너 주입 확인 | 외부 인증·전화번호 인증 흐름 |
| OpenAI | 설정한 모델 조회 HTTP 200 | 테마 생성·과금 요청 |
| DeepL | 사용량 조회 HTTP 200; 빈 `DEEPL_API_URL`은 Free/Pro 자동 선택 | 실제 번역·용어집 생성 |
| 카카오맵 | 이번 배포에서 재검증하지 않음 | Azure 도메인 등록과 지도 표시 |

카카오맵은 기존 public JavaScript 키로 정상 SDK endpoint를 변경 전 Azure FQDN Referer와 함께 요청했을 때 HTTP 401을 반환했다. 키 비활성화·잘못된 키 가능성도 남아 있지만, 현재 Azure 도메인이 Kakao Developers의 해당 앱 JavaScript SDK 도메인에 등록되지 않았을 가능성이 높다. 동료 또는 앱 관리자는 `https://danzzan.koreacentral.cloudapp.azure.com`을 등록한 뒤 지도를 다시 검증한다. [Kakao Maps Web 안내](https://apis.map.kakao.com/web/guide/)

조회·인증 성공은 실제 생성·번역·발송 흐름 전체의 성공이나 충분한 잔액을 보장하지 않는다. 이번 검증에서는 이메일·문자를 발송하거나 AI 생성·번역 요청을 보내지 않았다.

위 표는 환경변수 최초 반영 시점의 기록이다. 이후 OpenAI 실제 테마·배경 추천은 별도로 검증했다. 현재까지의 성공 시간과 미확정 원인은 [AI 추천 진단 기록](azure-ai-recommendation-diagnostics.md)을 참고한다.

### 외부 서비스 환경변수 입력과 반영

1. `deploy/azure/external-services.env.example`을 **새 파일을 만들 때만** `deploy/azure/secrets/external-services.env`로 복사한다. 기존 비공개 파일을 덮어쓰지 않는다. 비밀값은 이 파일에서 직접 입력하며 Git·채팅에 넣지 않는다. 파일은 Git과 Docker 빌드 대상에서 제외된다.
2. Octomo는 `OCTOMO_API_KEY`와 `OCTOMO_BASE_URL`을 함께 설정하고 수신 번호를 확인한다. 아직 연결하지 않을 때는 샘플 값을 유지한다. OpenAI는 `OPENAI_API_KEY`를 입력하고 모델을 확인한다. DeepL은 `DEEPL_API_KEY`를 입력하며 `DEEPL_API_URL`은 비워두면 Free/Pro를 자동 선택한다. 메일은 host·port·username·password·from을 함께 설정한다.
3. 기본 동작에서는 **DeepL이 앱 시작 시 같은 이름의 용어집을 삭제하고 다시 만든다.** Azure Compose는 `TRANSLATION_GLOSSARY_SYNC_ON_STARTUP=false`로 이를 끄고, `TRANSLATION_BACKFILL_SCHEDULED_ENABLED=false`로 기존 게시물 자동 번역 보정도 끈다. 키를 반영해도 배포 자체가 기존 콘텐츠나 외부 용어집을 바꾸지 않도록 하기 위한 설정이다. 사용자가 실행하는 번역 기능과 수동 보정 API는 계속 동작하므로 별도 승인 없이 검증에 호출하지 않는다. 기존 운영 용어집에 영향을 주지 않도록 샘플의 Azure 시연 전용 이름을 사용하고, 같은 API 계정에 이미 사용하는 이름인지 확인한다. 외부 서비스 요금은 Azure 학생 크레딧과 별개다.
4. Compose의 `env_file`은 `app.env` 다음에 `external-services.env`를 읽는다. Compose `environment`에 같은 키를 추가하면 비공개 파일보다 우선하므로 외부 API 키를 거기에 중복 선언하지 않는다. DB·JWT·인증코드 암호화 비밀값은 기존 `app.env`에 유지한다. FE에는 이 파일을 전달하지 않는다.
5. VM에 기존 설정의 비공개 백업을 만든 뒤 SSH로 파일을 전달하고 권한을 `600`으로 제한한다. 반영 전에는 `docker compose ... config --quiet`로 검사한다. 확장된 `config`, `docker inspect`의 전체 환경변수, 비밀 파일 내용을 화면이나 로그에 출력하지 않는다.
6. 키·외부 계정·요금·DeepL 용어집 변경 영향을 확인한 뒤 app만 재생성한다. 환경변수 변경은 단순 `restart`로 적용되지 않는다. 잠시 API가 중단되며 MySQL·Redis·Kafka·이미지 데이터는 수정하지 않는다.

```bash
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml config --quiet
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml up -d --no-deps --force-recreate app
```

반영 후에는 health와 값이 아닌 설정 유무부터 확인한다. 실제 메일 발송·Octomo 인증·OpenAI 생성·DeepL 번역은 비용 및 외부 데이터 변경이 발생할 수 있으므로 승인된 테스트 계정과 데이터만 사용한다. 외부 API 성공 여부와 환경변수 주입 성공 여부는 구분해서 기록한다.

## main 병합 코드와 환경변수 배포 기록 (2026-10-14)

- BE 기준 커밋 `5fd9a4b`, FE 기준 커밋 `3bb6bf5d`를 반영했다. BE에는 배포 당시 미커밋 상태였던 외부 환경변수 분리와 자동 번역·시작 시 용어집 동기화 차단 코드도 포함됐다. 정확한 배포 파일은 VM의 `deployed-source-manifest.json`으로 구분한다.
- 새 이미지를 먼저 빌드한 뒤 `app`, `frontend`만 `--no-deps --force-recreate`로 교체했다. MySQL·Redis·Kafka 컨테이너 ID와 기존 app/mysql/frontend-build 비공개 설정 파일은 배포 전후 동일했다. DB 스키마·볼륨·Blob 파일은 변경하지 않았다.
- 배포 준비 경로: `${RELEASE_PREPARATION_DIR}`. 이 경로의 `activation-complete`는 실제 HTTPS 확인 후 생성했다.
- 비공개 복구 경로: `${BACKUP_20261014_MAIN_ENV_DIR}` (디렉터리 `700`, 비공개 파일 `600`). 이전 소스·Compose·두 DB의 일관된 덤프와 검증 결과를 보관한다. 이 경로와 덤프를 Git이나 문서 첨부로 공유하지 않는다.
- 이전 이미지 태그: `danzzan-be:rollback-20261014-main-env`, `danzzan-fe:rollback-20261014-main-env`.

검증 결과:

- BE 테스트 총 535개 중 522개 통과, 13개 건너뜀, 실패 0개 및 `bootJar` 성공. macOS의 한글 중첩 테스트 클래스 검색 문제를 피하기 위해 임시 Gradle init script로 직접 중첩 클래스 스캔만 제외했으며 JUnit의 중첩 테스트 실행은 유지했다.
- FE는 직전 병합 검증에서 129개 파일·647개 테스트, lint/typecheck/build를 통과한 동일 소스를 사용했다. 이번 VM에서 BE·FE Docker 빌드가 모두 성공했다.
- HTTPS 홈·공지·관리자 로그인 경로와 health·축제 설정·홈 이미지·공지·부스맵·날짜를 지정한 공연 조회 API가 HTTP 200을 반환했다. 실제 브라우저에서 공지 제목·목록·하단 메뉴 렌더링도 확인했다.
- 기존 콘텐츠와 이미지 참조를 포함한 19개 테이블의 정규화 덤프 해시가 배포 전후 일치했다. Blob 업로드·삭제 요청은 수행하지 않았다.
- 두 자동 실행 차단 플래그의 실제 컨테이너 값과 시작 로그를 확인했다. 이번 배포에서는 관리자 쓰기·로그인·발권·전체 이미지 로드·부하 테스트를 다시 실행하지 않았다.

복구할 때는 먼저 현재 소스와 설정을 별도로 보존한다. 기존 Compose를 원래 위치로 복원하고 위 이전 이미지 태그를 지정한 override를 사용해 `app`, `frontend`만 `--no-build --no-deps --force-recreate`로 재생성한다. Compose의 상대 경로 기준은 `${DEPLOY_ROOT}/Danzzan-BE`로 유지해야 한다. 이후 health와 HTTPS를 다시 확인한다. 이번 배포는 스키마 변경이 없으므로 애플리케이션 롤백을 위해 DB 덤프를 덮어쓰지 않는다. 소스까지 되돌릴 때는 백업의 이전 소스를 사용하고 비공개 설정은 별도로 비교한다.

커밋 정리 시 외부 서비스 샘플에는 빈 자격 증명과 명시적인 테스트 값만 남겼다. 실제 `secrets/` 파일은 Git 제외를 확인했다. 로컬 Compose `config --quiet`와 번역 보정·용어집 대상 테스트 17건이 통과했다. 이 검증은 외부 API를 호출하거나 Azure를 다시 배포하지 않는다.

## 기본 테마·5월 색상 프리셋 배포 (2026-10-14)

`20261014-theme-presets`로 다음 변경을 기존 VM에 반영했다. BE의 `AdminThemeController`, `ThemeService`와 FE의 테마 API·색상 적용·기본 팔레트·관리 화면·편집 도우미 소스 등 총 8개 파일만 전달했으며 각 SHA-256이 로컬과 일치함을 확인했다. 환경변수·Compose·SQL·이미지 파일은 이번 전송에 포함하지 않았다.

- BE: `PUT /admin/festival/{festivalSettingId}/themes/default/activate` 추가. 명시적 요청 시에만 현재 축제의 활성 테마 참조를 해제한다. 기존 테마 레코드나 이미지에는 쓰지 않는다.
- FE: `Tangerine Cream`(초기 주황·크림), `Coastal Sunset`(PR #103의 일반 화면 청회색·살구색) 선택지를 제공한다. 과거 소스와 14색 대응은 FE `docs/azure-image-integration.md`에 기록했다. 조회·선택은 데이터를 생성하지 않으며 Coastal Sunset의 저장·활성화는 적용 버튼을 누를 때만 수행한다.
- BE 테마 서비스 테스트 14개 통과. FE 마지막 이름 변경 관련 테스트 64개 통과(앞선 전체 검사 130개 파일·660개 통과). VM BE·FE Docker 빌드 모두 성공.
- `app`을 먼저 `up -d --no-deps --wait`로 교체하고 healthy 확인 후 `frontend`를 재생성했다. MySQL·Redis·Kafka의 컨테이너 ID는 그대로다. `ddl-auto=validate`를 유지했으며 스키마 이관은 실행하지 않았다.
- 기존 게시물·이미지 참조·축제 설정·테마를 포함한 20개 테이블의 정규화 데이터 덤프 해시가 배포 전후 및 UI 확인 후 일치했다. 덤프 내용은 출력·전송하지 않고 VM에서 해시로 비교했다. Blob 업로드·삭제는 실행하지 않았다.
- HTTPS 홈·공지·관리자 테마 페이지·health HTTP 200, 내부 health와 Nginx 설정 검사 성공. 실제 관리자 브라우저에서 두 이름·미리보기·읽기 전용 색상 입력을 확인했다. 기존 활성 테마는 유지했다. 운영 환경의 활성화·복제·삭제 요청은 실행하지 않았다.

복구 자료는 `${BACKUP_20261014_THEME_PRESETS_DIR}`에 권한 700으로 보관한다. `sources-before.tar.gz`, `source-manifest.json`, `deployed-images.txt`, 배포 로그와 콘텐츠 해시 비교 결과가 있다. 이전 실행 이미지 태그는 `danzzan-be:rollback-20261014-theme-presets`, `danzzan-fe:rollback-20261014-theme-presets`다. 롤백은 이 이미지로 `app`·`frontend`만 교체하며 DB 복원이나 볼륨 삭제는 필요 없다. SSH는 DNS 변경 전부터 보관한 호스트 키를 `HostKeyAlias=danzzan-demo-2026.koreacentral.cloudapp.azure.com`, `StrictHostKeyChecking=yes`로 검증했다.

커밋 정리 시 BE `ThemeServiceTest` 14건을 다시 실행해 통과했다. 기본 테마 활성화는 HTTP 204를 반환하며, 활성 테마 ID만 해제한다. 저장된 테마 레코드와 이미지 참조는 유지한다. 관련 API·프리셋의 FE 변경과 함께 사용해야 관리자 화면에서 기본 색상을 다시 선택할 수 있다.

## 동료 계정 또는 다른 VM으로 옮길 때

Azure 제공 DNS 이름은 공인 IP 리소스에 연결된다. 다른 구독·다른 VM으로 이동하면 FQDN이 달라질 수 있으며, 다음 위치를 새 실제 FQDN에 맞춰 바꾼다.

| 위치 | 변경할 값 |
| --- | --- |
| `docker-compose.azure.yml` | `CORS_ALLOWED_ORIGINS` |
| FE/Nginx HTTPS 서버 설정 | `server_name`, 인증서 경로와 HTTP redirect 대상 |
| Certbot 발급·webroot 설정 | `-d <새 FQDN>` 및 인증서 이름 |
| `deploy/azure/renew-nginx.sh` | Compose 경로가 달라졌을 때만 실제 배포 경로 |
| Azure NSG | 웹 80·443, 작업자 SSH `/32` 규칙 |

다른 Azure 테넌트의 VM은 현재 저장소 계정의 시스템 할당 관리 ID를 그대로 사용할 수 없다. 저장소를 함께 쓸 경우 대상 VM 관리 ID에 `images` 컨테이너의 Storage Blob Data Contributor와 저장소 계정의 Storage Blob Delegator를 새로 할당한다. 새 저장소로 옮기면 endpoint와 컨테이너, 역할 범위를 함께 바꾸고 기존 Blob이 자동 이관되지 않는다는 점을 확인한다.

## 평가 종료 후

서비스 준비가 끝났고 평가 기간이 아닐 때는 포털 또는 CLI에서 VM을 **할당 취소**한다. OS 종료만으로는 컴퓨팅 과금이 멈추지 않을 수 있다. 디스크·정적 공인 IP·Blob은 할당 취소 후에도 비용이 남으므로, 평가가 끝난 뒤 보관 기간과 삭제 대상을 별도로 결정한다.


## Coastal Sunset·포스터 자산 FE 추가 배포 (2026-10-14)

`20261014-coastal-sunset`으로 FE의 메인색 보정(`brand.primary=#073161`)과 커밋 `c300d4bf`까지의 포스터 자산 변경을 반영했다. BE 코드·환경변수·Compose는 변경하지 않고 frontend만 `--no-build --no-deps --force-recreate`로 교체했다. 관련 FE 테스트 87개와 VM Docker 빌드(타입 검사 포함)가 통과했다.

실제 관리자 브라우저에서 기본 Coastal Sunset의 새 HEX를 확인했고 기존 활성 저장 테마는 유지했다. 적용 버튼은 누르지 않았다. 이미지 4개 HTTPS 응답과 로컬 파일 해시가 일치했다. Nginx 설정·HTTPS 주요 경로 정상, BE/MySQL/Redis/Kafka 컨테이너 ID 및 20개 콘텐츠 테이블의 데이터 해시가 배포 전후 동일했다. 앞서 옮긴 11/27·28 일정 조회도 정상이다.

복구 위치: `${BACKUP_20261014_COASTAL_SUNSET_DIR}/` (권한 700). 이전 소스 아카이브·전송 파일 해시·비공개 빌드 및 검증 로그를 보관했다. 이전 이미지 `danzzan-fe:rollback-20261014-coastal-sunset`을 FE 이미지로 지정하고 frontend만 재생성하면 애플리케이션 복구가 가능하다. DB 덮어쓰기·데이터 컨테이너 재시작은 필요하지 않다. 상세 자산과 UI 확인 기록은 FE `docs/azure-image-integration.md` 10절에 있다.

## 홈 이미지 단독 저장·테마 선택 화면 배포 (2026-10-14)

`20261014-home-theme`로 BE 6개·FE 7개 런타임 소스 파일을 배포했다. 기준 커밋은 BE `0273507`, FE `b8a2a65d`이며 이번 미커밋 변경을 포함한다. 배포 전 허용 목록의 빌드 입력 844개를 비교하고, 변경된 13개 파일만 전달한 뒤 전체 입력 해시가 로컬과 같음을 확인했다. 비밀 파일, Compose, SQL, 업로드 이미지 및 공개 이미지 자산은 전송하지 않았다.

- BE: 첫 홈 이미지 단독 저장 API와 원본 비율을 유지하는 포스터 미리보기. 스키마 변경 없음.
- FE: 파일 선택/포스터 편집 → 미리보기 → 홈화면 이미지 저장/선택 취소. 테마 전체 적용에서 홈 이미지 저장을 분리했다. `/admin/theme/select` 화면과 메뉴를 추가하고 기존 제작 화면 하단 목록도 유지했다.
- 로컬 검증: 관련 FE 6개 파일·113개 테스트, 앱·테스트 타입 검사, 대상 ESLint 및 빌드 통과. BE 저장 API·동시성·권한 회귀 28개 및 렌더러 3개 테스트 통과 이력을 확인했다. VM BE·FE Docker 빌드도 성공했다.
- 먼저 app을 `--no-build --no-deps --force-recreate --wait`로 교체해 healthy를 확인한 뒤 frontend만 교체했다. MySQL·Redis·Kafka 컨테이너 ID 및 Compose·기존 비공개 환경 파일 해시는 그대로다.
- 홈페이지, 공지, 관리자 테마, 별도 테마 선택, `/api/health` HTTPS 200과 Nginx 설정 검사를 확인했다. 실제 관리자 브라우저에서 새 버튼·편집/취소·테마 선택 목록을 확인했다. 실제 파일 업로드·저장·테마 적용·AI 생성 요청은 수행하지 않았다.
- 게시물·이미지 참조·축제 설정·테마 등 기존 20개 콘텐츠 테이블의 정규화 데이터 해시가 배포 전후 동일했다. Blob 업로드·삭제 및 데이터 초기화는 실행하지 않았다.

복구 자료는 `${BACKUP_20261014_HOME_THEME_DIR}/`(권한 700)에 보관한다. 이전 소스 `sources-before.tgz`, 변경/전체 파일 해시, 기준 버전 `release-info.json`, 빌드·배포 로그, 콘텐츠·컨테이너·설정 해시 비교 결과를 포함한다. 이전 실행 이미지는 `danzzan-be:rollback-20261014-home-theme`, `danzzan-fe:rollback-20261014-home-theme`다. 복구 시 이 이미지를 지정해 app·frontend만 재생성한다. 데이터베이스 복원이나 볼륨 삭제는 필요하지 않다. 소스를 되돌릴 때는 이전 소스 아카이브와 manifest를 함께 확인하며, 이번 신규 DTO 파일도 구분한다.


## 테마 색상 고정·기본 펼침·홈 표시 방식 배포 (2026-10-14)

`20261014-theme-policy`로 BE·FE를 함께 배포했다. 기준 커밋은 BE `0273507`, FE `b8a2a65d`이며 이번 미커밋 변경을 포함한다. 빌드 입력 845개 경로를 비교하고 변경된 9개 파일과 불필요해진 기존 보정기 소스 1개 삭제만 전달한 뒤 전체 입력 해시 일치를 확인했다. 비밀 환경 파일·Compose·SQL·이미지 자산은 전송하지 않았다.

- 테마 생성·추천·수정 저장 시 상태 4색과 주점 외 지도 5색을 기존 Theme Core 기본값으로 고정하고 주점은 최종 `brand.primary`와 같게 만든다. 기존 저장 테마 조회·활성화로 데이터를 자동 변경하지 않는다.
- 고급 설정·브랜드·상태·지도 카테고리 토글은 기본 펼침으로 표시한다.
- 홈 이미지와 화면 미리보기는 5월 운영 방식의 중앙 `object-cover`로 영역을 채운다. 내부 상하 여유 공간을 제거했으며 비율 차이에 따라 가장자리가 잘릴 수 있다. 저장된 이미지 파일은 수정하지 않았다.
- 로컬 BE 관련 40개 테스트, FE 관련 69개 테스트와 빌드·테스트 타입 검사·변경 코드 ESLint를 통과했다. VM에서도 BE·FE Docker 빌드 성공 후 app·frontend만 재생성했다. MySQL·Redis·Kafka 컨테이너 ID와 Compose·비공개 환경 파일 해시는 배포 전후 동일하다.
- 앱 healthy, 실행 이미지와 빌드 이미지 일치, Nginx 설정 검사 성공. `/`, `/notice`, `/admin/theme`, `/admin/theme/select`, `/api/health`는 HTTPS 200이다.
- 실제 관리자 화면에서 기본 펼침과 읽기 전용 고정 색상을 확인했다. 새 테마 초안에서 Primary를 바꾸면 주점도 같이 바뀌는 것을 확인하고 저장 없이 새로고침했다. 기존 활성 테마를 유지했고 실제 AI 호출·테마 저장·활성화는 수행하지 않았다. 홈 이미지의 정상 로드와 부모 영역 전체 높이를 채우는 cover 표시도 확인했다.
- 20개 콘텐츠 테이블의 정규화 데이터 해시는 배포 전후 및 UI 확인 후 일치했다. 자동 번역 backfill·시작 시 glossary sync는 비활성 상태를 유지한다. Blob 쓰기·삭제, DB 스키마 변경은 수행하지 않았다.

복구 자료는 `${BACKUP_20261014_THEME_POLICY_DIR}/`(권한 700)에 보관한다. 이전 소스 `sources-before.tgz`, 변경/전체 manifest, `release-info.json`, 빌드·배포 로그와 데이터·설정 비교 결과를 포함한다. 이전 이미지는 `danzzan-be:rollback-20261014-theme-policy`, `danzzan-fe:rollback-20261014-theme-policy`다. 복구 시 해당 이미지를 지정해 app·frontend만 재생성한다. 소스 복구는 이전 아카이브로 삭제된 보정기를 복원하고 신규 `ThemeColorPolicy.java`도 manifest에 따라 구분한다. DB 복원이나 볼륨 삭제는 필요하지 않다.

## 동료 변경 반영: AI 배경 생성·회원가입 복구 (2026-10-15)

배포 ID는 `20261015-colleague-signup-background`이며 서비스 주소는 `https://danzzan.koreacentral.cloudapp.azure.com`이다. 두 저장소 모두 기존 작업 브랜치의 upstream을 fetch한 뒤 fast-forward로 반영했다. 충돌이나 로컬 코드 수정은 없었다.

| 대상 | 이전 HEAD | 배포 HEAD | 반영 내용 |
| --- | --- | --- | --- |
| BE | `4e807f8` | `e16c05a559187a94c675aa629ab7a637c32819ab` | 공식 포스터 기반 AI 배경 이미지 생성, 회원가입 네이버 ID 검증·저장 복구 |
| FE | `2ab73ad6` | `f1e0839da0e27e5391e25c166d99932dd8ffa8ed` | AI 배경 생성 대기·전체 적용 흐름 개선, 모바일 티켓팅 OFF 배경 암전 완화, 네이버 ID 입력 복구 |

### 실행 계약과 사전 확인

- 배경 추천은 공식 포스터를 OpenAI Images Edit API에 전달해 새 이미지를 생성한다. 단순 포스터 자르기를 대체하며, 결과는 티켓용 800×360 및 마감 안내용 720×1440 JPEG로 처리한다. 서버 시작만으로 AI를 호출하지 않는다.
- 코드 기본값은 `OPENAI_BACKGROUND_MODEL=gpt-image-1.5`, `OPENAI_BACKGROUND_BASE_URL=https://api.openai.com/v1/images/edits`다. 기존 `OPENAI_API_KEY`가 설정되어 있는지만 확인했으며 값은 출력하지 않았다. 이번 배포에서 비공개 환경 파일은 수정하지 않았다.
- BE 외부 요청 제한은 150초, FE 요청 대기와 Nginx의 배경 추천 전용 경로 `/api/admin/festival/ticketing-background/recommend`의 읽기 제한은 180초다. 일반 API 경로의 제한과 구분한다.
- 회원가입은 네이버 ID와 재입력을 검증하고 공백을 정리한 값을 저장한다. 운영 DB `danzzan_fall_demo.users.naver_id`가 이미 nullable `varchar(100)`임을 확인했다. SQL 이관이나 `ddl-auto` 변경 없이 배포했다.
- 자동 번역 backfill과 시작 시 glossary sync가 꺼져 있음을 확인하고 그대로 유지했다.

### 이번 배포에서 확인한 증거

1. 배포 HEAD와 PR HEAD가 같은 것을 확인한 뒤 [BE CI](https://github.com/DKU-Dan-Zzan/Danzzan-BE/actions/runs/38697437083/job/116297238200)의 Java 17 `clean test bootJar` 성공을 확인했다. 로컬 BE 전체 테스트를 새로 실행한 것은 아니다.
2. FE는 로컬에서 lint, 앱·테스트 타입 검사, 전체 테스트 **131개 파일·679개**, 프로덕션 빌드를 새로 실행해 통과했다. [FE quality CI](https://github.com/DKU-Dan-Zzan/Danzzan-FE/actions/runs/38697435431/job/116297233294), [구조 검사 CI](https://github.com/DKU-Dan-Zzan/Danzzan-FE/actions/runs/38697435456/job/116297232958)와 두 저장소 GitGuardian 검사도 해당 HEAD에서 성공했다. 로컬 빌드에는 Browserslist 데이터 갱신 안내가 남았다.
3. 전체 빌드 입력 979개 경로를 비교해 변경된 26개 파일과 더 이상 쓰지 않는 Java 소스 1개 삭제를 반영했다. 전송 후 및 배포 후 전체 입력 해시가 로컬과 일치했다. 이미지 파일·SQL·비공개 환경 파일은 변경하지 않았다.
4. VM에서 BE와 FE 이미지를 순차 빌드했다. `app`을 `--no-build --no-deps --force-recreate --wait`로 교체해 healthy를 확인한 뒤 `frontend`를 교체했다. 실행 이미지와 빌드 이미지 ID 일치 및 Nginx 설정 검사 성공을 확인했다.
5. HTTPS `/`, `/notice`, `/admin/theme`, `/admin/theme/select`, `/ticket/signup`, `/api/health`, `/api/festival/settings`, `/api/home/1/images`가 모두 200이었다. API 응답은 JSON 파싱도 확인했다. 실제 브라우저에서 홈의 로고·포스터 로드와 회원가입 첫 인증 화면 표시를 확인했다.
6. 게시물·이미지 참조·축제 설정·테마 등 20개 콘텐츠 테이블의 데이터 해시가 배포 전후 일치했다. MySQL·Redis·Kafka 컨테이너 ID, Compose와 비공개 설정 파일 해시도 모두 유지됐다. Blob 업로드·삭제, 계정 생성 및 DB 초기화는 실행하지 않았다.

실제 유료 AI 이미지 생성과 학교·SMS 인증을 거치는 회원가입 완료는 이번 운영 검증 범위에 포함하지 않았다. 따라서 배포·기본 조회 정상과 전체 사용자 흐름의 실서비스 성공을 구분한다. AI 모델 접근 권한·실제 생성 지연은 이후 실제 요청으로 확인해야 한다.

### 복구 자료

VM의 `${BACKUP_20261015_COLLEAGUE_SIGNUP_BACKGROUND_DIR}/`에 권한 700으로 배포 전 변경 대상 소스 `sources-before.tgz`, 이전 경로 목록, 전체 입력 manifest, `release-info.json`, 빌드·배포 로그, 데이터·설정 비교 기록 및 `verification.json`을 보관했다. `rollout-success`는 배포 검증 완료 표식이다. 소스 아카이브는 전체 저장소 백업이 아니라 이번 변경 대상의 이전 버전이다.

이전 실행 이미지 태그는 `danzzan-be:rollback-20261015-colleague-signup-background`와 `danzzan-fe:rollback-20261015-colleague-signup-background`다. 복구할 때는 해당 이미지로 app·frontend만 `--no-build --no-deps --force-recreate` 재생성하고 health를 확인한다. 소스도 되돌려야 하면 이전 경로 목록과 아카이브를 대조해 신규 파일과 삭제 파일을 구분한다. 이번 배포에는 스키마 변경이 없어 DB 복원이나 볼륨 삭제가 필요하지 않다.

## FE 내 정보·로그인 안내 배경 분리 배포 (2026-10-21)

배포 ID `20261021-account-background-48d0e8a0`으로 FE 커밋 `48d0e8a0f7ecca82c37165059030756625c5b9ce`를 반영했다. 티켓팅 OFF 배경을 내 정보·로그인 안내에도 공유하던 연결을 제거하고, 두 계정 화면은 공식 축제 포스터를 사용하도록 분리했다. 관리자 미리보기와 설명도 함께 수정했다. 상세 화면 변경과 로컬 테스트는 [FE Azure 이미지 연동 문서](../../Danzzan-FE/docs/azure-image-integration.md)의 같은 날짜 기록에 있다. 상대 링크는 두 저장소를 나란히 둔 로컬 배치용이다.

### 배포와 검증

- 기존 SSH 연결이 시간 초과되어 Azure VM Run Command로 진행했다. SSH 방화벽 규칙이나 인증 설정을 변경하지 않았다.
- 이전 FE 이미지와 변경 대상 8개 파일을 백업한 뒤 같은 8개 파일만 전송했다. 사전 소스 해시 검사 557개 경로 중 `.env.example`, `nginx.conf`는 로컬 기준과 달랐으며 덮어쓰지 않았다. 수정 대상 파일의 변경 전·후 해시는 각각 기준 커밋·배포 커밋과 일치했다. Azure 런타임은 `Dockerfile.azure`와 `nginx.azure.conf`를 사용한다.
- VM에서 FE Docker 이미지를 빌드하고 `frontend`만 `--no-build --no-deps --force-recreate`로 교체했다. `app`·MySQL·Redis·Kafka 컨테이너 ID와 Compose·기존 비공개 환경 파일 해시는 유지됐다. BE 코드·DB 스키마·Blob 파일 변경은 없었다. DB 덤프 해시 비교는 이번에 별도로 실행하지 않았다.
- 로컬 관련 4개 파일·108개 테스트, 앱·테스트 타입 검사, 변경 파일 ESLint 및 프로덕션 빌드가 통과했다. VM Nginx 설정 검사와 HTTPS `/`, `/mypage`, `/ticket`, `/admin/theme`, `/api/health` 요청도 성공했다.
- 외부 `/mypage`·`/api/health`의 HTTP 200과 실제 운영 비로그인 안내의 공식 포스터·로고 로드를 확인했다. 로그인 후 내 정보 화면은 자동 테스트 범위이며 이번 운영 브라우저에서는 재검증하지 않았다.

실행 FE 이미지 ID는 `sha256:a0ef0b53323454ea78308f16492f66dc2aefe9e829f7fd9952d32d0287fe0182`다. 이미지 내부 `assets/MyPage-oes32tms.js`와 외부 HTTPS 응답의 SHA-256은 모두 `031054017d639930bdb5e1890c40933c4cb7d8123aff564a091ff5f76f4b6dde`로 일치했다.

### 복구 자료와 절차

VM `${BACKUP_20261021_ACCOUNT_BACKGROUND_48D0E8A0_DIR}/`에 다음 자료를 보관한다. 비밀값·환경 파일 내용·DB 덤프는 이 문서에 포함하지 않는다.

- `sources-before.tgz`: 변경 대상 8개 파일의 배포 전 버전. 전체 저장소 백업이 아니다.
- `changes.tgz`, `before.json`: 전송 파일과 배포 전 컨테이너 ID·설정 해시·배포 파일 기대 해시.
- `rollback.yml`: 이전 FE 이미지 `danzzan-fe:rollback-20261021-account-background-48d0e8a0`를 지정한 override.
- `deploy.sh`, `verify.py`, 빌드·활성화·Nginx 검사 로그, `deployed-image.txt`: 실행 및 확인 자료.
- `status`: 배포 완료 시 `complete`. 이번 배포에서 자동 롤백은 실행되지 않았다.

이미지 복구가 필요하면 VM에서 아래 명령으로 frontend만 교체한다. 실행 전에 이후 배포가 있는지 확인하고, 현재 이미지와 추가 소스 변경을 별도로 보존한다.

```sh
: "${BACKUP_20261021_ACCOUNT_BACKGROUND_48D0E8A0_DIR:?Set BACKUP_20261021_ACCOUNT_BACKGROUND_48D0E8A0_DIR from the private operations guide}"
: "${DEPLOY_ROOT:?Set DEPLOY_ROOT from the private operations guide}"
cd "${DEPLOY_ROOT}/Danzzan-BE"
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml \
  -f "${BACKUP_20261021_ACCOUNT_BACKGROUND_48D0E8A0_DIR}/rollback.yml" \
  up -d --no-build --no-deps --force-recreate frontend
curl -fsS https://danzzan.koreacentral.cloudapp.azure.com/mypage >/dev/null
curl -fsS https://danzzan.koreacentral.cloudapp.azure.com/api/health >/dev/null
```

이미지 복구만으로 VM 소스는 되돌아가지 않는다. 다음 빌드에서도 이전 동작을 유지해야 한다면 추가 변경을 보존한 후 `sources-before.tgz`의 8개 파일을 `${DEPLOY_ROOT}/Danzzan-FE`에 복원한다. DB 복원이나 볼륨 삭제는 필요 없다. 이 절차는 복구용으로 기록했으며 이번 정상 배포 후 실행하지 않았다.

## AI 색상 시안 생성 시간 제한 연장 배포 (2026-10-21)

배포 ID는 `20261021-theme-timeout`이다. BE `05b18bf`, FE `59690e20`을 각 기존 Azure 작업 브랜치에 커밋·푸시한 뒤 반영했다. main 병합은 실행하지 않았다.

### 변경 이유와 적용 범위

배포 전 최근 24시간의 운영 진단 로그에서 색상 추천은 OpenAI 응답 처리 성공 1건·시간 초과 11건, 배경 생성은 성공 2건·시간 초과 10건으로 집계됐다. 색상 실패는 약 45초의 `OPENAI_TIMEOUT`이며 해당 로그에는 색상 검증 탈락이 없었다. OpenAI 처리 지연과 전송·연결 지연 중 어느 것이 근본 원인인지는 확정하지 못했다. 사용자가 더 긴 대기를 허용해 색상 생성의 제한을 다음과 같이 조정했다.

| 구간 | 이전 | 이번 배포 |
| --- | --- | --- |
| BE → OpenAI 색상 생성 요청 | 45초 | 150초 |
| FE 색상 추천 중단 타이머 | 60초 | 180초 |
| Nginx `/api/admin/festival/1/themes/recommend` | 일반 API 제한 75초 | 전용 경로 180초 |

배경 생성은 기존 BE 150초·FE/Nginx 180초, 일반 Nginx API는 75초를 유지했다. 연결 수립 제한·모델·프롬프트·색상 검증 규칙·포스터 전송 방식은 변경하지 않았으며 자동 유료 재시도도 추가하지 않았다. 이번 배포는 더 오래 기다릴 수 있게 하는 변경이며 AI 성공률 개선을 입증한 결과는 아니다.

### 배포와 검증

- 로컬 BE `ThemeRecommendationTest` 24개, FE `AdminTheme.test.tsx` 64개가 통과했다. BE는 150초 요청 제한과 시간 초과 오류 반환·호출 1회를 확인했다. FE는 60초·150초에는 취소하지 않고 180초에 취소하는 가상 시간 테스트를 추가했다. FE 타입 검사·변경 파일 ESLint·프로덕션 빌드와 두 저장소의 diff 검사가 통과했다.
- Azure VM Run Command로 기준 빌드 입력 969개 경로의 해시를 비교했고 모두 일치했다. 별도 전송 전 검사에서 VM의 BE 테스트 파일이 로컬과 달라, 테스트 파일은 덮어쓰지 않고 실행에 필요한 BE Java 소스·FE 화면 소스·Nginx 설정 3개만 전달했다. 테스트 변경은 Git에 포함돼 있다.
- `application-prod.yml`의 `ddl-auto: validate`와 이를 덮어쓰는 환경 설정이 없음을 확인했다. SQL 이관·DB 복원·Blob 변경은 실행하지 않았다.
- 이전 두 이미지를 보관한 뒤 VM에서 BE·FE Docker 빌드를 수행했다. `app`만 교체해 healthy를 기다린 다음 `frontend`를 교체했다. 실패 시 이전 app·frontend 이미지와 변경 전 소스를 복원하는 스크립트를 준비했다.
- 실행 중인 BE JAR에서 색상 추천 클라이언트의 `TIMEOUT=Duration.ofSeconds(150)` 바이트코드를 확인했다. 실행 프론트의 AdminTheme 번들에서 180초 중단 설정, Nginx 실행 설정에서 색상·배경 전용 경로 180초와 일반 API 75초를 확인했다. 실행 이미지 ID는 빌드한 이미지와 일치했다.
- Nginx 설정 검사와 HTTPS `/`, `/mypage`, `/admin/theme`, `/api/health` 요청이 성공했다. 외부에서 받은 AdminTheme 번들의 해시도 실행 이미지 내부 파일과 일치했다.
- MySQL·Redis·Kafka 컨테이너 ID와 Compose·기존 비공개 환경 파일 해시는 유지됐다. 데이터 내용의 덤프 해시 비교는 별도로 수행하지 않았다.

실제 유료 AI 생성 요청은 이번 배포 검증에서 보내지 않았다. 따라서 45초 이후의 실제 생성 성공, 전체 적용·이미지 업로드 성공 및 장기 실패율은 미검증이다. 후속 실제 요청의 처리 시간과 결과를 확인해야 한다.

### 복구 자료와 절차

VM `${BACKUP_20261021_THEME_TIMEOUT_DIR}/`에 변경 전 실행 파일 3개의 `sources-before.tgz`, 컨테이너 ID·설정/파일 해시·코드 커밋을 담은 `before.json`, `rollback.yml`, 배포·검증 스크립트와 로그, 실행 이미지·제한값·프론트 번들 해시를 담은 `verification.json`을 보관한다. `status=complete`는 배포 검증 완료를 뜻한다.

롤백 이미지 태그는 `danzzan-be:rollback-20261021-theme-timeout`, `danzzan-fe:rollback-20261021-theme-timeout`이다. 이후 배포가 없는지 확인하고 현재 이미지·추가 변경을 보존한 뒤, VM에서 아래 명령으로 BE를 healthy 상태까지 복구하고 FE를 재생성한다. FE 재생성은 복구한 BE의 내부 주소를 Nginx에 반영하는 데도 필요하다.

```sh
: "${BACKUP_20261021_THEME_TIMEOUT_DIR:?Set BACKUP_20261021_THEME_TIMEOUT_DIR from the private operations guide}"
: "${DEPLOY_ROOT:?Set DEPLOY_ROOT from the private operations guide}"
cd "${DEPLOY_ROOT}/Danzzan-BE"
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml \
  -f "${BACKUP_20261021_THEME_TIMEOUT_DIR}/rollback.yml" \
  up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 app
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml \
  -f "${BACKUP_20261021_THEME_TIMEOUT_DIR}/rollback.yml" \
  up -d --no-build --no-deps --force-recreate frontend
curl -fsS https://danzzan.koreacentral.cloudapp.azure.com/api/health >/dev/null
```

소스까지 복구해야 하면 추가 변경을 보존한 후 `sources-before.tgz`를 `${DEPLOY_ROOT}` 기준으로 복원한다. 아카이브는 전체 저장소 백업이 아니다. 스키마 변경이 없어 DB 복원이나 볼륨 삭제는 필요 없다. 이 롤백 명령은 복구용으로 기록했으며 정상 배포 후 실행하지 않았다.

## OpenAI 키·색상 모델 교체 배포 (2026-10-21)

배포 ID는 `20261021-theme-model-key`다. 색상 생성의 150초 시간 초과가 재발한 뒤 사용자 요청에 따라 OpenAI API 키를 교체하고 색상 추천 모델을 `gpt-6-luna`로 변경했다. 이번 기록은 설정 적용과 서버 상태 확인에 관한 것이며, 시간 초과의 근본 원인이나 장기 성공률 개선을 확정한 결과는 아니다.

### 적용 설정과 코드

VM `${DEPLOY_ROOT}/Danzzan-BE/deploy/azure/secrets/external-services.env`에서 다음 항목만 변경했다. 실제 키는 Git과 문서에 포함하지 않는다.

| 항목 | 배포 전 | 배포 후 |
| --- | --- | --- |
| `OPENAI_API_KEY` | 기존 키 | 사용자가 새로 발급한 키 |
| `OPENAI_THEME_MODEL` | `gpt-4o-mini` | `gpt-6-luna` |
| `OPENAI_THEME_REASONING_EFFORT` | 미설정 | `none` |

- `OpenAiThemeRecommendationClient`에 선택적인 `openai.theme-reasoning-effort` 설정을 추가했다. 값이 있을 때만 Responses API 요청에 `reasoning.effort`를 보낸다. 빈 값이면 기존 요청 형식을 유지한다.
- `application.yml`에서 `OPENAI_THEME_REASONING_EFFORT`를 연결했다. 공통 모델 기본값은 기존 `gpt-4o-mini`로 유지하고, Azure의 명시적인 환경 설정으로 `gpt-6-luna`를 선택한다. 로컬에서 같은 모델을 쓰려면 모델과 추론 설정을 함께 지정해야 한다.
- Azure 환경 파일 예시는 `gpt-6-luna`와 `none` 조합으로 갱신했다. 기존 모델로 되돌릴 때는 추론 설정도 해당 모델 지원 여부에 맞게 비워야 한다.
- 색상 요청의 150초 제한, FE·Nginx의 180초 제한, 프롬프트·포스터 입력·JSON Schema·2,400 출력 토큰 한도와 팔레트 검증 규칙은 유지했다. 자동 재시도는 추가하지 않았다.
- 배경 생성 모델과 엔드포인트는 변경하지 않았다. 두 클라이언트가 같은 `OPENAI_API_KEY` 설정을 사용하므로 새 키는 배경 생성에도 적용된다.

### 배포 절차와 확인 결과

1. 로컬 비공개 환경 파일의 키 존재 여부, 모델·추론 설정과 공식 엔드포인트를 확인했다. 새 키로 OpenAI `GET /v1/models/gpt-6-luna`를 조회해 HTTP 200과 대상 모델 ID를 확인했다. 이는 모델 조회 권한 확인이며 생성 요청의 성공이나 계정 한도를 입증하지 않는다.
2. Azure VM Run Command로 기존 컨테이너 ID와 소스·설정 파일 해시를 기록했다. 수정할 Java 클라이언트와 `application.yml`의 VM 내용이 로컬 HEAD 기준과 같은지 확인하고, 전송 직전 변경 여부를 다시 검사했다.
3. VM에서 일회용 RSA 공개키를 생성하고 새 API 키를 OAEP/SHA-256으로 암호화해 전달했다. 평문 키를 명령 출력이나 전송 스크립트에 넣지 않았다. VM의 비공개 환경 파일은 권한 `600`으로 갱신했고, 정상 배포 후 일회용 개인키를 삭제했다.
4. 변경 전 소스 2개·비공개 환경 파일·BE 이미지를 보관한 뒤 소스 2개만 전달해 `app` 이미지를 빌드했다. 빌드 성공 후 환경 설정을 갱신하고 `app`만 재생성해 healthy를 기다렸다. FE 이미지는 유지하고 Nginx 설정 검사와 reload로 새 BE 주소를 반영했다.
5. 배포 스크립트의 `status=complete`, 실행 컨테이너의 `gpt-6-luna`·`none`, 키가 이전 값과 다르고 새 환경 파일과 일치함을 확인했다. 실행 BE 이미지 ID는 `sha256:e7a7a1ae3038d05c9e9bc868884ee37a025f5dcad89adc8b076d9afe5f1eea59`다.
6. MySQL·Redis·Kafka 컨테이너 ID, Compose와 다른 비공개 환경 파일의 해시를 비교해 유지됨을 확인했다. 외부 서비스 환경 파일에서도 위 3개 항목 외의 값은 같았다. SQL 적용·DB 복원·Blob 저장/삭제는 수행하지 않았으며 데이터 내용 전체의 해시 비교는 하지 않았다.

로컬 `ThemeRecommendationTest`는 26개 통과했다. 추가 테스트는 `gpt-6-luna`·`none` 요청에서도 이미지 입력과 strict JSON Schema가 유지되는지, 추론 설정이 비어 있으면 해당 필드가 빠지는지를 확인한다. 배포 전 동일 코드로 Gradle 테스트 명령을 다시 실행했으며 기존 성공 결과를 `UP-TO-DATE`로 재사용했다. VM의 Docker 빌드·Nginx 설정 검사·app health도 통과했다. 배포 진행 중 공개 HTTPS `/api/health`에서 HTTP 200을 확인했으며 이를 새 이미지 교체 후 확인으로 혼동하지 않는다.

### 실제 시안 검증의 범위

사용자는 실제 시안 생성 성공을 알렸고 관리자 화면에서 추천 완료·원본형 시안을 확인했다. 다만 당시 배포 상태 조회는 빌드 중이었으므로, 이 결과를 새 키·모델 적용 후의 성공으로 귀속하지 않았다. 새 컨테이너 적용 확인 후 별도 검증 화면에서 대표색 추출을 시작했지만 사용자가 검증 중단을 요청해 기준색 선택·추가 AI 생성·저장·전체 적용은 실행하지 않았다.

따라서 **새 키·모델의 운영 적용과 서버 정상 상태는 확인했고, 새 설정을 사용하는 실제 시안 생성과 두 배경 생성의 성공 여부는 이번 배포 검증 범위에 포함하지 않는다.** `verification.json`의 `generationVerified`도 `false`다. 이후 문서화·커밋 과정에서도 추가 시안 생성 검증을 하지 않았다.

### 복구 자료와 절차

VM `${BACKUP_20261021_THEME_MODEL_KEY_DIR}/`에 다음 자료를 보관한다. 디렉터리는 권한 `700`이며 비공개 환경 백업은 권한 `600`이다.

- `before.json`: 변경 전 컨테이너 ID와 소스·설정 해시.
- `sources-before.tgz`: Java 클라이언트와 `application.yml`의 변경 전 내용. 전체 저장소 백업이 아니다.
- `external-services.env.before`: 변경 전 비공개 환경 파일. 기존 API 키가 포함되므로 출력·공유·Git 추가를 하지 않는다.
- `rollback.yml`: `danzzan-be:rollback-20261021-theme-model-key` 이미지를 지정한 override.
- `payload.json`: 전송 소스와 암호화된 키. 평문 키는 포함하지 않는다.
- `deploy.sh`, `activate.py`, `verify.py`, 빌드·활성화·설정 검사 로그, `verification.json`, `status`: 배포와 확인 자료.

정상 배포 시 자동 복구는 실행되지 않았다. 아래는 **실행하지 않은 복구 절차**다. 이후 배포·설정 변경을 먼저 확인하고 현재 상태를 별도로 보관한 뒤 사용한다. 전체 환경 파일 복원은 다른 외부 서비스의 이후 변경도 되돌리므로, 후속 변경이 있다면 필요한 OpenAI 항목만 복원한다. 기존 키가 폐기됐다면 백업의 키를 복원하지 말고 유효한 새 키를 유지하면서 모델을 `gpt-4o-mini`, 추론 설정을 빈 값으로 조정해야 한다.

후속 변경이 없고 기존 키도 유효하여 전체 복원이 적절한 경우:

```sh
: "${BACKUP_20261021_THEME_MODEL_KEY_DIR:?Set BACKUP_20261021_THEME_MODEL_KEY_DIR from the private operations guide}"
: "${DEPLOY_ROOT:?Set DEPLOY_ROOT from the private operations guide}"
cd "${DEPLOY_ROOT}/Danzzan-BE"
sudo cp "${BACKUP_20261021_THEME_MODEL_KEY_DIR}/external-services.env.before" \
  deploy/azure/secrets/external-services.env
sudo chmod 600 deploy/azure/secrets/external-services.env
sudo tar -xzf "${BACKUP_20261021_THEME_MODEL_KEY_DIR}/sources-before.tgz" \
  -C "${DEPLOY_ROOT}/Danzzan-BE"
sudo docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml \
  -f "${BACKUP_20261021_THEME_MODEL_KEY_DIR}/rollback.yml" \
  up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 app
sudo docker exec danzzan-azure-frontend-1 nginx -t
sudo docker exec danzzan-azure-frontend-1 nginx -s reload
curl -fsS https://danzzan.koreacentral.cloudapp.azure.com/api/health >/dev/null
```

DB 복원이나 볼륨 삭제는 필요 없다. 이 복구 명령은 AI 생성 요청을 보내지 않는다.
