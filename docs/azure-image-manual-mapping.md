# Azure 이미지 수동 업로드 · DB 경로 교체 안내

작성일: 2026-10-14. 현재 BE의 업로드 코드·엔티티·[배포 스키마](../deploy/azure/schema.sql)를 대조한 안내다. 이 문서를 작성하면서 DB나 Blob을 변경하지 않았다. 실행 중인 DB의 전체 스키마를 새로 조회한 결과는 아니므로, 실제 수정 전 아래 조회로 대상 DB·행·컬럼 길이를 확인한다.

## 1. 어디에 올리고 무엇을 DB에 넣는가

| 구분 | 대상 |
| --- | --- |
| 이미지 파일 저장소 | Azure Storage 계정 `danzzandemo2026` → 비공개 컨테이너 `images` |
| 앱 데이터 저장소 | Azure VM 안의 MySQL. [DB 이관 기록](azure-db-migration.md) 기준 앱 DB는 `danzzan_fall_demo` |
| 보존할 DB | 이전 Azure `danzzan_demo`, 로컬 `festival_test` |
| DB에 넣는 값 | `azure:images/<컨테이너 안의 전체 Blob 이름>` |
| 브라우저에서 열람하는 값 | 읽기 권한과 만료 시각이 포함된 HTTPS SAS URL. DB에 저장하지 않는다. |

Blob Storage는 이미지 파일 저장소이고, MySQL은 그 파일을 가리키는 경로를 저장한다. **파일 업로드만으로는 서비스 화면에 등록되지 않는다.** 아래 표에 맞는 기존 DB 행의 이미지 컬럼도 교체해야 한다.

예를 들어 `images` 안에 다음 파일을 올렸다면:

```text
Blob 이름: notices/images/20261014/8b1ca2f0-67a5-4dda-902b-73b879260a11_notice.jpg
DB 저장값: azure:images/notices/images/20261014/8b1ca2f0-67a5-4dda-902b-73b879260a11_notice.jpg
```

`images/images/...`처럼 컨테이너 이름을 중복하지 않는다. 포털의 전체 주소, 로컬 파일 경로, `?sv=...&sig=...`가 붙은 SAS는 붙여넣지 않는다. `azure:` 식별자는 브라우저 주소창에서 직접 여는 URL이 아니다. 앱이 응답할 때 기본 300초 읽기 SAS로 변환한다.

## 2. 이미지 종류별 폴더 · 테이블 · 컬럼

표의 폴더는 **`images` 컨테이너 내부 경로**다. 공통 파일명 `F`는 아래 형식으로 바꾼다.

```text
F = YYYYMMDD/새UUID_짧은영문파일명.jpg
예: 20261014/8b1ca2f0-67a5-4dda-902b-73b879260a11_notice.jpg
```

아래의 `<festivalId>`, `<artistId>`, `<boothId>`, `<imageId>`, `<단과대>`, `<학과>`, `F`는 설명용 자리표시자다. 그대로 DB에 저장하지 않는다. 축제 설정은 현재 코드에서 ID `1`을 사용하지만, 수정할 행을 먼저 확인한다.

### 앱 업로드 경로가 있는 이미지

