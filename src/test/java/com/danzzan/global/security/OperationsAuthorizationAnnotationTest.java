package com.danzzan.global.security;

import com.danzzan.domain.event.controller.AdminEventController;
import com.danzzan.domain.ticket.controller.AdminTicketController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsAuthorizationAnnotationTest {

    private static final String TICKETING_CHECK =
            "@userAdminAuthorizationService.hasTicketingRole(authentication)";

    @Test
    void everyEventAndTicketAdminMethodUsesTheTicketingAuthorizationCheck() {
        assertTicketingChecks(AdminEventController.class);
        assertTicketingChecks(AdminTicketController.class);
    }

    private void assertTicketingChecks(Class<?> controllerType) {
        Arrays.stream(controllerType.getDeclaredMethods())
                .map(method -> new Object[]{method, method.getAnnotation(PreAuthorize.class)})
                .filter(pair -> pair[1] != null)
                .forEach(pair -> assertThat(((PreAuthorize) pair[1]).value())
                        .as(((Method) pair[0]).getName())
                        .isEqualTo(TICKETING_CHECK));
    }
}
