# 가을 축제 영어 지원 — 팀 인수인계

2026 DANFESTA "LEGEND" (09.09~09.10) 대비 작업 정리.
**배포 전에 이 문서의 "반드시 지켜야 할 것" 세 가지를 먼저 읽어주세요.**

## 무엇이 만들어졌나

관리자가 한국어로만 등록하면 DeepL이 영문을 만들어 함께 저장하고,
사용자는 앱 우측 상단 KO/EN 토글로 전체 화면을 영어로 볼 수 있습니다.

번역 대상은 7개입니다: 공지, 긴급공지, 부스, 주점, 단과대, 아티스트, 공연.

가을 축제는 티켓팅을 하지 않으므로 티켓팅·로그인·회원가입·내 정보는
안내 화면으로 대체하고, 백엔드 API도 함께 차단했습니다.

### 브랜치와 PR 순서

| 브랜치 | 지라 | 내용 |
|---|---|---|
| `DANZ-356` (BE) | [BE] 페이지 번역 API 처리 | 번역 API, 티켓팅 차단, 긴급공지 |
| `DANZ-355` (FE) | [FE] 페이지 번역본 UI 구성 | 번역 UI, 언어 토글, 관리자 영문란, 도커 |
| `DANZ-358` (FE) | [FE] 티켓팅 관련된 페이지 오류처리 | 안내 화면 |

**`DANZ-358`은 `DANZ-355` 위에 올라가 있습니다. PR은 355 → 358 순서로 머지해야 합니다.**

---

## 반드시 지켜야 할 것

### 1. 배포 전에 마이그레이션을 먼저 돌린다

```bash
mysql -u <user> -p <database> < scripts/add_translation_columns.sql
```

운영은 `ddl-auto: validate`라 컬럼을 자동으로 만들지 않습니다.
안 돌리고 배포하면 앱이 **아예 기동하지 않고** 이 로그를 남기며 재시작을 반복합니다.

```
Schema-validation: missing column [en_is_manual] in table [emergency_notice]
```

로컬에서 실제로 이 상태를 재현했습니다. 배포 때 겪으면 원인을 찾느라 시간을 씁니다.

이 스크립트는 한 번만 실행하도록 만들어져 있습니다. 이미 적용된 DB에 다시 돌리면
"컬럼이 이미 있다"며 실패합니다. 되돌리려면 각 `ADD COLUMN`에 대응하는
`DROP COLUMN`을 직접 작성해야 합니다.

### 2. 한국어를 DB에 직접 넣을 때는 utf8mb4를 명시한다

```bash
docker exec -i danzzan-mysql mysql --default-character-set=utf8mb4 \
  -udanzzan -pdanzzan1234 danzzan < some.sql
```

**`--default-character-set=utf8mb4`를 빼면 한국어가 이중 인코딩되어 저장됩니다.**
컨테이너 안 mysql 클라이언트가 `latin1`로 접속하기 때문입니다.

이게 지독한 이유는 **증상이 늦게, 엉뚱한 모습으로 드러나기 때문**입니다.

- 같은 클라이언트로 `SELECT` 하면 멀쩡해 보입니다. 넣을 때와 뺄 때 같은 방식으로
  깨지므로 상쇄됩니다.
- 앱은 raw 바이트를 UTF-8로 읽으므로 `ìš°ì²œ ì‹œ...` 같은 글자를 봅니다.
- 그 깨진 글자가 DeepL로 넘어가면 **DeepL은 오류를 내지 않고 그럴듯한 영어 문장을
  지어냅니다.** 실제로 "우천 시 부스 운영 안내"가
  "Lee Seo-hyun, Director of the National Intelligence Service"로 저장됐습니다.

번역이 내용과 전혀 상관없이 나오면 번역 로직이 아니라 인코딩을 먼저 의심하세요.
확인법:

```sql
SELECT HEX(LEFT(title,3)) FROM notice LIMIT 1;
-- '우천' 이면 EC9AB0ECB29C... 로 시작해야 정상
```

축제 데이터를 SQL로 일괄 등록할 계획이라면 특히 주의가 필요합니다.