| 이미지 종류 | 업로드할 폴더 | 바꿀 DB 테이블·컬럼 | DB 값 형식 | 대상 행·함께 확인할 항목 |
| --- | --- | --- | --- | --- |
| 공식 포스터 / AI 색상 분석 원본 | `festival/<festivalId>/poster/` | `festival_setting.poster_image_url` | `azure:images/festival/<festivalId>/poster/F` | `festival_setting.id`. 홈 배너와 별도다. |
| 상단 축제 로고 | `festival/<festivalId>/logo/` | `festival_setting.logo_image_url` | `azure:images/festival/<festivalId>/logo/F` | `festival_setting.id` |
| 홈 첫 화면 메인 이미지 · 슬라이드 | `festival/<festivalId>/home/` | `home_image.image_url` | `azure:images/festival/<festivalId>/home/F` | `id`와 `festival_setting_id`로 대상 확인. `display_order`가 작은 순으로 표시한다. |
| 티켓팅 OFF 입장 안내 배경 | `festival/ticketing-background/` | `festival_setting.ticketing_background_image_url` | `azure:images/festival/ticketing-background/F` | `festival_setting.id`. OFF 안내와 내 정보 화면에서 사용하는 배경이다. |
| 발급된 티켓 카드 배경 | `festival/ticketing-background/` | `festival_setting.ticket_card_background_image_url` | `azure:images/festival/ticketing-background/F` | 위 항목과 폴더는 같지만 DB 컬럼이 다르다. |
| 공지 목록 썸네일 | `notices/images/` | `notice.thumbnail_image_url` | `azure:images/notices/images/F` | `notice.id`. 해당 이미지를 본문에서도 사용한다면 아래 행도 확인한다. |
| 공지 상세 첨부 이미지 | `notices/images/` | `notice_image.image_url` | `azure:images/notices/images/F` | **`notice_id` + `position`**으로 식별한다. 별도 `id` 컬럼은 없다. |
| 광고 배너 | `ads/` | `advertisement.image_url` | `azure:images/ads/F` | `id`, `placement` 확인. `link_url`은 클릭 시 이동할 주소이므로 이미지 경로를 넣지 않는다. |
| 주점 대표·상세 이미지 | `pub-images/<단과대>/<학과>/` | `pub_image.image_url` | `azure:images/pub-images/<단과대>/<학과>/F` | `id`, `pub_id` 확인. `is_main = 1`인 이미지가 목록 대표 이미지다. |
| 아티스트 사진 / 공연 목록 사진 | `artists/<artistId>/` | `artist.image_url` | `azure:images/artists/<artistId>/F` | `artist.id`. 공연은 `performance.artist_id`로 이 사진을 참조한다. |

주점 폴더는 앱에서 `college.name`과 `pub.department`로 만든다. 영문·숫자·한글·`.`·`_`·`-` 이외의 문자는 `_`로 바뀌며 빈 값은 `unknown`이다. 실제 파일명은 한글도 `_`로 바뀐다. 수동 업로드는 짧은 영문 파일명을 사용하면 경로를 확인하기 쉽다. 주점이 어느 학과인지 폴더만 보고 판단하지 말고 `pub_id`도 확인한다.

### 전용 업로드 메서드가 없는 이미지: 수동 관리용 권장 폴더

아래 폴더명은 **이 문서의 운영 규칙 제안**이며 앱이 자동 생성하는 경로가 아니다. 기존 DB 이미지 컬럼과 공개 조회 API는 있으므로 수동 교체 대상으로 사용할 수 있다.

| 이미지 종류 | 권장 폴더 | DB 테이블·컬럼 | DB 값 형식 | 대상 확인 |
| --- | --- | --- | --- | --- |
| 푸드트럭·체험부스·이벤트·쉼터·편의시설 사진 | `manual/booths/<boothId>/` | `booth.image_url` | `azure:images/manual/booths/<boothId>/F` | `booth.id`, `name`, `type` |
| 홈 하단 라인업 이미지 | `manual/lineup/<imageId>/` | `lineup_image.image_url` | `azure:images/manual/lineup/<imageId>/F` | `id`, `display_order` |
| 타임테이블 콘텐츠 미리보기 | `manual/content/<imageId>/preview/` | `content_image.preview_image_url` | `azure:images/manual/content/<imageId>/preview/F` | `id`, `name`, `display_order` |
| 타임테이블 콘텐츠 상세 이미지 | `manual/content/<imageId>/detail/` | `content_image.detail_image_url` | `azure:images/manual/content/<imageId>/detail/F` | 미리보기와 **같은 `content_image.id`**의 다른 컬럼이다. |

`manual/` 아래의 미등록 파일은 앱의 관리자 임시 업로드 SAS 갱신 허용 경로에 포함되지 않는다. DB에 등록되기 전 미리 보려면 포털에서 해당 Blob의 짧은 읽기 SAS를 발급해 사용한다. 등록 후에도 공개 API에서 노출 가능한 이미지라는 조건을 충족해야 익명 갱신이 가능하다.

### 직접 교체 대상으로 오해하기 쉬운 항목

- `theme.source_poster_url`: **테마 생성 당시 포스터의 출처 기록**이다. 현재 공식 포스터를 바꾸려면 `festival_setting.poster_image_url`을 바꾼다. 과거 테마의 출처를 새 이미지로 일괄 교체하지 않는다.
- `theme.theme_data`: 색상 데이터다. 홈 배너나 티켓 배경 파일 경로를 넣는 컬럼이 아니다.
- `pub`: 주점 자체 정보다. 이미지는 `pub_image`에 있다.
- `performance`: 공연 일정이다. 사진은 연결된 `artist.image_url`에 있다.
- `festival_map_setting`: 현재 코드/배포 스키마에는 이미지 컬럼이 없다.
- `lost_item`, `naverpay_*`, `user_tickets_backup` 등 이관 보관 테이블은 이 문서의 화면 이미지 교체 대상이 아니다. 보관 테이블의 경로까지 일괄 치환하지 않는다.
- 주점·부스의 Azure 썸네일은 원본 이미지를 사용한다. 별도 `thumb/` 파일을 만들거나 썸네일 컬럼을 추가할 필요가 없다.

