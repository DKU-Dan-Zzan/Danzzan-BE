package com.danzzan.global.config;
import com.danzzan.domain.festival.service.TicketingAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TicketingApiGateFilterTest {
    @Test void offBlocksBookingButPreservesAccountsAndExistingTickets() throws Exception {
        var policy = mock(TicketingAccessPolicy.class);
        var filter = new TicketingApiGateFilter(policy);
        for (String path : new String[]{"/tickets/me", "/user/me", "/user/login", "/user/signup", "/auth/login", "/api/admin/events"}) {
            var chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("GET",path), new MockHttpServletResponse(), chain);
            assertNotNull(chain.getRequest(), path);
        }
        for (String path : new String[]{"/tickets/events", "/tickets/1/queue/enter", "/tickets/1/reserve"}) {
            var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("POST",path),response,chain);
            assertEquals(403,response.getStatus()); assertNull(chain.getRequest());
        }
    }
}
