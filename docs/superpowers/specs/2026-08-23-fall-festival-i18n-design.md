# 가을 축제 대응 설계 — 영어 번역 + 비활성 서비스 안내

작성일: 2026-08-23
대상 레포: `Danzzan-BE`, `Danzzan-FE`
기준 커밋: BE `177624c`, FE `e61a2bf6`

## 배경

봄 축제(DANFESTA 2026)가 끝나고 가을 축제를 준비한다. 두 가지 변화가 있다.

1. 외국인 방문객을 위한 영어 지원이 필요하다.
2. 가을 축제는 티켓팅을 하지 않는다. 티켓팅에 딸린 로그인·회원가입·내 정보도 함께 쓰이지 않는다.

## 목표

- 사용자가 한/영 토글로 앱 전체를 영어로 볼 수 있다. 정적 UI와 DB 콘텐츠 모두.
- 티켓팅·로그인·내 정보 진입 시 "고장"이 아니라 "가을 축제에는 필요 없다"로 읽히는 안내를 준다.
- 작업 완료 후 `docker compose up` 한 번으로 로컬에서 전체 확인이 가능하다.

## 비목표

- 영어 외 언어. 구조는 단순하게 가고, 중국어가 필요해지면 그때 다시 설계한다.
- 봄 축제 티켓팅 코드 삭제. 라우트만 안내 화면으로 교체하고 코드는 남긴다.
- URL 기반 언어 분기(`/en/notice`). 캠퍼스 현장용 PWA라 SEO·링크 공유 요구가 약하다.

---

## 1. 정적 UI 번역 (FE)

### 방식

경량 자체 구현. react-i18next를 쓰지 않는다.

근거: `scripts/check-bundle-budget.mjs`가 메인 index 청크에 **270,000바이트 예산**을 걸고 있다.
i18next + react-i18next는 압축 전 약 150KB로 이 예산을 위협한다. 반면 필요한 기능은
2개 언어 · 평문 치환 · 소수의 보간뿐이라 자체 구현으로 충분하다.

### 구조

```
src/i18n/
  index.ts          LanguageProvider, useT() 훅
  locales/ko.ts     { "nav.boothmap": "부스맵", ... }
  locales/en.ts     { "nav.boothmap": "Booth Map", ... }
```

- `useT()`가 `t("nav.boothmap")` 형태로 현재 언어 문자열을 반환한다.
- 언어 상태는 React Context + `localStorage("danzzan.lang")`.
- **첫 방문 시 `navigator.language`로 자동 판별한다.** 한국어가 아니면 영어로 시작한다.
  외국인이 토글을 찾지 못해도 영어를 보게 하는 것이 이 기능의 요점이다.
- `en.ts`에 키가 없으면 한국어로 폴백한다. 번역 누락이 빈 화면이 되어서는 안 된다.

### 번역 대상 화면

가을 축제에 실제로 쓰는 화면만 대상으로 한다. 관리자 화면은 제외한다.

| 위치 | 한글 라인 수(근사) |
|---|---|
| `src/routes/home` | 8 |
| `src/routes/notice` | 20 |
| `src/routes/timetable` | 7 |
| `src/routes/boothmap` | 47 |
| `src/routes/not-found` | 8 |
| `src/components/layout` | 57 |
| `src/components/app` | 108 |
| `src/components/common` | 4 |

합계 약 260줄. 사전 방식으로 감당 가능한 규모다.

### 토글 UI

헤더 우측에 `KO / EN` 토글을 둔다. 기존 헤더 원형 버튼 스타일
(`AppHeaderRoundButtonClass.ts`)을 재사용해 시각적 일관성을 지킨다.

---

## 2. DB 콘텐츠 번역 (BE)

### 방식

**쓰기 시점 번역 + `_en` 컬럼 저장.**

읽기 시점 번역을 택하지 않은 이유: 공지 10개 목록을 영어로 열면 DeepL 호출 10건을
기다려야 한다. 쓰기 시점이면 관리자가 등록하는 순간(사용자가 모르는 시점) 번역이 끝나 있어
EN 토글이 한국어와 같은 속도로 전환된다. 번역문이 DB 행으로 남아 관리자가 직접 고칠 수 있다는
이점도 있다.

### 흐름

