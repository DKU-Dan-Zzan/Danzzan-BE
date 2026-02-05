package com.danzzan.ticketing.domain.admin.auth.service;

import com.danzzan.ticketing.domain.admin.auth.dto.AdminLoginRequestDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLoginResponseDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminLogoutResponseDTO;
import com.danzzan.ticketing.domain.admin.auth.dto.AdminMeResponseDTO;

public interface AdminAuthService {
    AdminLoginResponseDTO login(AdminLoginRequestDTO request);
    AdminMeResponseDTO me();
    AdminLogoutResponseDTO logout();
}
