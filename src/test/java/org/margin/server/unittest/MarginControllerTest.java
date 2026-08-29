package org.margin.server.unittest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.users.api.UserLookup;
import org.margin.server.shared.ratelimit.RateLimitService;
import org.margin.server.social.margin.controllers.MarginController;
import org.margin.server.social.margin.exceptions.MarginNotFoundException;
import org.margin.server.social.margin.models.dtos.CreateNewMarginRequest;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.social.models.Visibility;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.margin.server.unittest.utils.ControllerTestSupport.standaloneMockMvc;

@ExtendWith(MockitoExtension.class)
class MarginControllerTest {

    @org.junit.jupiter.api.BeforeEach
    void authenticate() {
        org.margin.server.unittest.utils.ControllerTestSupport.authenticateAs(
                new org.margin.server.shared.security.AuthenticatedUser(1L, "a@b.c", "alice"));
        org.mockito.Mockito.lenient()
                .when(userLookup.findById(1L))
                .thenReturn(java.util.Optional.of(org.margin.server.unittest.utils.UserTestUtils.createUser(1L, "alice")));
    }

    @org.junit.jupiter.api.AfterEach
    void clearAuth() {
        org.margin.server.unittest.utils.ControllerTestSupport.clearAuthentication();
    }

    public static final String API_MARGINS = "/api/margins/";

    @Mock
    private MarginService marginService;
    @Mock
    private MarginAuthorizationService authenticationService;
    @Mock
    private MarginMapper marginMapper;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private UserLookup userLookup;
    @InjectMocks
    private MarginController marginController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return standaloneMockMvc(marginController);
    }

    @Test
    void shouldCreateMarginWithoutIcon() throws Exception {
        CreateNewMarginRequest request = new CreateNewMarginRequest("Test Margin", "Test Description", "PUBLIC");
        MockMultipartFile data = new MockMultipartFile(
                "data", "", "application/json",
                objectMapper.writeValueAsBytes(request)
        );
        when(rateLimitService.tryConsume(any(), any())).thenReturn(true);

        mockMvc().perform(multipart(API_MARGINS + "create_new_margin")
                        .file(data))
                .andExpect(status().isOk());

        verify(marginService, times(1)).createMargin(
                eq("Test Margin"),
                eq("Test Description"),
                eq(Visibility.PUBLIC),
                eq(null),
                any(Long.class));
    }

    @Test
    void shouldCreateMarginWithIcon() throws Exception {
        CreateNewMarginRequest request = new CreateNewMarginRequest("Test Margin", "Test Description", "PUBLIC");
        MockMultipartFile data = new MockMultipartFile(
                "data", "", "application/json",
                objectMapper.writeValueAsBytes(request)
        );
        MockMultipartFile icon = new MockMultipartFile(
                "marginIcon", "icon.jpg", "image/jpeg",
                "image content".getBytes()
        );
        when(rateLimitService.tryConsume(any(), any())).thenReturn(true);

        mockMvc().perform(multipart(API_MARGINS + "create_new_margin")
                        .file(data)
                        .file(icon))
                .andExpect(status().isOk());

        verify(marginService, times(1)).createMargin(
                eq("Test Margin"),
                eq("Test Description"),
                eq(Visibility.PUBLIC),
                any(),
                any(Long.class));
    }

    @Test
    void shouldGetMargin() throws Exception {
        MarginDTO marginDTO = new MarginDTO(
                1L,
                "Test Margin",
                "Test Description",
                Visibility.PUBLIC,
                null,
                List.of(),
                List.of()
        );

        when(marginService.getMarginAsDto(1L)).thenReturn(marginDTO);

        mockMvc().perform(get(API_MARGINS + "get_margin/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marginId").value(1L))
                .andExpect(jsonPath("$.marginName").value("Test Margin"))
                .andExpect(jsonPath("$.marginDescription").value("Test Description"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.spaces").isArray());
    }


    @Test
    void shouldReturnNotFoundWhenMarginDoesNotExist() throws Exception {
        when(marginService.getMarginAsDto(99L))
                .thenThrow(new MarginNotFoundException(99L));

        mockMvc().perform(get(API_MARGINS + "get_margin/99"))
                .andExpect(status().isNotFound());
    }
}