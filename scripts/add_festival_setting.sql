-- 축제 설정 및 티켓팅 회차 테이블. ddl-auto=validate 환경에서는 BE 배포 전에 실행한다.
-- 적용: mysql -u <user> -p <database> < scripts/add_festival_setting.sql
-- 대상 DB를 먼저 확인한다. 기존 테이블/데이터는 수정하거나 삭제하지 않는다.
-- IF NOT EXISTS는 이미 존재하는 테이블의 누락 컬럼까지 보완하지는 않는다.
-- 설정값과 회차는 관리자 화면에서 저장하므로 초기 데이터는 넣지 않는다.

CREATE TABLE IF NOT EXISTS festival_setting (
    id BIGINT NOT NULL,
    school_name VARCHAR(100) NOT NULL,
    festival_name VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    ticketing_enabled BIT(1) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS festival_ticketing_round (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticketing_at DATETIME(6) NOT NULL,
    capacity INT NOT NULL,
    performance_date DATE NOT NULL,
    display_order INT NOT NULL,
    -- 현재 엔티티는 event_id를 nullable Long으로 저장한다. FK는 별도로 추가하지 않는다.
    event_id BIGINT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
