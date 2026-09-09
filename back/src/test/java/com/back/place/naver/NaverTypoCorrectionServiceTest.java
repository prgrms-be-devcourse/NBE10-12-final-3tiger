package com.back.place.naver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NaverTypoCorrectionServiceTest {

    private final NaverTypoCorrectionClient client = mock(NaverTypoCorrectionClient.class);
    private final NaverTypoCorrectionService service = new NaverTypoCorrectionService(client);

    @Test
    void cachesCorrectionIncludingEmptyResult() {
        given(client.correct("spdlqj")).willReturn("");

        assertThat(service.correct("spdlqj")).isEmpty();
        assertThat(service.correct("spdlqj")).isEmpty();

        verify(client, times(1)).correct("spdlqj");
    }
}
