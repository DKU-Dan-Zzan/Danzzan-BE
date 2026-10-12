# 졸업작품 Azure 이미지 저장소

동료 계정에서 재현할 때는 **[Azure 준비·이미지 연동 인수인계](azure-demo-handoff.md)**부터 읽는다. 포털 단계별 설정, 테넌트·구독 분기, IAM, 로컬 인증, 검증·정리, 미실행 VM 계획을 포함한다. 이 문서는 코드 계약과 실제 검증 기록을 유지한다.

## 확정한 범위

- 학생 구독의 `rg-danzzan-demo` / `danzzandemo2026` / 비공개 `images` 컨테이너를 사용한다.
- 기존 NHN 파일은 이관하지 않고 새 시연 이미지부터 Azure에 올린다. 기존 DB·파일은 삭제하지 않는다.
- 프론트의 multipart → BE 업로드 API와 JSON 이미지 필드 이름은 유지한다.
- DB에는 `azure:images/<blob-key>`를 저장한다. 응답 직렬화 시 기본 300초 읽기 SAS를 발급한다.
- 새 URL을 저장 요청에 다시 보내면 이미지 필드 역직렬화 단계에서 SAS를 제거한다. SAS·연결 문자열·계정 키를 DB나 Git에 보관하지 않는다.
- 논리적인 이미지 등록 해제와 실제 Blob 삭제는 별개다. 기존 등록 해제 API에 물리 삭제를 추가하지 않았다.

## 변경된 HTTP 계약

| 경로/동작 | 계약 |
| --- | --- |
| 기존 multipart 업로드 | 요청 그대로. 응답의 imageUrl/url은 짧은 읽기 SAS URL |
| 이미지 등록·수정 | 기존 imageUrl/images 등의 필드 그대로. 자기 저장소 URL만 canonical reference로 변환 |
| `POST /images/read-url` | `{ "imageUrl": "기존 또는 만료된 SAS URL" }` → `{ "imageUrl": "새 읽기 SAS URL" }`, `Cache-Control: no-store` |
| 기존 presign API | NHN/S3 설정에서는 기존 key/fileUrl/presignedUrl/expiresAt/method 계약을 유지한다. Azure 설정에서는 `400 Azure 이미지 업로드는 multipart API를 사용해 주세요`를 반환한다. |
| Azure 업로드 | 모든 실제 업로드는 기존 multipart → BE 경로를 사용한다. 브라우저의 Blob 직접 PUT/SAS 업로드는 제공하지 않는다. |

갱신 API는 DB의 공개 이미지 또는 기존 관리자 권한이 허용하는 임시 업로드만 처리한다. 임의의 비공개 Blob 경로를 익명으로 서명하지 않는다. Azure에서는 `/thumb/` 파일 생성 작업이 없으므로 썸네일도 원본을 사용한다.

## 인증 및 로컬 실행

Azure 계정 키 접근을 비활성화한 현재 설정을 유지한다. 로컬은 Azure CLI 로그인, 운영 VM은 관리 ID를 사용한다.

- 로컬 사용자: 현재 `images` 범위의 Storage Blob Data Contributor로 데이터 접근. 구독 Owner의 관리 작업 권한으로 user delegation key 요청도 가능하다.
- 향후 VM 관리 ID: `images` 범위 Storage Blob Data Contributor + 저장소 계정 범위 Storage Blob Delegator를 검토한다. 실제 권한 변경은 사용자 승인 후 진행한다.
- Azure CLI는 호스트에 설치하고 브라우저에서 본인이 로그인한다. 비밀키·비밀번호·토큰을 채팅에 붙여 넣지 않는다.
- 호스트 로그인은 기존 Docker BE에 자동 전달되지 않는다. MySQL·Redis·Kafka는 Docker에 두고 BE를 호스트에서 실행하는 검증 구성을 우선 사용한다.

로그인 이후 BE 환경 설정:

```sh
AZURE_STORAGE_CREDENTIAL=azure-cli
AZURE_STORAGE_ENDPOINT=https://danzzandemo2026.blob.core.windows.net
AZURE_STORAGE_CONTAINER=images
SPRING_PROFILES_ACTIVE=dev,azure
```

