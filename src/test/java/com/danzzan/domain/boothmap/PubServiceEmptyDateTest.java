package com.danzzan.domain.boothmap;

import com.danzzan.domain.boothmap.repository.PubImageRepository;
import com.danzzan.domain.boothmap.repository.PubOperationRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
import com.danzzan.domain.boothmap.service.PubService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 축제 운영 날짜는 관리자 설정에서 바뀐다. 아직 주점을 등록하지 않은 날짜로 옮기면
 * 그 날짜에는 주점 운영 정보가 없다.
 *
 * 예전에는 이때 목록 조회가 예외를 던져 404 가 나갔고, 부스맵 화면 전체가
 * "정보를 불러올 수 없습니다" 가 됐다. 목록은 빈 목록이어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class PubServiceEmptyDateTest {

    @Mock
    private PubRepository pubRepository;

    @Mock
    private PubImageRepository pubImageRepository;

    @Mock
    private PubOperationRepository pubOperationRepository;

    @InjectMocks
    private PubService pubService;

    @Test
    void 주점_운영_정보가_없는_날짜는_빈_목록을_준다() {
        LocalDate emptyDate = LocalDate.of(2027, 5, 14);
        when(pubOperationRepository.findByOperationDate(emptyDate)).thenReturn(Optional.empty());

        assertTrue(pubService.getPubs(emptyDate, false).isEmpty());
        // 운영 정보가 없으면 주점을 조회할 필요도 없다.
        verify(pubRepository, never()).findAllVisibleByPubOperationIdWithCollegeAndImages(anyLong());
    }

    @Test
    void 상세_조회는_운영_정보가_없으면_오류를_낸다() {
        LocalDate emptyDate = LocalDate.of(2027, 5, 14);
        when(pubOperationRepository.findByOperationDate(emptyDate)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> pubService.getPubDetail(1L, emptyDate, false)
        );
    }
}
