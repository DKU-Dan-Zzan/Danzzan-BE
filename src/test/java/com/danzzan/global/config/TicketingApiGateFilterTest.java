package com.danzzan.global.config;
import com.danzzan.domain.festival.service.TicketingAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TicketingApiGateFilterTest {
    @Test void offBlocksTicketsButPreservesAccountsAndAdmin() throws Exception {
        var policy = mock(TicketingAccessPolicy.class);
        var filter = new TicketingApiGateFilter(policy);
        for (String path : new String[]{"/user/me", "/user/login", "/user/signup", "/auth/login", "/api/admin/events"}) {
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

    @Test void offBlocksExistingTicketLookup() throws Exception {
        var filter = new TicketingApiGateFilter(mock(TicketingAccessPolicy.class));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/tickets/me"), response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void onAllowsExistingTicketLookup() throws Exception {
        var policy = mock(TicketingAccessPolicy.class);
        when(policy.isTicketingEnabled()).thenReturn(true);
        var chain = new MockFilterChain();
        new TicketingApiGateFilter(policy).doFilter(
                new MockHttpServletRequest("GET", "/tickets/me"), new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
    }

    @Test void offAllowsCorsPreflight() throws Exception {
        var chain = new MockFilterChain();
        new TicketingApiGateFilter(mock(TicketingAccessPolicy.class)).doFilter(
                new MockHttpServletRequest("OPTIONS", "/tickets/me"), new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
    }
}
