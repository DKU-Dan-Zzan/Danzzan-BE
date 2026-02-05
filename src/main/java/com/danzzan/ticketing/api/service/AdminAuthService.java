package com.danzzan.ticketing.api.service;

import com.danzzan.ticketing.api.dto.request.AdminLoginRequestDTO;
import com.danzzan.ticketing.api.dto.response.AdminLoginResponseDTO;
import com.danzzan.ticketing.api.dto.response.AdminLogoutResponseDTO;
import com.danzzan.ticketing.api.dto.response.AdminMeResponseDTO;

public interface AdminAuthService {
    AdminLoginResponseDTO login(AdminLoginRequestDTO request);
    AdminMeResponseDTO me();
    AdminLogoutResponseDTO logout();
}