### 3. DeepL 키는 공개 파일에 넣지 않는다

| 실행 방식 | 키 위치 | 깃 추적 |
|---|---|---|
| 로컬 `bootRun` | `src/main/resources/application-dev.yml` | gitignore |
| 로컬 도커 | `Danzzan-BE/.env` | gitignore |
| 운영 서버 | 서버의 `.env` | gitignore |

`application.yml`과 `application-prod.yml`은 **깃허브 공개**입니다.
여기엔 `${DEEPL_API_KEY:}` 참조만 들어 있고, 실제 값을 쓰면 안 됩니다.

팀원에게 `application-dev.yml`을 전달할 때 아래를 추가해서 주세요.

```yaml
deepl:
  api-key: 발급받은키-:fx까지-전부
```

무료 플랜 키는 `:fx`로 끝나고 `api-free.deepl.com`을, 유료 키는 접미사 없이
`api.deepl.com`을 씁니다. 코드가 키 접미사로 자동 판별하므로 URL은 신경 쓰지
않아도 됩니다. 계정 한도는 월 100만 자이고, 축제 분량 기준으로는 여유롭습니다.

---

## 로컬에서 띄우기

```bash
# 1. 브랜치
#    BE: DANZ-356   FE: DANZ-355 (안내 화면까지 보려면 DANZ-358)

# 2. Danzzan-BE/.env 에 키 추가
#    DEEPL_API_KEY=발급받은키:fx
#    OCTOMO_API_KEY=local-octomo-api-key-placeholder

# 3. 전체 기동 (FE + BE + MySQL + Redis + Kafka)
cd Danzzan-BE
docker compose -f docker-compose.local.yml up -d --build

# 4. 목업 데이터 (선택) — 부스 6, 주점 4, 공연 5, 공지 4건
docker exec -i danzzan-mysql mysql --default-character-set=utf8mb4 \
  -udanzzan -pdanzzan1234 danzzan < scripts/seed_local_fall_festival.sql
```

- 앱: http://localhost:5173
- 관리자: http://localhost:5173/admin/login — 학번 `1234`, 비밀번호 `1234`
  (시드 스크립트가 아니라 수동 생성한 계정입니다. 없으면 아래 참고)

### 주의

- `docker compose up --build`는 **현재 체크아웃된 브랜치**를 빌드합니다.
  FE 코드를 고쳤으면 `fe` 서비스도 다시 빌드해야 반영됩니다.
- 이 앱은 PWA라 서비스워커가 남습니다. 화면이 갱신되지 않으면 시크릿 창을
  쓰거나 개발자도구 → Application → Service Workers에서 Unregister 하세요.

---

## 번역이 언제 채워지나

| 등록 방법 | `_en` 채워지는 시점 |
|---|---|
| 관리자 페이지 | **즉시** (저장하는 순간) |
| SQL로 DB 직접 | 최대 5분 (보정 스케줄러) |

즉시 채우고 싶으면 관리자 토큰으로 호출합니다.

```
POST /api/admin/translation/backfill   →   {"filled": 28}
```

### 번역이 이상할 때

관리자 폼 아래 **"영어 번역"** 접이식 칸을 펼치면 DeepL이 만든 영문이 있습니다.
고쳐 쓰면 그 값이 확정되고 자동 번역이 다시 덮어쓰지 않습니다.
비워두면 자동 번역이 유지됩니다.

### 용어집

`src/main/resources/glossary/ko-en.tsv`에 축제 용어를 넣어두었습니다.
기동할 때마다 DeepL에 동기화되므로, 용어를 추가하려면 **이 파일만 고치고
배포**하면 됩니다. 콘솔에서 따로 만질 필요 없습니다.

용어집이 없으면 "주점"이 `bar`(상업 술집)로 번역됩니다. 실측 예시:

```
공과대학 주점은 오후 6시부터 운영합니다.
  용어집 없음: The College of Engineering bar opens at 6:00 p.m.
  용어집 적용: The College of Engineering Student Pub opens at 6:00 p.m.
```

---

## 티켓팅 되살리기 (내년 봄)

