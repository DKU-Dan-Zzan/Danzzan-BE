package com.danzzan.infra.dku;

import com.danzzan.infra.dku.exception.DkuFailedLoginException;
import com.danzzan.infra.dku.model.DkuAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 단국대 웹정보 포털(webinfo.dankook.ac.kr) SSO 로그인을 처리하는 서비스.
 *
 * 브라우저가 로그인하는 과정을 서버 측에서 그대로 흉내낸다 (헤드리스 브라우저 방식).
 *
 * <p><b>전체 로그인 흐름:</b></p>
 * <pre>
 * 1. webinfo 초기 접속 → JSESSIONID 등 초기 세션 쿠키 수집
 * 2. logon.do에 학번/비밀번호 POST → 302 리다이렉트 응답
 * 3. SSO 리다이렉트 체인 순차 이동 (pmi-sso.jsp → portal → pmi-sso2.jsp → ...)
 *    → 각 단계에서 발급되는 쿠키를 누적 수집
 * 4. 학생정보 페이지 사전 접근 → webinfo 측 세션 최종 확보
 * 5. 수집된 쿠키 묶음을 DkuAuth로 반환
 * </pre>
 *
 * <p><b>로그인 성공/실패 판단 기준:</b></p>
 * <ul>
 *   <li>POST 응답 200 OK → 로그인 페이지 재렌더링 = 학번/비밀번호 불일치</li>
 *   <li>POST 응답 302 Found → 로그인 성공 → SSO 리다이렉트 시작</li>
 * </ul>
 */
@Slf4j
@Service
public class DkuAuthenticationService {

    private static final String WEBINFO_URL = "https://webinfo.dankook.ac.kr";

    /** SSO 파라미터(sso=ok)를 포함한 로그인 엔드포인트 */
    private static final String LOGIN_URL = WEBINFO_URL + "/member/logon.do?sso=ok";

    /** 무한 리다이렉트 방지를 위한 최대 리다이렉트 횟수 */
    private static final int MAX_REDIRECTS = 10;

    /**
     * 실제 Chrome 브라우저처럼 보이기 위한 User-Agent.
     * 봇 차단을 우회하기 위해 사용한다.
     */
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final WebClient webClient;

    public DkuAuthenticationService() {
        // followRedirect(false): 리다이렉트를 자동으로 따라가지 않고
        // 302 응답을 직접 받아서 쿠키를 수동으로 수집한다.
        // 자동 리다이렉트 시 중간 단계의 Set-Cookie 헤더를 놓칠 수 있기 때문.
        HttpClient httpClient = HttpClient.create()
                .followRedirect(false);

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .build();
    }

