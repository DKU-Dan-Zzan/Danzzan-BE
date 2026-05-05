package com.danzzan.infra.dku;

import com.danzzan.infra.dku.exception.DkuFailedCrawlingException;
import com.danzzan.infra.dku.exception.DkuFailedLoginException;
import com.danzzan.infra.dku.model.DkuAuth;
import com.danzzan.infra.dku.model.StudentInfo;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.util.List;

/**
 * 단국대 웹정보 포털에서 학생 정보를 크롤링하는 서비스.
 *
 * {@link DkuAuthenticationService}로부터 받은 세션 쿠키({@link DkuAuth})를 사용하여
 * 학생정보 페이지에 접근하고, HTML을 파싱하여 학생 데이터를 추출한다.
 *
 * <p><b>크롤링 흐름:</b></p>
 * <pre>
 * 1. 세션 쿠키를 Cookie 헤더에 담아 학생정보 페이지 GET 요청
 * 2. 200 OK → HTML 직접 파싱
 *    302 Found → SSO 리다이렉트 체인 재이동 후 파싱
 * 3. Jsoup으로 hidden input 필드에서 학번/이름/학적/소속 추출
 * 4. 소속(단과대학+학과)을 마지막 공백 기준으로 분리
 * </pre>
 */
@Slf4j
@Service
public class DkuStudentService {

    private static final String WEBINFO_URL = "https://webinfo.dankook.ac.kr";

    /** 학생 기본정보 조회 페이지 경로 */
    private static final String STUDENT_INFO_PATH = "/tiac/univ/srec/srlm/views/findScregBasWeb.do?_view=ok";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final WebClient webClient;

    public DkuStudentService() {
        // followRedirect(false): 리다이렉트를 수동으로 처리하여 중간 쿠키를 모두 수집
        HttpClient httpClient = HttpClient.create()
                .followRedirect(false);

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    /**
     * 로그인 세션 쿠키를 이용해 학생 정보를 크롤링하여 반환한다.
     *
     * @param auth 로그인 후 수집된 세션 쿠키 묶음
     * @return 파싱된 학생 정보 {@link StudentInfo}
     * @throws DkuFailedLoginException    세션이 유효하지 않아 로그인 페이지가 반환된 경우
     * @throws DkuFailedCrawlingException HTML 파싱 실패 또는 기타 크롤링 오류
     */
    public StudentInfo crawlStudentInfo(DkuAuth auth) {
        try {
            MultiValueMap<String, String> cookies = auth.getCookies();
            log.info("크롤링 시작. 쿠키: {}", cookies.keySet());

            // 학생 정보 페이지 요청
            String html = fetchStudentInfo(cookies);

            if (html == null || html.isEmpty()) {
                throw new DkuFailedCrawlingException("학생 정보 페이지를 가져올 수 없습니다.");
            }

            log.info("학생 정보 HTML 길이: {}", html.length());
            // 디버그: HTML을 파일에 저장
            try {
                java.nio.file.Files.writeString(
                    java.nio.file.Path.of("/tmp/dku_student_info.html"), html);
            } catch (Exception ex) {
                log.warn("HTML 파일 저장 실패: {}", ex.getMessage());
            }

            return parseStudentInfo(html);

        } catch (DkuFailedLoginException e) {
            // 로그인 실패는 그대로 전파
            throw e;
        } catch (Exception e) {
            log.error("학생 정보 크롤링 실패: {}", e.getMessage(), e);
            // 에러 정보를 파일에 저장
            try {
                java.nio.file.Files.writeString(
                    java.nio.file.Path.of("/tmp/dku_error.txt"),
                    "Error: " + e.getClass().getName() + ": " + e.getMessage() + "\nCookies: " + auth.getCookies());
            } catch (Exception ignored) {}
            throw new DkuFailedCrawlingException("학생 정보 크롤링 중 오류가 발생했습니다.");
        }
    }

    /**
     * 쿠키 Map을 "key=value; key2=value2" 형태의 Cookie 헤더 문자열로 변환한다.
     * WebClient의 .cookies()는 내부적으로 쿠키를 관리하지만,
     * 일부 경우 직접 Cookie 헤더를 문자열로 넘겨야 정상 동작하기 때문에 사용한다.
     */
    private String toCookieHeader(MultiValueMap<String, String> cookies) {
        StringBuilder sb = new StringBuilder();
        cookies.forEach((name, values) -> {
            for (String value : values) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(name).append("=").append(value);
            }
        });
        return sb.toString();
    }