코드를 지우지 않았습니다. 플래그만 바꾸면 됩니다.

**백엔드** — `application.yml`

```yaml
app:
  ticketing:
    api-enabled: true    # 또는 TICKETING_API_ENABLED=true
```

`false`면 `/tickets/**`와 `/user/**`가 차단됩니다.
**`/auth/**`(관리자 로그인)는 차단 대상이 아닙니다** — 함께 막히면 축제 당일
공지를 올릴 수 없으므로, 테스트로 명시 검증하고 있습니다.

**프론트엔드** — `DANZ-358`의 라우트 변경을 되돌리면 됩니다.
티켓팅 화면 코드는 그대로 남아 있습니다.

---

## 작업 중 발견한 기존 문제

우리 작업과 무관하게 원래 있던 것들입니다.

### origin/main의 테스트 컴파일 오류 (수정함)

`AdminBoothManagementBoothResponseJsonTest`가 생성자 인자 개수 불일치로
컴파일되지 않아 `./gradlew test`가 아예 실행되지 않았습니다.
`8810a05`에서 `operationDates` 필드가 추가됐는데 테스트를 갱신하지 않은 것입니다.
`DANZ-356`의 커밋 `cf26b4e`로 고쳤습니다. **main에도 반영이 필요합니다.**

### 긴급공지 저장 500 오류 (수정함)

긴급공지 행이 없는 상태에서 **조회만 해도** 500이 났습니다.
읽기 전용 트랜잭션 안에서 기본 행을 만들려 했기 때문입니다.
긴급공지를 한 번도 등록하지 않은 상태에서 관리자 페이지를 여는 것만으로
재현됩니다. 축제 당일 처음 쓰려는 순간 걸릴 버그였습니다.

### docker-compose.yml의 프로파일 기본값 (우회함)

`SPRING_PROFILES_ACTIVE=dev`가 기본인데, `application-dev.yml`은 MySQL·Redis·Kafka
주소를 `127.0.0.1`로 고정합니다. 호스트에서 `bootRun` 할 때는 맞지만
**컨테이너 안에서는 자기 자신을 가리켜 절대 연결되지 않습니다.**

`docker-compose.local.yml`에서 `prod`로 덮어써 우회했고, 원본은 건드리지
않았습니다. 원본도 정리하는 게 맞아 보입니다.

### FE `public/` 파일 권한 (수정함)

47개 정적 파일이 `0600`이라 도커 이미지로 복사되면 nginx가 읽지 못해
로고·파비콘·PWA 아이콘이 전부 403이었습니다.
FE `Dockerfile`에 `chmod -R a+rX`를 넣어 원본 권한과 무관하게 동작하도록 했습니다.

---

## 관리자 계정이 없을 때

로컬 DB는 비어 있으므로 직접 만들어야 합니다.

```bash
HASH=$(python3 -c "import bcrypt; print(bcrypt.hashpw(b'1234', bcrypt.gensalt(rounds=10)).decode())")
docker exec -i danzzan-mysql mysql --default-character-set=utf8mb4 -udanzzan -pdanzzan1234 danzzan <<SQL
INSERT INTO users (student_id, password, name, college, major, academic_status, role,
                   is_phone_verified, token_version, is_deleted, created_at)
VALUES ('1234', '$HASH', '테스트관리자', '공과대학', '소프트웨어학과', 'ENROLLED',
        'ROLE_ADMIN', 1, 0, 0, NOW());
SQL
```

---

## 남은 일

- **헤더 로고가 아직 봄 축제(落花流水)입니다.** 가을 로고로 교체 필요.
  안내 화면에만 LEGEND를 적용해 둔 상태입니다.
- `npm run lint`를 완주하지 못했습니다. 작업 당시 디스크 I/O 문제로 40분 넘게
  걸려도 끝나지 않았습니다. 머지 전에 한 번 돌려보는 것이 좋습니다.
- 테스트 픽스처에 DeepL 무료 키 형태의 더미 값(`279a2e9d-...:fx`)이 있습니다.
  실제 키가 아니지만 깃허브 시크릿 스캐너에 걸릴 수 있어 바꾸는 편이 안전합니다.