```
관리자가 공지 등록
    ↓
NoticeService.create()
    ↓
TranslationService.translate(title, content) ──→ DeepL API (glossary 적용)
    ↓                                                   │ 실패 시
notice 저장 (title, content, title_en, content_en) ←────┘ _en = null 로 저장
                                                         등록 자체는 성공
    ↓
사용자: GET /notices?lang=en
    → title_en 있으면 영문, null이면 한국어 폴백
```

### 장애 대응 (중요)

**DeepL이 죽어도 공지 등록은 성공해야 한다.** 축제 당일 긴급 공지가 외부 API 장애로
올라가지 않으면 그것이 사고다. 번역 실패 시 `_en`을 null로 두고 저장하며,
스케줄러가 주기적으로 **`_en IS NULL`인 필드만** 채운다.

이 "null만 채운다" 규칙이 중요하다. 스케줄러가 이미 값이 있는 필드를 건드리지 않으므로,
자동 번역이든 사람이 쓴 것이든 **한 번 채워진 영문은 스케줄러가 덮어쓰지 않는다.**

### 컴포넌트 경계

- `TranslationClient` (인터페이스) ← `DeepLTranslationClient` (구현)
  - 엔진 교체와 테스트용 가짜 주입을 위해 분리한다.
- `TranslationService`
  - 용어집 적용, 빈 문자열 스킵, 실패 처리, 재시도를 담당한다.
- 각 도메인 서비스는 `TranslationService`만 안다. DeepL을 모른다.

### 번역 대상 필드

| 엔티티 | 필드 |
|---|---|
| `Notice` | title, content |
| `Booth` | name, description |
| `Pub` | name, intro, description, department |
| `College` | name |
| `Artist` | name, description |
| `Performance` | stage |

6개 엔티티, 12개 필드.

### 스키마

각 테이블에 `_en` 컬럼을 추가한다. 범용 translation 테이블을 쓰지 않는 이유는
쿼리·엔티티·관리자 폼이 모두 직관적이고 JOIN이 없어 읽기가 빠르기 때문이다.
언어를 늘릴 계획이 없으므로 범용 구조의 유연성은 값을 하지 못한다.

추가로 엔티티마다 `en_is_manual BOOLEAN DEFAULT false` 플래그를 하나 둔다.
필드별이 아니라 **엔티티당 한 개**다.

이 플래그가 필요한 시점은 스케줄러가 아니라 **한국어 원문이 수정될 때**다.
관리자가 공지 제목을 고치면 기존 영문은 낡은 번역이 되므로 재번역해야 한다.
그런데 그 영문을 사람이 직접 쓴 것이라면 기계번역으로 덮어써서는 안 된다.

동작 규칙:

| 상황 | 처리 |
|---|---|
| `_en`이 null | 스케줄러가 번역해 채운다 |
| 한국어 수정 + `en_is_manual = false` | 자동 재번역해 덮어쓴다 |
| 한국어 수정 + `en_is_manual = true` | 덮어쓰지 않고 관리자 목록에 "검토 필요"로 표시한다 |

**보호는 엔티티가 아니라 필드 단위다.** 플래그가 켜져 있어도 비어 있는 필드는
기계번역이 채운다. 지켜야 할 것은 사람이 실제로 쓴 값이지, 사람이 손댄 행 전체가 아니다.

엔티티 단위로 막으면 이런 일이 생긴다. 관리자가 영문 제목만 채우고 본문을 비워두면
`content_en`이 null인 채로 플래그가 켜지고, 스케줄러가 그 행을 통째로 건너뛰어
**영문 본문이 영구히 비어 있게 된다.** 복구 경로는 사람이 다시 손으로 넣는 것뿐이다.

필드별 플래그(12개)를 두지 않는 이유는, 스케줄러가 null만 채우므로 필드 단위 구분이
실익을 내는 경우가 위 세 번째 행 하나뿐이기 때문이다. 그 경우에도 관리자가 해당 행을
열어 확인하므로 엔티티 단위 표시로 충분하다.

마이그레이션 SQL은 `scripts/`에 둔다. 기존 데이터는 일회성 배치로 채운다.

### 용어집

DeepL glossary API가 KO→EN을 지원함을 확인했다. 일반 기계번역은 캠퍼스 축제 용어를 틀린다.

실측 결과:

```
공과대학 주점은 오후 6시부터 운영합니다.
  용어집 없음: The College of Engineering bar opens at 6:00 p.m.
  용어집 적용: The College of Engineering Student Pub opens at 6:00 p.m.

타임테이블에서 공연 시간을 확인하세요.
  용어집 없음: Please check the performance times on the schedule.
  용어집 적용: Please check the performance times on the Timetable.
```