## 3. 폴더·파일명 검사가 있는 항목

SQL로 직접 값을 넣으면 앱의 저장 시 검증을 우회한다. 지금 이미지가 보이더라도 다음 관리자 저장이나 AI 분석이 실패할 수 있으므로 아래 규칙을 따른다.

| 항목 | 코드에서 확인하는 조건 |
| --- | --- |
| 공식 포스터 AI 읽기 | `festival/<해당 축제 ID>/poster/` 아래여야 한다. 빈 파일 불가, 최대 5 MiB. 포스터 읽기 자체는 날짜/UUID 접미사 검사를 하지 않지만 표의 앱 파일명 형식을 권장한다. |
| 홈 이미지 첫 장 저장 | `festival/<해당 축제 ID>/home/YYYYMMDD/UUID_파일명` 형식, Blob 존재, 0 초과 5 MiB 이하, 이미지 Content-Type |
| 티켓 배경 검증 | `festival/ticketing-background/YYYYMMDD/UUID_파일명` 형식, Blob 존재, 0 초과 10 MiB 이하, 이미지 Content-Type. 두 배경 컬럼에 공통 적용되는 검증이다. |

홈·티켓 배경 접미사의 실제 검사식은 `[0-9]{8}/[a-fA-F0-9-]{36}_[A-Za-z0-9._-]+`다. 수동 업로드도 새 UUID와 날짜 폴더를 사용하고, 같은 이름의 기존 Blob을 덮어쓰지 않는다.

포스터·로고·홈의 관리자 업로드는 실제 JPEG/PNG, 최대 5 MiB·4천만 화소를 검사한다. 티켓 배경 업로드는 JPEG/PNG, 최대 10 MiB·4천만 화소다. 일반 공지·광고·주점·아티스트 업로드는 최대 5 MiB이고 Azure 저장 계층은 JPEG/PNG/WebP MIME을 허용한다. 수동 작업도 호환성이 높은 JPEG/PNG를 사용하고 실제 파일과 맞는 `Content-Type`(`image/jpeg` 또는 `image/png`)을 설정한다.

포스터·로고·홈·티켓 배경은 가능하면 관리자 화면에서 교체한다. 특히 홈 이미지 저장은 첫 장의 기존 ID/URL과 생성 원본 포스터가 바뀌지 않았는지도 확인한다. 직접 SQL로 바꾼 뒤 열린 관리자 화면에서 이전 초안을 저장하면 충돌할 수 있으므로 새로 조회한 다음 작업한다.

## 4. 실제 작업 순서

1. IntelliJ의 연결 대상과 선택된 DB가 Azure **`danzzan_fall_demo`**인지 확인한다. 로컬 `festival_test`와 이전 `danzzan_demo`는 선택하지 않는다.
2. 대상 행을 SELECT하고, 행 식별자와 기존 이미지 경로를 비공개 작업 메모에 남긴다. DB 전체 덤프를 새로 만드는 대신 이번에 바꿀 값만 기록해도 해당 경로를 되돌릴 수 있다. 기존 이미지 파일은 보존한다.
3. 표의 폴더에 새 이름으로 업로드한다. 포털에 표시되는 Blob 이름과 이미지 MIME을 확인한다. 기존 Blob을 덮어쓰거나 삭제하지 않는다.
4. 해당 Blob의 짧은 읽기 SAS로 브라우저에서 이미지가 열리는지 확인한다. SAS는 열람용이며 문서·Git·DB에 넣지 않는다.
5. `azure:images/` 뒤에 **컨테이너 내부 전체 Blob 이름**을 붙인다. 대소문자와 확장자까지 일치시킨다. URL의 `%2F`를 복사하기보다 포털의 Blob 이름을 사용한다.
6. 아래처럼 대상 행과 이전 값까지 조건으로 한 이미지 컬럼만 바꾼다. 공지 썸네일·본문처럼 같은 그림을 사용하는 두 위치는 필요한 범위를 함께 확인한다.
7. 앱 화면을 새로고침해 목록·상세·이미지 확대를 확인한다. 직접 SQL은 브라우저의 기존 조회 캐시를 갱신하지 않는다. 홈이면 첫 장과 나머지 슬라이드 순서도 확인한다.

