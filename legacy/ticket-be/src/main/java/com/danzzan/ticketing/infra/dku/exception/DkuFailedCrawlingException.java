package com.danzzan.ticketing.infra.dku.exception;

public class DkuFailedCrawlingException extends RuntimeException {

    public DkuFailedCrawlingException() {
        super("단국대 포털에서 학생 정보를 가져오는데 실패했습니다.");
    }

    public DkuFailedCrawlingException(String message) {
        super(message);
    }
}