"주점"이 상업 술집(bar)으로 번역되면 외국인에게 잘못된 정보가 간다.

초기 항목:

| 한국어 | 영어 |
|---|---|
| 주점 | Student Pub |
| 타임테이블 | Timetable |
| 단과대 | College |
| 총학생회 | Student Council |
| 부스맵 | Booth Map |
| 뒷풀이 | After-party |

**용어집 ID를 하드코딩하지 않는다.** TSV 파일을 레포에 두고 기동 시 동기화한다.
하드코딩하면 용어 추가마다 수동 작업이 필요하고 팀원이 재현할 수 없다.

### API 규약

FE·BE 병렬 작업의 유일한 접점이므로 먼저 확정한다.

**사용자 조회 API**

- 요청: 기존 조회 엔드포인트에 `?lang=en` 쿼리 파라미터 추가. 생략 시 한국어.
- 응답: **필드명은 그대로 두고 값만 바꾼다.** `title_en`을 노출하지 않는다.
  FE가 언어에 따라 다른 필드를 읽는 분기를 갖지 않게 하기 위함이다.

**관리자 API는 반대로 간다.**

관리자 폼은 한국어와 영어를 나란히 보여줘야 하므로 `title`과 `title_en`을 **둘 다 노출한다.**
사용자 API의 "필드명 고정" 규칙은 관리자 API에 적용하지 않는다. 두 API의 목적이 다르다.
사용자는 한 언어만 보고, 관리자는 두 언어를 동시에 다룬다.

### 설정

`application.yml` (공개 레포)에는 참조만 둔다:

```yaml
deepl:
  api-key: ${DEEPL_API_KEY:}
  api-url: ${DEEPL_API_URL:https://api-free.deepl.com/v2}
```

키 실제 값의 위치:

| 실행 방식 | 위치 |
|---|---|
| 로컬 `bootRun` | `src/main/resources/application-local.yml` (gitignore됨) |
| 로컬 docker | `Danzzan-BE/.env` → compose가 주입 |
| 운영 서버 | 서버 `.env` → `docker-compose.prod.yml`이 `env_file`로 전달 |

무료 플랜 키는 `:fx`로 끝나며 `api-free.deepl.com`을 쓴다. 유료 키는 접미사가 없고
`api.deepl.com`을 쓴다. **키 접미사를 보고 URL을 자동 판별하도록 구현한다.**
팀이 유료로 전환해도 깨지지 않게 하기 위함이다.

### 관리자 영문 입력란

공지·부스·주점·아티스트 등록/수정 폼에 영문 입력란을 접이식으로 단다.

- 비워두면 자동 번역
- 채우면 사람이 쓴 값이 우선하고 `_en_is_manual = true`

---

## 3. 비활성 서비스 안내 (FE)

### 컴포넌트

`src/routes/common/ServiceClosedNotice.tsx`

기존 404 페이지(`NotFoundPage.tsx`)가 이미 톤을 잡아뒀다. 브랜드 그라디언트 CTA,
워터마크 로고, 중앙 정렬 카드. 같은 문법을 따르고 문구만 주입받는다.

```
props: { titleKey, descriptionKey, actionKey, actionTo }
```

### 문구 방향

화면마다 다르게 간다. "이용할 수 없습니다"만 반복하면 사용자는 고장으로 인식한다.
**왜 없는지와 대신 무엇을 할 수 있는지**를 함께 준다.

| 화면 | 방향 | 액션 |
|---|---|---|
| 티켓팅 | 가을 축제는 티켓팅 없이 자유 입장 | 타임테이블 보기 |
| 로그인/회원가입 | 로그인 없이 모든 기능 이용 가능 | 홈으로 |
| 내 정보 | 가을 축제 기간에는 미제공 | 홈으로 |

i18n 키를 쓰므로 영어로도 나온다.

### 적용 범위

| 경로 | 처리 |
|---|---|
| `/ticket`, `/ticket/login`, `/ticket/signup`, `/ticket/reset-password` | 안내 화면 |
| `/ticket/ticketing`, `/ticket/my-ticket`, `/ticket/myticket` | 안내 화면 |
| `/login`, `/signup`, `/reset-password`, `/ticketing`, `/myticket` | 안내 화면 |
| `/mypage` | 안내 화면 |
| `/ticket/admin/**` (팔찌 운영) | 안내 화면 |
| **`/admin/**`** | **손대지 않는다** |