새 행 추가는 경로 교체와 다르다. `notice_image.position`, `home_image.festival_setting_id/display_order`, `pub_image.pub_id/is_main`, 필수 생성 시각 등을 함께 맞춰야 한다. 기존 게시물 이미지를 바꾸는 작업에서는 행을 삭제·재생성하지 말고 URL 컬럼만 수정한다.

## 5. 수정 전 조회 SQL

아래는 읽기 전용 조회다. 결과에는 기존 운영 이미지 경로가 포함될 수 있으므로 외부 PR에 그대로 첨부하지 않는다.

```sql
SELECT DATABASE(); -- 기대값: danzzan_fall_demo

SELECT id, festival_name, poster_image_url, logo_image_url,
       ticketing_background_image_url, ticket_card_background_image_url
FROM danzzan_fall_demo.festival_setting;

SELECT id, festival_setting_id, display_order, image_url
FROM danzzan_fall_demo.home_image
ORDER BY festival_setting_id, display_order, id;

SELECT id, title, thumbnail_image_url FROM danzzan_fall_demo.notice;
SELECT notice_id, position, image_url FROM danzzan_fall_demo.notice_image
ORDER BY notice_id, position;

SELECT id, pub_id, is_main, image_url FROM danzzan_fall_demo.pub_image
ORDER BY pub_id, id;
SELECT id, name, type, image_url FROM danzzan_fall_demo.booth;
SELECT id, name, image_url FROM danzzan_fall_demo.artist;
SELECT id, title, placement, image_url, link_url FROM danzzan_fall_demo.advertisement;
SELECT id, display_order, image_url FROM danzzan_fall_demo.lineup_image
ORDER BY display_order, id;
SELECT id, name, display_order, preview_image_url, detail_image_url
FROM danzzan_fall_demo.content_image ORDER BY display_order, id;

-- 실제 DB의 컬럼 길이 확인: 255자인 컬럼에는 긴 경로를 넣을 수 없다.
SELECT TABLE_NAME, COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'danzzan_fall_demo'
  AND COLUMN_NAME IN ('image_url', 'thumbnail_image_url', 'preview_image_url',
                      'detail_image_url', 'poster_image_url', 'logo_image_url',
                      'ticketing_background_image_url', 'ticket_card_background_image_url',
                      'source_poster_url')
ORDER BY TABLE_NAME, COLUMN_NAME;
```

배포 스키마 기준 `home_image`, `lineup_image`, `booth`, `artist`, `pub_image`의 `image_url`과 `content_image`의 두 URL은 255자다. 축제 설정·공지·광고·테마 출처 URL은 2048자다. `azure:images/`까지 포함한 전체 값의 길이로 판단한다. 긴 파일명을 짧게 바꾸고 그 이름으로 업로드하면 된다.

## 6. 한 행 교체 SQL 예시

**실행용 완성 SQL이 아닌 템플릿**이다. `NULL` 자리 세 개를 조회한 대상 ID·기존 경로·새 경로로 채운다. IntelliJ의 같은 쿼리 콘솔/연결에서 순서대로 실행한다. 먼저 SELECT 결과를 확인하고 UPDATE 부분만 실행한다.

```sql
SET @target_image_id = NULL; -- 교체할 home_image.id
SET @old_ref = NULL;         -- SELECT로 확인한 기존 image_url 문자열
SET @new_ref = NULL;         -- 실제 업로드 완료한 azure:images/... 문자열

START TRANSACTION;
SELECT id, festival_setting_id, display_order, image_url
FROM danzzan_fall_demo.home_image
WHERE id = @target_image_id AND festival_setting_id = 1 FOR UPDATE;

UPDATE danzzan_fall_demo.home_image
SET image_url = @new_ref
WHERE id = @target_image_id
  AND festival_setting_id = 1
  AND image_url = @old_ref
  AND @new_ref LIKE 'azure:images/festival/1/home/%';
SELECT ROW_COUNT() AS changed_rows; -- 바로 실행. 기대값 1, 아니면 ROLLBACK

SELECT id, festival_setting_id, display_order, image_url
FROM danzzan_fall_demo.home_image WHERE id = @target_image_id;

ROLLBACK; -- 기본 예시는 되돌림. 값과 변경 행 수를 검토한 실제 작업에서만 COMMIT 선택.
```

