package team.hotelchain.hotel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import team.hotelchain.staff.StaffAccessService;

/**
 * 본사가 지점을 만들고 목록을 읽는 쓰기 동작을 검증한다.
 * <p>
 * 멱원은 두 갈래로 동작한다. 같은 키 재호출은 200 created=false, 응답 유실 뒤 새 키로
 * 같은 내용을 보내도 같은 결과가 내려와야 한다.
 */
@SpringBootTest
class HotelCreateIntegrationTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final UUID SOKCHO = UUID.fromString("17000000-0000-0000-0000-000000000001");
    private static final String HQ_EMAIL = "hotel-hq@example.com";
    private static final String SOKCHO_EMAIL = "hotel-sokcho@example.com";

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private String hqToken;
    private String sokchoToken;

    @BeforeEach
    void seed() {
        clean();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "쓰기 속초", "속초", "Asia/Seoul");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), HQ_EMAIL, "본사 관리자", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), SOKCHO_EMAIL, "속초 직원", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);

        hqToken = staffAccess.login(HQ_EMAIL, "hq-password").token();
        sokchoToken = staffAccess.login(SOKCHO_EMAIL, "branch-password").token();
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void headquartersCreatesHotel() throws Exception {
        // 빈 지점을 만든다. 객실 유형이 없으므로 roomTypes는 0이다.
        String body = createHotel("create-hotel", "춘천 지점", "강원", "Asia/Seoul", status().isCreated())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.name").value("춘천 지점"))
                .andExpect(jsonPath("$.region").value("강원"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.roomTypes").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID hotelId = UUID.fromString(mapper.readTree(body).get("hotelId").asText());

        UUID storedId = jdbc.queryForObject(
                "select id from hotel where name = ?", UUID.class, "춘천 지점");
        org.assertj.core.api.Assertions.assertThat(storedId).isEqualTo(hotelId);

        // 멱원 기록이 남아 있어야 재호출이 같은 결과를 찾을 수 있다.
        Integer commandCount = jdbc.queryForObject(
                "select count(*) from hotel_command where hotel_id = ?", Integer.class, hotelId);
        org.assertj.core.api.Assertions.assertThat(commandCount).isEqualTo(1);
    }

    @Test
    void idempotencyReplayReturnsSameHotel() throws Exception {
        // 같은 키 재호출은 200 created=false로 같은 지점을 돌려준다.
        String first = createHotel("replay-hotel", "부산 지점", "부산", "Asia/Seoul", status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode firstJson = mapper.readTree(first);
        UUID firstId = UUID.fromString(firstJson.get("hotelId").asText());

        String second = createHotel("replay-hotel", "부산 지점", "부산", "Asia/Seoul", status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(mapper.readTree(second).get("hotelId").asText())
                .isEqualTo(firstId.toString());

        Integer hotelCount = jdbc.queryForObject(
                "select count(*) from hotel where name = '부산 지점'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(hotelCount).isEqualTo(1);
    }

    @Test
    void newKeyWithSameContentReturnsSameHotel() throws Exception {
        // 응답을 잃은 뒤 새 키로 같은 내용을 보내도 SHA-256 지문이 같아 같은 지점을 돌려준다.
        String first = createHotel("first-key", "제주 지점", "제주도", "Asia/Seoul", status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID firstId = UUID.fromString(mapper.readTree(first).get("hotelId").asText());

        createHotel("second-key", "제주 지점", "제주도", "Asia/Seoul", status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.hotelId").value(firstId.toString()));

        Integer hotelCount = jdbc.queryForObject(
                "select count(*) from hotel where name = '제주 지점'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(hotelCount).isEqualTo(1);
    }

    @Test
    void duplicateNameIsRejected() throws Exception {
        // 이미 있는 이름으로 만들면 409로 거부한다. 빈 지점이 중복으로 만들어지지 않는다.
        // 두 번째 요청은 지역이 달라서 같은 멱원 지문이 아니어야 한다. 그래야 이름 충돌이
        // 멱원 재호출과 구분된다.
        createHotel("dup-first", "춘천 지점", "강원", "Asia/Seoul", status().isCreated());
        createHotel("dup-second", "춘천 지점", "경기", "Asia/Seoul", status().isConflict())
                .andExpect(jsonPath("$.code").value("HOTEL_NAME_CONFLICT"));

        Integer hotelCount = jdbc.queryForObject(
                "select count(*) from hotel where name = '춘천 지점'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(hotelCount).isEqualTo(1);
    }

    @Test
    void duplicateNameIsCaseInsensitive() throws Exception {
        // 대소문자만 다른 이름은 같은 지점으로 본다. 대소문자로 중복을 만들 수 없다.
        createHotel("dup-upper", "GANGNEUNG", "강원", "Asia/Seoul", status().isCreated());
        createHotel("dup-lower", "gangneung", "강원", "Asia/Seoul", status().isConflict());

        Integer hotelCount = jdbc.queryForObject(
                "select count(*) from hotel where lower(name) = 'gangneung'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(hotelCount).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(post("/api/staff/hotels")
                .header("X-Staff-Session", hqToken)
                .contentType("application/json")
                .content("{\"name\":\"원주 지점\",\"region\":\"강원\",\"timezone\":\"Asia/Seoul\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankFieldsAreRejected() throws Exception {
        createHotel("blank-name", "  ", "강원", "Asia/Seoul", status().isBadRequest());
        createHotel("blank-region", "원주 지점", "  ", "Asia/Seoul", status().isBadRequest());
        createHotel("blank-timezone", "원주 지점", "강원", "  ", status().isBadRequest());
    }

    @Test
    void unknownTimezoneIsRejected() throws Exception {
        // 잘못된 시간대는 재고 시드와 취소 마감 시각이 엉뚱한 날짜를 가리킨다.
        createHotel("bad-timezone", "원주 지점", "강원", "Mars/Olympus", status().isBadRequest());

        Integer hotelCount = jdbc.queryForObject(
                "select count(*) from hotel where name = '원주 지점'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(hotelCount).isEqualTo(0);
    }

    @Test
    void branchStaffCannotCreateHotel() throws Exception {
        // 지점 생성은 본사 전용이다. 지점 직원은 403이다.
        createHotel("branch-create", "지점 직원 지점", "강원", "Asia/Seoul", status().isForbidden(),
                sokchoToken);
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(post("/api/staff/hotels")
                .header("Idempotency-Key", "no-session")
                .contentType("application/json")
                .content("{\"name\":\"원주 지점\",\"region\":\"강원\",\"timezone\":\"Asia/Seoul\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void headquartersListsHotels() throws Exception {
        // 지점 목록은 카탈로그·재고·보고서 화면의 지점 선택기가 쓴다.
        createHotel("list-hotel", "춘천 지점", "강원", "Asia/Seoul", status().isCreated());

        mvc.perform(get("/api/staff/hotels")
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '춘천 지점')]").exists())
                .andExpect(jsonPath("$[?(@.name == '쓰기 속초')]").exists());
    }

    @Test
    void branchStaffCannotListHotels() throws Exception {
        // 지점 목록은 본사 전용이다. 지점 직원은 자기 지점만 보면 되므로 403이다.
        mvc.perform(get("/api/staff/hotels")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listingRequiresSession() throws Exception {
        mvc.perform(get("/api/staff/hotels"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions createHotel(
            String idempotencyKey, String name, String region, String timezone,
            org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        return createHotel(idempotencyKey, name, region, timezone, expected, hqToken);
    }

    private org.springframework.test.web.servlet.ResultActions createHotel(
            String idempotencyKey, String name, String region, String timezone,
            org.springframework.test.web.servlet.ResultMatcher expected,
            String token)
            throws Exception {
        return mvc.perform(post("/api/staff/hotels")
                .header("X-Staff-Session", token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content("{\"name\":\"" + name + "\",\"region\":\"" + region
                        + "\",\"timezone\":\"" + timezone + "\"}"))
                .andExpect(expected);
    }

    private void clean() {
        // staff_session이 staff_member를 참조하고 staff_member가 hotel을 참조하므로
        // 세션 → 직원 → 지점 순으로 지운다. 이전 실행이 도중에 실패해 남은 행도 지운다.
        jdbc.update("delete from staff_session");
        jdbc.update("delete from hotel_command");
        jdbc.update("delete from staff_member where email in (?, ?) or hotel_id = ?", HQ_EMAIL, SOKCHO_EMAIL, SOKCHO);
        jdbc.update("delete from hotel where id = ?", SOKCHO);
        jdbc.update("delete from hotel where name in ('춘천 지점','부산 지점','제주 지점','GANGNEUNG','gangneung','지점 직원 지점','원주 지점')");
    }
}