    /**
     * 학생정보 페이지를 GET 요청으로 가져온다.
     * 응답이 302이면 SSO 리다이렉트 체인을 최대 5회 따라가며 최종 HTML을 반환한다.
     *
     * @param cookies 현재 세션 쿠키 Map
     * @return 학생정보 페이지 HTML 문자열 (실패 시 null)
     */
    private String fetchStudentInfo(MultiValueMap<String, String> cookies) {
        String cookieHeader = toCookieHeader(cookies);
        log.info("학생정보 요청 Cookie 헤더: {}", cookieHeader);

        ResponseEntity<String> response = webClient.get()
                .uri(WEBINFO_URL + STUDENT_INFO_PATH)
                .header(HttpHeaders.COOKIE, cookieHeader)
                .header(HttpHeaders.REFERER, WEBINFO_URL + "/")
                .retrieve()
                .onStatus(status -> false, resp -> null)
                .toEntity(String.class)
                .block();

        if (response == null) return null;

        HttpStatus status = (HttpStatus) response.getStatusCode();
        log.info("학생정보 응답 상태: {}", status);

        // 200 OK: 바로 HTML 반환
        if (status == HttpStatus.OK) {
            return response.getBody();
        }

        // 302: 세션이 만료됐거나 SSO 재인증이 필요한 경우
        // Location 헤더를 따라 리다이렉트 체인을 재이동하며 쿠키를 갱신
        if (status == HttpStatus.FOUND || status == HttpStatus.MOVED_TEMPORARILY) {
            URI location = response.getHeaders().getLocation();
            log.info("학생정보 리다이렉트: {}", location);

            if (location != null) {
                // SSO 리다이렉트 체인 따라가기
                collectCookies(response.getHeaders(), cookies);
                cookieHeader = toCookieHeader(cookies);

                for (int i = 0; i < 5; i++) {
                    log.info("학생정보 리다이렉트 {}단계: {}", i + 1, location);
                    ResponseEntity<String> rResponse = webClient.get()
                            .uri(location)
                            .header(HttpHeaders.COOKIE, cookieHeader)
                            .header(HttpHeaders.REFERER, WEBINFO_URL + "/")
                            .retrieve()
                            .onStatus(s -> false, resp -> null)
                            .toEntity(String.class)
                            .block();

                    if (rResponse == null) return null;
                    collectCookies(rResponse.getHeaders(), cookies);
                    cookieHeader = toCookieHeader(cookies);

                    HttpStatus rStatus = (HttpStatus) rResponse.getStatusCode();
                    if (rStatus == HttpStatus.OK) {
                        return rResponse.getBody();
                    }

                    location = rResponse.getHeaders().getLocation();
                    // Location이 없으면 현재 응답 body가 최종 결과
                    if (location == null) {
                        return rResponse.getBody();
                    }
                }
            }
        }

        return response.getBody();
    }

    /**
     * HTTP 응답 헤더의 Set-Cookie 값을 파싱하여 쿠키 Map에 추가한다.
     * 같은 이름의 쿠키가 이미 있으면 최신 값으로 교체한다.
     */
    private void collectCookies(HttpHeaders headers, MultiValueMap<String, String> cookies) {
        List<String> setCookieHeaders = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null) return;