이 예시는 현재 축제 ID `1`의 홈 이미지용이다. `LIKE` 조건만으로 파일 존재·크기·MIME·전체 파일명 규칙이 검증되는 것은 아니다. 트랜잭션을 열린 채 두지 말고 확인 직후 `COMMIT` 또는 `ROLLBACK`으로 끝낸다. 브라우저 확인은 커밋 이후에 수행한다.

다른 이미지도 **해당 표의 테이블·컬럼과 정확한 키**를 사용한다. 예를 들어 공지 첨부는 다음 조건이 필요하다.

```sql
-- notice_image에는 id가 없으므로 두 컬럼으로 한 장을 지정한다.
-- @notice_id, @position, @old_ref, @new_ref를 먼저 설정하고 같은 트랜잭션에서 실행한다.
UPDATE danzzan_fall_demo.notice_image
SET image_url = @new_ref
WHERE notice_id = @notice_id AND position = @position AND image_url = @old_ref;
SELECT ROW_COUNT();

-- 같은 그림이 그 공지의 썸네일일 때만 함께 교체한다. 일치하지 않으면 0행이 정상이다.
UPDATE danzzan_fall_demo.notice
SET thumbnail_image_url = @new_ref, updated_at = NOW(6)
WHERE id = @notice_id AND thumbnail_image_url = @old_ref;
SELECT ROW_COUNT();
```

이미지 경로 교체 때문에 Kafka 토픽, 발권 내역, 티켓팅 일정, Redis를 삭제하거나 초기화할 필요는 없다.

## 7. 화면에 안 보일 때

| 증상 | 확인할 항목 |
| --- | --- |
| 포털에 파일은 있지만 화면에 없음 | DB 행에 등록했는지, 현재 앱 DB와 대상 ID가 맞는지 확인 |
| SAS 없는 HTTPS URL이 열리지 않음 | 컨테이너가 비공개이므로 읽기 SAS로 확인. 공개 접근 설정을 켜지 않는다. |
| SAS가 만료되어 403 | 새 읽기 URL로 열기. DB에는 만료되지 않는 `azure:images/...` 경로 유지 |
| 404 / 깨진 이미지 | 파일명·대소문자·날짜 폴더·확장자, `images/` 중복 여부 확인 |
| 포스터 표시만 되고 AI 분석 실패 | 축제 ID에 맞는 `festival/<id>/poster/` 경로와 실제 JPEG/PNG 파일인지 확인 |
| 홈·티켓 배경 저장 시 업로드 이미지 선택 오류 | 전용 경로와 날짜/UUID 파일명, 존재 여부·크기·MIME 검사 |
| 주점 상세는 바뀌었는데 목록 사진은 그대로 | 교체한 `pub_image`가 `is_main = 1`인지 확인 |
| 공지 상세만 바뀜 | `notice.thumbnail_image_url`도 기존 이미지를 가리키는지 확인 |
| 주점·공연 이미지 갱신 API가 거부됨 | 주점 운영 정보, 공연에 연결된 아티스트 등 공개 조회 조건 확인. 업로드 자체가 공개 등록을 의미하지 않는다. |

## 코드 근거와 관련 문서

- [S3Uploader: Azure에서도 사용하는 종류별 업로드 경로·파일명 생성](../src/main/java/com/danzzan/infra/s3/S3Uploader.java)
- [AzureBlobStorage: 참조 정규화·SAS·경로와 Blob 검증](../src/main/java/com/danzzan/infra/azure/AzureBlobStorage.java)
- [PublicImageReferenceLookup: 공개 이미지 참조 조회](../src/main/java/com/danzzan/infra/image/PublicImageReferenceLookup.java), [ImageReadUrlService: 임시 이미지 갱신 권한](../src/main/java/com/danzzan/infra/image/ImageReadUrlService.java)
- [Notice: 공지 첨부 관계와 순서](../src/main/java/com/danzzan/domain/notice/entity/Notice.java), [PubImage: 주점 대표 이미지](../src/main/java/com/danzzan/domain/boothmap/model/entity/PubImage.java)
- [HomeImageWriter: 첫 이미지 교체와 순서 보존](../src/main/java/com/danzzan/domain/home/service/HomeImageWriter.java)
- [Azure 이미지 저장소 계약](azure-image-storage.md), [DB 이관 기록](azure-db-migration.md), [홈 이미지 별도 저장](home-image-save.md)