    /**
     * 단국대 포털에 로그인하여 인증된 세션 쿠키 묶음을 반환한다.
     *
     * @param studentId 단국대 학번
     * @param password  포털 비밀번호
     * @return 로그인 성공 후 수집된 세션 쿠키 묶음 ({@link DkuAuth})
     * @throws DkuFailedLoginException 학번/비밀번호 불일치 또는 예상치 못한 응답 시
     */
    public DkuAuth login(String studentId, String password) {
        // 쿠키를 누적 저장할 Map (key=쿠키 이름, value=쿠키 값 목록)
        MultiValueMap<String, String> cookies = new LinkedMultiValueMap<>();

        // 0단계: webinfo 초기 접속 → JSESSIONID 등 초기 쿠키 확보
        // 이 쿠키 없이 바로 POST하면 서버가 세션을 인식하지 못할 수 있음
        collectInitialCookies(cookies);

        // 1단계: logon.do에 학번/비밀번호 POST
        // form body: username=학번&password=비번&tabIndex=0
        String formData = makeFormData(studentId, password);
        ResponseEntity<String> loginResponse = postLogin(LOGIN_URL, formData, cookies);

        HttpStatus loginStatus = (HttpStatus) loginResponse.getStatusCode();
        collectCookies(loginResponse.getHeaders(), cookies);

        log.info("로그인 POST 응답: {}, Location: {}", loginStatus, loginResponse.getHeaders().getLocation());

        // 200 OK = 로그인 실패 (포털이 로그인 폼 페이지를 다시 렌더링해서 내려줌)
        if (loginStatus == HttpStatus.OK) {
            log.warn("DKU 로그인 실패: 200 OK (잘못된 학번/비밀번호)");
            throw new DkuFailedLoginException();
        }

        // 302 이외의 응답은 예상 외 → 로그인 실패 처리
        if (!isRedirect(loginStatus)) {
            log.error("DKU 로그인 예상 외 응답: {}", loginStatus);
            throw new DkuFailedLoginException();
        }

        // 2단계: 302 응답의 Location 헤더를 따라 SSO 리다이렉트 체인 이동
        // 체인 예시: pmi-sso.jsp → portal.dankook.ac.kr → pmi-sso2.jsp → webinfo
        URI location = loginResponse.getHeaders().getLocation();
        if (location == null) {
            log.error("DKU 로그인: Location 헤더 없음");
            throw new DkuFailedLoginException();
        }

        followRedirectChain(location, cookies);

        // 3단계: 학생정보 페이지를 미리 한 번 방문하여 webinfo 측 세션을 완전히 활성화
        // 이 단계를 건너뛰면 DkuStudentService에서 학생정보 조회 시 세션이 유효하지 않을 수 있음
        try {
            URI studentInfoUri = URI.create(WEBINFO_URL + "/tiac/univ/srec/srlm/views/findScregBasWeb.do?_view=ok");
            followRedirectChain(studentInfoUri, cookies);
        } catch (Exception e) {
            // 사전 접근 실패는 치명적이지 않으므로 무시 (DkuStudentService에서 재시도)
            log.warn("학생정보 페이지 사전 접근 실패 (무시): {}", e.getMessage());
        }

        log.info("DKU 로그인 완료. 수집된 쿠키 키: {}", cookies.keySet());
        return new DkuAuth(cookies);
    }

    /**
     * webinfo 초기 접속으로 세션 시작에 필요한 초기 쿠키를 수집한다.
     * 오류 발생 시 무시하고 진행한다 (쿠키 없이도 로그인이 성공하는 경우가 있음).
     */
    private void collectInitialCookies(MultiValueMap<String, String> cookies) {
        try {
            URI currentUri = URI.create(WEBINFO_URL + "/");
            for (int i = 0; i < MAX_REDIRECTS; i++) {
                ResponseEntity<String> response = doGet(currentUri, cookies);
                collectCookies(response.getHeaders(), cookies);

                HttpStatus status = (HttpStatus) response.getStatusCode();
                if (!isRedirect(status)) break;

                URI nextLocation = response.getHeaders().getLocation();
                if (nextLocation == null) break;
                // 상대 경로 Location을 현재 URL 기준 절대 URL로 변환
                if (!nextLocation.isAbsolute()) {
                    nextLocation = currentUri.resolve(nextLocation);
                }
                currentUri = nextLocation;
            }
        } catch (Exception e) {
            log.warn("초기 쿠키 수집 중 오류 (무시): {}", e.getMessage());
        }
    }