        for (String setCookie : setCookieHeaders) {
            // "JSESSIONID=abc123; Path=/; HttpOnly" → name="JSESSIONID", value="abc123"
            String[] parts = setCookie.split(";")[0].split("=", 2);
            if (parts.length == 2) {
                String name = parts[0].trim();
                String value = parts[1].trim();
                cookies.remove(name);   // 기존 값 제거 후 최신 값으로 갱신
                cookies.add(name, value);
            }
        }
    }

    /**
     * 학생정보 페이지 HTML을 Jsoup으로 파싱하여 {@link StudentInfo}를 반환한다.
     *
     * <p>단국대 학생정보 페이지는 학생 데이터를 hidden input 필드에 담아 내려준다:</p>
     * <pre>
     * &lt;input type="hidden" id="nm"         value="홍길동"&gt;
     * &lt;input type="hidden" id="stuid"      value="32211862"&gt;
     * &lt;input type="hidden" id="scregStaNm" value="재학"&gt;
     * &lt;input type="hidden" id="pstnOrgzNm" value="공과대학 컴퓨터공학과"&gt;
     * &lt;input type="hidden" id="etrsYy"     value="2022"&gt;
     * </pre>
     *
     * @throws DkuFailedLoginException    studentId가 없고 HTML에 로그인 폼이 있는 경우 (세션 만료)
     * @throws DkuFailedCrawlingException studentId가 없지만 로그인 페이지도 아닌 경우
     */
    private StudentInfo parseStudentInfo(String html) {
        Document doc = Jsoup.parse(html);

        String studentName    = getElementValue(doc, "nm");           // 학생 이름
        String studentId      = getElementValue(doc, "stuid");        // 학번
        String academicStatus = getElementValue(doc, "scregStaNm");   // 학적상태 (재학/휴학/졸업/수료)
        String affiliation    = getElementValue(doc, "pstnOrgzNm");   // 소속 (예: "공과대학 컴퓨터공학과")
        String yearStr        = getElementValue(doc, "etrsYy");       // 입학년도
        String registrationDate = getElementValue(doc, "regsDt");     // 등록일자 (예: "20260311")

        // studentId가 없으면 학생정보 페이지가 아닌 다른 페이지가 반환된 것
        if (studentId == null || studentId.isEmpty()) {
            // 로그인 폼이 포함된 경우 → 세션 만료 또는 로그인 실패
            if (html.contains("logonForm") || html.contains("member/logon.do")) {
                log.warn("학생 정보 페이지 대신 로그인 페이지가 반환됨 → 로그인 실패");
                throw new DkuFailedLoginException("단국대 포털 로그인에 실패했습니다. 학번과 비밀번호를 확인해주세요.");
            }
            log.error("학생 정보 파싱 실패. HTML 앞 500자: {}", html.substring(0, Math.min(500, html.length())));
            throw new DkuFailedCrawlingException("학생 정보를 파싱할 수 없습니다.");
        }

        // pstnOrgzNm = "공과대학 컴퓨터공학과" 형태이므로 마지막 공백 기준으로 단과대/학과 분리
        // 예: "공과대학 컴퓨터공학과" → college="공과대학", major="컴퓨터공학과"
        //     "사범대학 교육대학원 교육행정학과" → college="사범대학 교육대학원", major="교육행정학과"
        String college = "";
        String major = "";
        if (affiliation != null && !affiliation.isEmpty()) {
            int spaceIdx = affiliation.lastIndexOf(' ');
            if (spaceIdx > 0) {
                college = affiliation.substring(0, spaceIdx).trim();
                major = affiliation.substring(spaceIdx + 1).trim();
            } else {
                // 공백이 없는 경우 전체를 학과로 처리
                major = affiliation.trim();
            }
        }

        // 입학년도 파싱 (숫자만 추출하여 앞 4자리 사용)
        int yearOfAdmission = 0;
        if (yearStr != null && !yearStr.isEmpty()) {
            try {
                yearOfAdmission = Integer.parseInt(yearStr.replaceAll("[^0-9]", "").substring(0, 4));
            } catch (Exception e) {
                log.warn("입학년도 파싱 실패: {}", yearStr);
            }
        }

        log.info("학생 정보 파싱 성공: 학번={}, 이름={}, 학적={}", studentId, studentName, academicStatus);

        return new StudentInfo(
                studentName != null ? studentName : "",
                studentId,
                college,
                major,
                academicStatus != null ? academicStatus : "",
                yearOfAdmission,
                registrationDate != null ? registrationDate : ""
        );
    }

    /**
     * Jsoup Document에서 주어진 id를 가진 요소의 값을 추출한다.
     *
     * <p>추출 우선순위:</p>
     * <ol>
     *   <li>id로 요소를 찾아 {@code .val()} 시도 (input/select 등 form 요소)</li>
     *   <li>위 결과가 없으면 {@code .text()} 시도 (span/div 등 텍스트 요소)</li>
     *   <li>id로 못 찾으면 {@code [name=id]} 속성으로 재시도</li>
     * </ol>
     *
     * @param doc Jsoup Document
     * @param id  찾을 HTML 요소의 id (또는 name 속성 값)
     * @return 추출된 값 (없으면 null)
     */
    private String getElementValue(Document doc, String id) {
        Element element = doc.getElementById(id);
        if (element != null) {
            String value = element.val();   // <input value="..."> 우선 시도
            if (value != null && !value.isEmpty()) {
                return value.trim();
            }
            String text = element.text();   // <span>...</span> 등 텍스트 fallback
            if (text != null && !text.isEmpty()) {
                return text.trim();
            }
        }

        // id 속성으로 찾지 못한 경우 name 속성으로 재시도
        Element byName = doc.selectFirst("[name=" + id + "]");
        if (byName != null) {
            String value = byName.val();
            if (value != null && !value.isEmpty()) {
                return value.trim();
            }
        }

        return null;
    }
}
