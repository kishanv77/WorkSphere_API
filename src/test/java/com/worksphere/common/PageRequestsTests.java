package com.worksphere.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.worksphere.common.api.PageRequests;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestsTests {

    @Test
    void clampsPageAndSize() {
        Pageable negative = PageRequests.of(-5, 0, Sort.unsorted());
        assertEquals(0, negative.getPageNumber());
        assertEquals(PageRequests.DEFAULT_SIZE, negative.getPageSize());

        assertEquals(PageRequests.MAX_SIZE, PageRequests.of(2, 10_000, Sort.unsorted()).getPageSize());
        assertEquals(2, PageRequests.of(2, 10, Sort.unsorted()).getPageNumber());
    }
}