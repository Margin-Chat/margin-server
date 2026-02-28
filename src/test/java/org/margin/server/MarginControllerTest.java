package org.margin.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.exceptions.GlobalExceptionHandler;
import org.margin.server.social.margin.MarginController;
import org.margin.server.social.margin.MarginNotFoundException;
import org.margin.server.social.margin.MarginService;
import org.margin.server.social.margin.models.Margin;
import org.margin.server.social.margin.models.dtos.CreateNewMarginRequest;
import org.margin.server.social.models.Visibility;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MarginControllerTest {

    public static final String API_MARGINS = "/api/margins/";
    @Mock
    private MarginService marginService;

    @InjectMocks
    private MarginController marginController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(marginController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateMarginWithoutIcon() throws Exception {
        CreateNewMarginRequest request = new CreateNewMarginRequest("Test Margin", "Test Description", "PUBLIC");
        MockMultipartFile data = new MockMultipartFile(
                "data", "", "application/json",
                objectMapper.writeValueAsBytes(request)
        );

        mockMvc().perform(multipart(API_MARGINS + "create_new_margin")
                        .file(data))
                .andExpect(status().isOk());

        verify(marginService, times(1)).createMargin(
                eq("Test Margin"),
                eq("Test Description"),
                eq(Visibility.PUBLIC),
                eq(null)
        );
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

        mockMvc().perform(multipart(API_MARGINS + "create_new_margin")
                        .file(data)
                        .file(icon))
                .andExpect(status().isOk());

        verify(marginService, times(1)).createMargin(
                eq("Test Margin"),
                eq("Test Description"),
                eq(Visibility.PUBLIC),
                any()
        );
    }

    @Test
    void shouldGetMargin() throws Exception {
        Margin margin = new Margin();
        margin.setId(1L);
        margin.setName("Test Margin");
        margin.setDescription("Test Description");
        margin.setVisibility(Visibility.PUBLIC);
        margin.setIconUrl(null);
        margin.setMembers(List.of());
        margin.setSpaces(List.of());

        when(marginService.getMargin(1L)).thenReturn(margin);

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
        when(marginService.getMargin(99L)).thenThrow(new MarginNotFoundException(99L));

        mockMvc().perform(get(API_MARGINS + "get_margin/99"))
                .andExpect(status().isNotFound());
    }
}