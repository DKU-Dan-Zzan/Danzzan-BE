package com.danzzan.domain.ticket.service.support;

import com.danzzan.domain.ticket.redis.TicketRequestStatus;

public final class ClaimLuaProtocol {

    public static final String USER_CLAIMED_VALUE = "1";

    public static final int RESULT_SIZE = 2;
    public static final int CODE_INDEX = 0;
    public static final int REMAINING_INDEX = 1;

    public static final long CODE_ALREADY = 1L;
    public static final long CODE_SOLD_OUT = 2L;
    public static final long CODE_SUCCESS = 3L;

    public static final String CODE_ALREADY_ARG = String.valueOf(CODE_ALREADY);
    public static final String CODE_SOLD_OUT_ARG = String.valueOf(CODE_SOLD_OUT);
    public static final String CODE_SUCCESS_ARG = String.valueOf(CODE_SUCCESS);

    private ClaimLuaProtocol() {
    }

    public static TicketRequestStatus resolveStatus(long code) {
        if (code == CODE_ALREADY) {
            return TicketRequestStatus.ALREADY;
        }
        if (code == CODE_SOLD_OUT) {
            return TicketRequestStatus.SOLD_OUT;
        }
        if (code == CODE_SUCCESS) {
            return TicketRequestStatus.SUCCESS;
        }
        throw new IllegalStateException("unexpected claim lua code: " + code);
    }
}