기존 로컬 DB·Redis·Kafka 연결값과 비밀값은 로컬 설정에서만 사용한다. 기존 BE와 충돌하지 않을 포트 및 FE API 주소를 실행 전에 확인한다. `local-storage` 프로필 대신 `azure`를 사용한다. VM에서는 `prod,azure` 및 `AZURE_STORAGE_CREDENTIAL=managed-identity`를 사용하되 전체 배포 설정은 이미지 검증 후 별도 준비한다.

## CORS

현재 사용하는 multipart 업로드는 브라우저 → BE 요청이므로 Blob PUT CORS가 필요하지 않다. 일반 `<img>`·CSS 이미지 표시는 읽기 SAS URL로 가능하다. 브라우저 fetch/canvas가 실제로 필요해질 때만 Blob 서비스 CORS를 최소 범위로 추가한다. CORS는 인증을 대신하지 않으며, 비공개 컨테이너와 짧은 SAS 정책을 유지한다.

## 실제 Azure smoke 테스트 실행

아래 명령은 사용자 승인과 호스트 Azure CLI 로그인이 끝난 뒤에만 실행한다. `AZURE_IMAGE_SMOKE`를 지정하지 않으면 일반 테스트에서 이 테스트는 건너뛴다.

```sh
AZURE_IMAGE_SMOKE=true ./gradlew test --tests 'com.danzzan.infra.azure.AzureBlobStorageSmokeTest' --rerun-tasks
```

대상은 코드에 고정된 `danzzandemo2026/images`이며 작은 PNG 한 개를 `smoke/<UUID>.png`에 만든다. 바이트 일치, 익명 접근 거부, SAS 만료와 새 SAS, 잘못된 MIME, 삭제 후 404를 확인한다. 실패 시에도 생성한 테스트 Blob만 정리한다. DB를 사용하는 앱 화면 E2E와는 별도의 SDK 검증이다.

