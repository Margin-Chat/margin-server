package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.exceptions.GlobalExceptionHandler;
import org.margin.server.social.channel.controllers.ChannelController;
import org.margin.server.social.channel.exceptions.ChannelNotFoundException;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChannelControllerTest {

    @Mock
    private ChannelService channelService;
    @Mock
    private MarginAuthorizationService marginAuthorizationService;
    @InjectMocks
    private ChannelController channelController;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(channelController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getChannelById_whenMissing_returns404WithProblemDetail() throws Exception {
        when(channelService.getById(99L))
                .thenThrow(new ChannelNotFoundException(99L));

        mockMvc().perform(get("/api/channels/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Channel not found: 99"));
    }
}
