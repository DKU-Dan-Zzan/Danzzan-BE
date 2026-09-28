package com.danzzan.domain.admin.staff.controller;

import com.danzzan.domain.admin.staff.dto.StaffMemberResponse;
import com.danzzan.domain.admin.staff.dto.StaffPageResponse;
import com.danzzan.domain.admin.staff.dto.StaffFilter;
import com.danzzan.domain.admin.staff.exception.StaffManagementException;
import com.danzzan.domain.admin.staff.service.StaffManagementService;
import com.danzzan.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminStaffControllerTest {
    @Mock private StaffManagementService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminStaffController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void 후보_조회는_no_store_ApiResponse_형식이다() throws Exception {
        when(service.findCandidate(eq(1L), eq("32100001"))).thenReturn(member());

        mockMvc.perform(get("/api/admin/staff/candidates").param("studentId", "32100001").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void 목록은_페이지_응답과_no_store를_반환한다() throws Exception {
        when(service.findStaff(1L, 0, 20, StaffFilter.ALL)).thenReturn(new StaffPageResponse(List.of(member()), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/admin/staff").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[0].studentId").value("32100001"))
                .andExpect(jsonPath("$.data.managementEnabled").value(false));
    }

    @Test
    void 목록_필터를_서비스에_전달하고_잘못된_필터는_400이다() throws Exception {
        when(service.findStaff(1L, 0, 20, StaffFilter.TICKETING))
                .thenReturn(new StaffPageResponse(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get("/api/admin/staff").param("filter", "TICKETING").principal(auth()))
                .andExpect(status().isOk());
        verify(service).findStaff(1L, 0, 20, StaffFilter.TICKETING);

        mockMvc.perform(get("/api/admin/staff").param("filter", "UNKNOWN").principal(auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error").value("INVALID_STAFF_FILTER"));
    }

    @Test
    void 잘못된_페이지_형식은_신규_업무_오류_envelope이다() throws Exception {
        mockMvc.perform(get("/api/admin/staff").param("page", "zero").principal(auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.error").value("INVALID_PAGE_REQUEST"));
    }

    @Test
    void 잘못된_대상_ID와_깨진_JSON은_업무_오류_envelope이다() throws Exception {
        mockMvc.perform(patch("/api/admin/staff/nope/role").principal(auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error").value("INVALID_STAFF_TARGET_ID"));

        mockMvc.perform(patch("/api/admin/staff/2/role").principal(auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error").value("INVALID_STAFF_ROLE"));
    }

    @Test
    void 변경_충돌은_409_업무_오류_envelope이다() throws Exception {
        when(service.changeRole(eq(1L), eq(2L), any())).thenThrow(StaffManagementException.conflict());

        mockMvc.perform(patch("/api/admin/staff/2/role").principal(auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.error").value("STAFF_ROLE_CONFLICT"));
    }

    @Test
    void 최고관리자_승격과_강등은_별도_POST로_응답한다() throws Exception {
        when(service.promoteAdmin(1L, 2L)).thenReturn(member());
        when(service.demoteAdmin(1L, 2L)).thenReturn(member());

        mockMvc.perform(post("/api/admin/staff/2/promote-admin").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2));
        mockMvc.perform(post("/api/admin/staff/2/demote-admin").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2));
        verify(service).promoteAdmin(1L, 2L);
        verify(service).demoteAdmin(1L, 2L);
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of());
    }

    private StaffMemberResponse member() {
        return new StaffMemberResponse(2L, "32100001", "홍길동", "공과대학", "컴퓨터공학과", "USER", java.util.List.of());
    }
}
