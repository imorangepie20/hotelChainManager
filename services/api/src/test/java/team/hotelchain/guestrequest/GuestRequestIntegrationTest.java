package team.hotelchain.guestrequest;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;

/**
 * 고객 요청 접수·조회·상태 변경의 통합 검증.
 * <p>
 * 접수는 누구나 인증 없이, 조회와 상태 변경은 직원 세션이 필요하다. 본사는 전
 * 지점을 읽고 지점 직원은 자기 지점만 읽는다.
 */
@SpringBootTest
class GuestRequestIntegrationTest {

    private static final UUID SOKCHO = UUID.fromString("16000000-0000-0000-0000-000000000001");
    private static final UUID JEJU = UUID.fromString("16000000-0000-0000-0000-000000000002");
    private static final UUID SOKCHO_ROOM_TYPE = UUID.fromString("26000000-0000-0000-0000-000000000001");
    private static final UUID SOKCHO_RATE_PLAN = UUID.fromString("36000000-0000-0000-0000-000000000001");
    private static final UUID SOKCHO_RESERVATION = UUID.fromString("56000000-0000-0000-0000-000000000001");
    private static final String HQ_EMAIL = "guest-request-hq@example.com";
    private static final String SOKCHO_EMAIL = "guest-request-sokcho@example.com";
    private static final String JEJU_EMAIL = "guest-request-jeju@example.com";

    private static final String REQUEST_BODY = """
            {
              "requestType": "ROOM_REQUEST",
              "subject": "객실 층수 요청",
              "body": "가능하면 높은 층의 객실을 부탁드립니다.",
              "guestName": "request-guest",
              "guestEmail": "guest@example.com",
              "guestPhone": "01012345678"
            }
            """;

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private String hqToken;
    private String sokchoToken;
    private String jejuToken;
    private UUID hqStaffId;