### 함정

`/admin`과 `/ticket/admin`은 이름이 비슷하지만 **완전히 별개 트리다.**

- `/admin/**` — 부스·공지·타임테이블 관리. `useAdminAuth` 사용. 가을 축제에 계속 쓴다.
- `/ticket/admin/**` — 팔찌 운영. 티켓팅에 딸린 화면. 막는다.

여기서 실수하면 축제 당일 공지를 올리지 못한다. 두 경로를 구분해 검증하는 라우팅 테스트를 넣는다.

또한 로그인 화면을 막아도 **BE 인증 엔드포인트는 건드리지 않는다.**
`/admin/login`이 이를 사용하므로 BE를 막으면 관리자 로그인이 깨진다. 3번 작업은 FE 전용이다.

### 바텀네비

티켓팅 탭의 `ticketingTarget`이 현재 로그인 여부로 분기한다. 고정 `/ticket`으로 단순화한다.
헤더의 티켓·사람 버튼도 각각 안내 화면으로 연결한다.

탭은 5개를 유지한다. 봄 축제 사용자에게 변화를 알리는 값이 있다고 판단했다.

---

## 4. 도커 구성

작업 완료 후 `docker compose up` 한 번으로 전체가 뜨게 한다.

```
danzzan-fe    :5173  nginx (vite build 결과물, /api → BE 프록시)
danzzan-app   :8080  Spring Boot
danzzan-mysql :3306
danzzan-redis :6379
danzzan-kafka :9094
```

- FE에 `Dockerfile` 신규 작성 (node 빌드 → nginx 서빙) + `nginx.conf`에 `/api` 프록시
- 루트에 `docker-compose.local.yml`을 두고 FE·BE를 묶는다. BE 레포의 기존 compose는 유지한다.
- **Kafka는 유지한다.** 티켓팅을 FE에서 막아도 BE의 Kafka 컨슈머는 그대로 기동하므로,
  빼면 앱이 뜨지 않는다.

---

## 5. 작업 순서

1. **BE 번역 기반** — `TranslationClient`/`TranslationService`, DeepL 연동, 용어집 동기화
2. **BE 스키마 + API** — `_en` 컬럼 마이그레이션, 도메인 서비스 연결, `?lang=en` 응답, 기존 데이터 배치
3. **BE 관리자 영문란** — 수동 입력 우선 처리, `_en_is_manual` 플래그
4. **FE i18n 골격** — Provider·훅·사전·토글. 홈 화면 하나만 먼저 뚫어 동작 확인
5. **FE 화면별 문자열 치환** — 홈 → 공지 → 타임테이블 → 부스맵 → 레이아웃
6. **FE 비활성 안내 화면** — 1~5와 독립. 언제든 삽입 가능
7. **도커 통합 + 로컬 확인**

1~3은 BE, 4~6은 FE라 두 사람이 병렬로 진행할 수 있다.
접점은 `?lang=en` 응답 형태뿐이며 위 API 규약에서 확정했다.

---

## 6. 테스트

- `ko.ts`와 `en.ts`의 키 집합이 일치하는지
- `?lang=en`일 때 `_en`이 null이면 한국어로 폴백하는지
- **DeepL 실패를 가짜 클라이언트로 주입했을 때 공지 등록이 성공하는지** (가장 중요)
- `/admin/**`이 안내 화면에 걸리지 않는지
- 스케줄러가 `_en`이 null인 필드만 채우고 기존 값은 건드리지 않는지
- 한국어 원문 수정 시 `en_is_manual = true`인 영문이 덮어씌워지지 않는지
- 관리자 API가 `title`과 `title_en`을 둘 다 반환하는지
- 첫 방문 시 `navigator.language`에 따라 초기 언어가 정해지는지

---

## 확인된 사실

- DeepL glossary는 KO→EN을 지원한다 (Thai만 제외)
- 계정 월 한도는 1,000,000자다
- 용어집 적용 전후 차이를 실측으로 확인했다 (2번 항목 참조)
- `/admin/login`은 `/ticket/login`과 별개이며 `useAdminAuth`를 쓴다
- FE 번들 예산은 메인 index 청크 270,000바이트다