    /**
     * 302 리다이렉트 체인을 순차적으로 따라가며 쿠키를 누적 수집한다.
     * 200 응답이 오거나 Location 헤더가 없으면 중단한다.
     *
     * @param startLocation 리다이렉트 체인의 시작 URI
     * @param cookies       누적 수집 중인 쿠키 Map (in/out)
     */
    private void followRedirectChain(URI startLocation, MultiValueMap<String, String> cookies) {
        URI location = startLocation;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            log.info("리다이렉트 {}단계: {}", i + 1, location);
            ResponseEntity<String> response = doGet(location, cookies);
            collectCookies(response.getHeaders(), cookies);

            HttpStatus status = (HttpStatus) response.getStatusCode();
            if (!isRedirect(status)) break;

            URI nextLocation = response.getHeaders().getLocation();
            if (nextLocation == null) break;
            // 상대 경로 Location을 현재 URL 기준 절대 URL로 변환
            if (!nextLocation.isAbsolute()) {
                nextLocation = location.resolve(nextLocation);
            }
            location = nextLocation;
        }
    }

    /** 302/301/307 등 리다이렉트 응답 여부 확인 */
    private boolean isRedirect(HttpStatus status) {
        return status == HttpStatus.FOUND || status == HttpStatus.MOVED_TEMPORARILY
                || status == HttpStatus.MOVED_PERMANENTLY || status == HttpStatus.TEMPORARY_REDIRECT;
    }

    /**
     * logon.do에 학번/비밀번호를 form-urlencoded 형식으로 POST한다.
     * onStatus(status -> false, ...): 4xx/5xx도 예외를 던지지 않고 그대로 응답을 받는다.
     * (기본 동작은 4xx/5xx 시 예외 발생이므로 명시적으로 비활성화)
     */
    private ResponseEntity<String> postLogin(String url, String formData, MultiValueMap<String, String> cookies) {
        try {
            return webClient.post()
                    .uri(url)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .header(HttpHeaders.ORIGIN, WEBINFO_URL)
                    .header(HttpHeaders.REFERER, url)
                    .cookies(c -> cookies.forEach((name, values) -> values.forEach(v -> c.add(name, v))))
                    .bodyValue(formData)
                    .retrieve()
                    .onStatus(status -> false, resp -> null) // 모든 상태코드를 정상으로 처리
                    .toEntity(String.class)
                    .block();
        } catch (Exception e) {
            log.error("로그인 POST 요청 실패: {}", e.getMessage());
            throw new DkuFailedLoginException();
        }
    }

    /**
     * 지정 URI에 GET 요청을 보낸다.
     * 현재까지 수집된 쿠키를 Cookie 헤더로 함께 전송한다.
     */
    private ResponseEntity<String> doGet(URI location, MultiValueMap<String, String> cookies) {
        try {
            return webClient.get()
                    .uri(location)
                    .header(HttpHeaders.REFERER, WEBINFO_URL + "/")
                    .cookies(c -> cookies.forEach((name, values) -> values.forEach(v -> c.add(name, v))))
                    .retrieve()
                    .onStatus(status -> false, resp -> null) // 모든 상태코드를 정상으로 처리
                    .toEntity(String.class)
                    .block();
        } catch (Exception e) {
            log.error("GET 요청 실패: {}", e.getMessage());
            throw new DkuFailedLoginException();
        }
    }

    /**
     * 학번/비밀번호를 URL 인코딩하여 form body 문자열로 조립한다.
     * tabIndex=0은 포털 로그인 폼의 필수 파라미터다 (학생 탭 선택).
     */
    private String makeFormData(String studentId, String password) {
        String encodedId = URLEncoder.encode(studentId, StandardCharsets.UTF_8);
        String encodedPwd = URLEncoder.encode(password, StandardCharsets.UTF_8);
        return "username=" + encodedId + "&password=" + encodedPwd + "&tabIndex=0";
    }

    /**
     * HTTP 응답 헤더의 Set-Cookie 값을 파싱하여 쿠키 Map에 추가한다.
     * 같은 이름의 쿠키가 이미 있으면 최신 값으로 교체한다.
     *
     * <p>Set-Cookie 형식 예시: {@code JSESSIONID=abc123; Path=/; HttpOnly}</p>
     * 세미콜론 앞의 "이름=값" 부분만 추출하고 나머지 속성(Path, HttpOnly 등)은 무시한다.
     */
    private void collectCookies(HttpHeaders headers, MultiValueMap<String, String> cookies) {
        List<String> setCookieHeaders = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null) return;

        for (String setCookie : setCookieHeaders) {
            // "JSESSIONID=abc123; Path=/; HttpOnly" → ["JSESSIONID", "abc123"]
            String[] parts = setCookie.split(";")[0].split("=", 2);
            if (parts.length == 2) {
                String name = parts[0].trim();
                String value = parts[1].trim();
                cookies.remove(name);   // 기존 값 제거 후 최신 값으로 갱신
                cookies.add(name, value);
            }
        }
    }
}