    @BeforeEach
    void seed() {
        clean();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();

        jdbc.update("insert into hotel values (?, ?, ?, ?)", SOKCHO, "request-sokcho", "sokcho", "Asia/Seoul");
        jdbc.update("insert into hotel values (?, ?, ?, ?)", JEJU, "request-jeju", "jeju", "Asia/Seoul");
        jdbc.update("insert into room_type values (?, ?, ?, ?)", SOKCHO_ROOM_TYPE, SOKCHO, "standard", 2);
        jdbc.update("insert into rate_plan values (?, ?, ?, true, 'FLEX-2026-01')", SOKCHO_RATE_PLAN, SOKCHO_ROOM_TYPE, "room-only");
        jdbc.update("""
                insert into reservation
                    (id, room_type_id, rate_plan_id, check_in, check_out, adults, children, rooms,
                     status, total_krw, currency, expires_at, guest_name, guest_email,
                     management_token_hash, policy_snapshot)
                values (?, ?, ?, ?, ?, 2, 0, 1, 'CONFIRMED', 200000, 'KRW', now() + interval '1 hour',
                        'request-guest', 'guest@example.com', 'request-token-hash', '{}'::jsonb)
                """, SOKCHO_RESERVATION, SOKCHO_ROOM_TYPE, SOKCHO_RATE_PLAN,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12));

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        hqStaffId = UUID.randomUUID();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                hqStaffId, HQ_EMAIL, "headquarters-admin", encoder.encode("hq-password"), "HQ_ADMIN", null);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), SOKCHO_EMAIL, "sokcho-staff", encoder.encode("branch-password"), "BRANCH_STAFF", SOKCHO);
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), JEJU_EMAIL, "jeju-staff", encoder.encode("branch-password"), "BRANCH_STAFF", JEJU);

        hqToken = staffAccess.login(HQ_EMAIL, "hq-password").token();
        sokchoToken = staffAccess.login(SOKCHO_EMAIL, "branch-password").token();
        jejuToken = staffAccess.login(JEJU_EMAIL, "branch-password").token();
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void customerSubmitsRequest() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/hotels/" + SOKCHO + "/guest-requests")
                .header("Idempotency-Key", "customer-key-1")
                .contentType("application/json")
                .content(REQUEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.requestType").value("ROOM_REQUEST"))
                .andExpect(jsonPath("$.subject").value("객실 층수 요청"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void idempotencyReturnsSameRequest() throws Exception {
        String first = mvc.perform(MockMvcRequestBuilders.post("/api/hotels/" + SOKCHO + "/guest-requests")
                        .header("Idempotency-Key", "customer-key-2")
                        .contentType("application/json")
                        .content(REQUEST_BODY))
                .andReturn().getResponse().getContentAsString();

        // 같은 멱원 키로 재호출하면 200으로 같은 요청을 돌려준다.
        mvc.perform(MockMvcRequestBuilders.post("/api/hotels/" + SOKCHO + "/guest-requests")
                .header("Idempotency-Key", "customer-key-2")
                .contentType("application/json")
                .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(extractId(first)));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/hotels/" + SOKCHO + "/guest-requests")
                .contentType("application/json")
                .content(REQUEST_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankSubjectIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/hotels/" + SOKCHO + "/guest-requests")
                .header("Idempotency-Key", "customer-key-3")
                .contentType("application/json")
                .content("""
                        {
                          "requestType": "GENERAL_INQUIRY",
                          "subject": "",
                          "body": "내용",
                          "guestName": "request-guest",
                          "guestEmail": "guest@example.com"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRequestTypeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/hotels/" + SOKCHO + "/guest-requests")
                .header("Idempotency-Key", "customer-key-4")
                .contentType("application/json")
                .content("""
                        {
                          "requestType": "RESERVATION_CHANGE",
                          "subject": "예약 변경",
                          "body": "예약을 변경하고 싶습니다.",
                          "guestName": "request-guest",
                          "guestEmail": "guest@example.com"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownHotelIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/hotels/" + UUID.randomUUID() + "/guest-requests")
                .header("Idempotency-Key", "customer-key-5")
                .contentType("application/json")
                .content(REQUEST_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void headquartersListsAllHotels() throws Exception {
        submit("customer-key-6", SOKCHO);
        submit("customer-key-7", JEJU);

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests")
                .header("X-Staff-Session", hqToken)
                .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.requests", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void branchStaffListsOwnHotelOnly() throws Exception {
        submit("customer-key-8", SOKCHO);
        submit("customer-key-9", JEJU);

        // 속초 직원은 속초 요청만 보인다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests")
                .header("X-Staff-Session", sokchoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.requests[0].hotelName").value("request-sokcho"));

        // 제주 직원은 제주 요청만 보인다.
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests")
                .header("X-Staff-Session", jejuToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.requests[0].hotelName").value("request-jeju"));
    }

    @Test
    void headquartersFiltersByHotel() throws Exception {
        submit("customer-key-10", SOKCHO);
        submit("customer-key-11", JEJU);

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests")
                .header("X-Staff-Session", hqToken)
                .param("hotelId", JEJU.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.requests[0].hotelName").value("request-jeju"));
    }

    @Test
    void missingSessionIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void limitOutsideRangeIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests")
                .header("X-Staff-Session", hqToken)
                .param("limit", "0"))
                .andExpect(status().isBadRequest());

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests")
                .header("X-Staff-Session", hqToken)
                .param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeOffsetIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests")
                .header("X-Staff-Session", hqToken)
                .param("offset", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownStatusFilterIsRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests")
                .header("X-Staff-Session", hqToken)
                .param("status", "ARCHIVED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailIncludesContactAndEvents() throws Exception {
        String id = submit("customer-key-12", SOKCHO);

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests/" + id)
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.guestName").value("request-guest"))
                .andExpect(jsonPath("$.guestEmail").value("guest@example.com"))
                .andExpect(jsonPath("$.guestPhone").value("01012345678"))
                .andExpect(jsonPath("$.hotelName").value("request-sokcho"))
                // 접수 이벤트가 1건 있어야 한다.
                .andExpect(jsonPath("$.events", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.events[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$.events[0].toStatus").value("OPEN"));
    }

    @Test
    void branchStaffCannotReadOtherHotelDetail() throws Exception {
        String id = submit("customer-key-13", SOKCHO);

        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests/" + id)
                .header("X-Staff-Session", jejuToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRequestIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/guest-requests/" + UUID.randomUUID())
                .header("X-Staff-Session", hqToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void headquartersTransitionsStatus() throws Exception {
        String id = submit("customer-key-14", SOKCHO);

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/guest-requests/" + id + "/transition")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "transition-key-1")
                .contentType("application/json")
                .content("""
                        {
                          "status": "IN_PROGRESS",
                          "resolutionNote": "프런트데스크에서 확인 중입니다."
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.events", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.events[1].eventType").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.events[1].fromStatus").value("OPEN"))
                .andExpect(jsonPath("$.events[1].toStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.events[1].note").value("프런트데스크에서 확인 중입니다."));
    }

    @Test
    void transitionIsIdempotent() throws Exception {
        String id = submit("customer-key-15", SOKCHO);

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/guest-requests/" + id + "/transition")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "transition-key-2")
                .contentType("application/json")
                .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // 같은 멱원 키 재호출은 200으로 같은 결과를 돌려준다.
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/guest-requests/" + id + "/transition")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "transition-key-2")
                .contentType("application/json")
                .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void transitionRequiresSession() throws Exception {
        String id = submit("customer-key-16", SOKCHO);

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/guest-requests/" + id + "/transition")
                .header("Idempotency-Key", "transition-key-3")
                .contentType("application/json")
                .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void branchStaffCannotTransitionOtherHotelRequest() throws Exception {
        String id = submit("customer-key-17", SOKCHO);

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/guest-requests/" + id + "/transition")
                .header("X-Staff-Session", jejuToken)
                .header("Idempotency-Key", "transition-key-4")
                .contentType("application/json")
                .content("{\"status\": \"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownTransitionStatusIsRejected() throws Exception {
        String id = submit("customer-key-18", SOKCHO);

        mvc.perform(MockMvcRequestBuilders.post("/api/staff/guest-requests/" + id + "/transition")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "transition-key-5")
                .contentType("application/json")
                .content("{\"status\": \"ARCHIVED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transitionOfUnknownRequestIsNotFound() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/staff/guest-requests/" + UUID.randomUUID() + "/transition")
                .header("X-Staff-Session", hqToken)
                .header("Idempotency-Key", "transition-key-6")
                .contentType("application/json")
                .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isNotFound());
    }

    private String submit(String idempotencyKey, UUID hotelId) throws Exception {
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/hotels/" + hotelId + "/guest-requests")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractId(body);
    }

    private String extractId(String body) {
        int start = body.indexOf("\"requestId\":\"") + "\"requestId\":\"".length();
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    private void clean() {
        jdbc.update("delete from staff_session");
        jdbc.update("delete from guest_request_event");
        jdbc.update("delete from guest_request");
        jdbc.update("delete from reservation_night");
        jdbc.update("delete from reservation");
        jdbc.update("delete from rate_plan");
        jdbc.update("delete from room_type");
        jdbc.update("delete from staff_member");
        jdbc.update("delete from hotel where id in (?, ?)", SOKCHO, JEJU);
    }
}