인증/RBAC 근거: [사용자 위임 SAS(Java)](https://learn.microsoft.com/en-gb/azure/storage/blobs/storage-blob-user-delegation-sas-create-java), [위임 키 발급 권한](https://learn.microsoft.com/en-us/rest/api/storageservices/get-user-delegation-key).

## 검증 및 남은 단계

2026-10-12 사용자 승인 후 Azure CLI 2.91.0 설치 및 학교 계정 로그인을 완료하고, 실제 `AzureBlobStorageSmokeTest`를 실행했다. 결과는 1개 통과, 실패/건너뜀 0개다.

- 대상: `danzzandemo2026/images`, `smoke/b75a40ac-e353-41c2-9518-45cda3aa89c4.png` (70바이트 PNG).
- 실제 업로드와 읽기 SAS GET 200, 원본 바이트 일치, 잘못된 MIME 거부 확인.
- 익명 GET은 `409 PublicAccessNotPermitted`로 거부됨. 최초 테스트의 403/404만 허용하던 판정을 [Azure의 문서화된 409 응답](https://learn.microsoft.com/en-us/azure/storage/blobs/anonymous-read-access-prevent)에 맞게 수정한 뒤 동일한 테스트 경로로 재검증했다.
- 1초 SAS 발급 후 6초 대기 시 403, 새 읽기 SAS로 200 확인.
- 삭제 후 SAS GET 404 및 별도 Azure CLI `blob exists` 결과 false 확인.
- 계정은 Korea Central / StorageV2 / Standard_LRS, HTTPS 필수, 익명 접근 및 Shared Key 비활성 상태임을 CLI로 확인했다. 권한·설정·기존 파일·DB는 변경하지 않았다.

일반 단위 테스트는 SDK mock을 사용하며 이 실제 검증과 구분한다. 향후 smoke 실행은 다시 새 UUID 경로를 사용한다. Soft delete 3일 동안 삭제된 테스트 Blob의 저장 용량 요금이 남을 수 있다. 테스트 중 Azure SDK와 Spring Boot 관리 Netty 버전의 차이 경고가 출력됐지만 이번 실제 SDK 요청은 모두 통과했다.

2026-10-13에는 실제 로컬 MySQL·Redis와 Azure를 연결한 임시 검증 서버(`prod,azure`, loopback 8081) 및 Vite 프론트(5174)로 테스트 공지 흐름을 검증했다. 검증 서버의 예약 작업과 Kafka 리스너만 시작하지 않도록 임시 테스트 설정을 사용했으며, 기존 Docker 서비스·사용자 권한·비밀번호는 변경하지 않았다.

- 실제 관리자 multipart API: 무인증 업로드 거부, 잘못된 MIME 거부, PNG 업로드와 다운로드 바이트 일치 확인.
- 테스트 공지 ID `47` 한 개 생성·수정 후 공개 API 조회. 저장 요청에는 읽기 SAS를 보내고, DB의 썸네일/본문 이미지 필드에는 `azure:images/<key>`만 남는 것을 확인.
- 검증용 SAS 유효 기간을 10초로 줄여 만료 후 403, 갱신 API의 새 URL로 200 확인. 기본 300초 설정은 바꾸지 않았다.
- 실제 프론트 공지 검색·상세 화면에서 이미지 로드 확인(naturalWidth/Height 1). 페이지를 새로고침하지 않고 SAS 만료 시각이 `15:52:04Z`에서 `15:52:34Z`로 갱신되고 이미지가 유지되는 것을 확인.
- 공지 API DELETE 204 후, 이번 테스트 ID와 고유 제목이 일치하는 DB 행·첨부 항목만 트랜잭션으로 정리. 공지 및 공지 이미지 테이블의 전체 행 내용 해시가 검증 전후 일치했으며, 기존 공지 4개·이미지 항목 14개 유지, 테스트 공지 잔여 0개를 별도 SQL로 확인.
- 테스트 Blob `notices/images/20261013/cdfc9f32-cc85-4640-9f19-64c468a270c2_test.png` 삭제 후 GET 404 및 별도 Azure CLI exists=false 확인. 실패한 진단 실행에서 생성했던 테스트 Blob도 각 실행의 finally에서 삭제 후 404를 확인했다.
- 최종 실제 통합 테스트 1개 통과(실패/오류/건너뜀 0). 수정 후 전체 BE 테스트 482개 중 469개 통과, 13개 건너뜀, 실패/오류 0. 검증용 서버·프론트는 종료했다.

실제 연동에서 SDK가 Blob 이름의 `/`를 `%2F`로 인코딩하여 저장 요청이 400이 되는 결함을 발견했다. URL 경로를 한 번 디코딩한 뒤 기존 키 검증을 적용하도록 수정했고, 실제 SDK의 URL 생성으로 재현하는 회귀 테스트를 추가했다. `..`, 이중 인코딩, 빈 경로 세그먼트 차단은 유지한다.

검증 범위의 한계: 관리자 로그인 화면·파일 선택 UI 자체는 이번 실제 검증에서 조작하지 않았고, 관리자 업로드/등록/수정/삭제는 실제 HTTP API로 실행했다. 조회 화면은 실제 브라우저로 검증했다. 저장소 장애와 기타 예외 처리는 자동 테스트 범위이며 실제 Azure 장애를 유발하지 않았다. 기본 5분 SAS로 수 시간 머무는 검증과 모든 관리자 화면별 수동 확인은 별도다.

2026-10-13에는 로컬 컨테이너의 단일 시점 메모리 사용량(약 2.6GiB), Korea Central SKU 제한 및 공개 가격을 조회했다. B2ms는 현재 구독에서 제한되고 D2as_v5 등 후보에는 SKU 제한이 표시되지 않았지만, Compute 공급자가 미등록이고 쿼터 응답은 빈 목록이어서 최종 생성 가능 여부는 미확정이다. VM은 만들지 않았다. 사용자는 크레딧 만료일을 2027-10-12, 잔액을 `US$136,475/US$136,475`로 전달했으며 잔액의 소수점/천단위 표기는 확정하지 않았다.

새 DB 스키마·시연 데이터, VM 실제 부하, VM/디스크 영속성, 전체 Compose Azure 배포 및 VM 관리 ID 인증은 여전히 미검증이다. 세부 견적과 동료 계정 준비 순서는 [인수인계 문서](azure-demo-handoff.md)에 기록했다.
