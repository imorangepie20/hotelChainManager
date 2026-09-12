package team.hotelchain.webcontent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WebsitePreviewTransportTest {
    @Test
    void permitsSecureRequestsAndExplicitLocalDevelopmentMode() {
        var secure = new MockHttpServletRequest();
        secure.setSecure(true);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> new WebsitePreviewTransport(true).requireSecure(secure));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> new WebsitePreviewTransport(false).requireSecure(new MockHttpServletRequest()));
    }
    @Test
    void rejectsInsecureIssueAndReadBeforeAccessingGrants() throws Exception {
        var grants = mock(WebsitePreviewGrantService.class);
        var mvc = MockMvcBuilders.standaloneSetup(
                new WebsitePreviewGrantController(grants, new WebsitePreviewTransport(true)),
                new PublicWebsitePreviewController(grants, new WebsitePreviewTransport(true))).build();
        mvc.perform(post("/api/staff/website/pages/12000000-0000-0000-0000-000000000001/preview-grants")
                        .header("X-Staff-Session", "fixture-session").contentType("application/json")
                        .content("{\"locale\":\"ko\",\"expectedDraftVersion\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/website/pages/preview").param("path", "/").param("locale", "ko")
                        .header("X-Website-Preview", "P".repeat(43)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(grants);
    }
}
