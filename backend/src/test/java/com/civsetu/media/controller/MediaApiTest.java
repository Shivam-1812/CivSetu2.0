package com.civsetu.media.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.civsetu.support.ApiTest;
import com.civsetu.support.TestFiles;

class MediaApiTest extends ApiTest {

    private static final String REPORT = "{\"title\":\"Burst pipe flooding lane\",\"description\":"
            + "\"Water has been gushing from the main since morning.\",\"category\":\"WATER_LEAK\","
            + "\"latitude\":18.5167,\"longitude\":73.8562,\"address\":\"Kasba Peth, Pune\"}";

    private static MockMultipartFile data(String json) {
        return new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile image(String name, String declaredType, byte[] bytes) {
        return new MockMultipartFile("images", name, declaredType, bytes);
    }

    private static MockMultipartFile voice(byte[] bytes) {
        return new MockMultipartFile("voice", "note.webm", "audio/webm", bytes);
    }

    private MockMultipartHttpServletRequestBuilder report(Session citizen) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/issues");
        request.header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken()));
        return request;
    }

    /** Reports with one photo and one voice note; returns the response body. */
    private String reportWithPhotoAndVoice(Session citizen) throws Exception {
        return mvc.perform(report(citizen)
                        .file(data(REPORT))
                        .file(image("pipe.jpg", "image/jpeg", TestFiles.jpeg(64, 48)))
                        .file(voice(TestFiles.webmVoice())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String mediaUrl(String body, String kind) {
        List<String> urls = JsonPath.read(body, "$.media[?(@.kind == '" + kind + "')].url");
        return urls.get(0);
    }

    @Test
    void reportWithPhotoAndVoiceNoteStoresBoth() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("media"));
        String body = reportWithPhotoAndVoice(citizen);
        String issueUrl = "/api/v1/issues/" + JsonPath.read(body, "$.id");

        assertThat(JsonPath.<List<String>>read(body, "$.media[*].kind")).containsExactlyInAnyOrder("IMAGE", "VOICE");

        // The public sees the photo but not the voice note
        mvc.perform(get(issueUrl))
                .andExpect(jsonPath("$.media[*].kind", containsInAnyOrder("IMAGE")));
    }

    @Test
    void photosAreReencodedWithoutExifAndTurnedUpright() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("exif"));
        // 80x40 landscape pixels, EXIF says "rotate 90° clockwise to display"
        byte[] photo = TestFiles.jpegWithExif(80, 40, 6, "GPS-SECRET 18.5204N 73.8567E");
        assertThat(TestFiles.contains(photo, "GPS-SECRET")).isTrue();

        String body = mvc.perform(report(citizen).file(data(REPORT)).file(image("p.jpg", "image/jpeg", photo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        byte[] stored = mvc.perform(get(mediaUrl(body, "IMAGE")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, private"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(TestFiles.contains(stored, "GPS-SECRET")).isFalse();
        assertThat(TestFiles.contains(stored, "Exif")).isFalse();
        BufferedImage upright = TestFiles.decode(stored);
        assertThat(upright.getWidth()).isEqualTo(40);
        assertThat(upright.getHeight()).isEqualTo(80);
    }

    @Test
    void pngStaysPng() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("png"));
        String body = mvc.perform(report(citizen).file(data(REPORT))
                        .file(image("map.png", "image/png", TestFiles.png(30, 20))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media[0].contentType").value("image/png"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get(mediaUrl(body, "IMAGE"))).andExpect(content().contentType("image/png"));
    }

    @Test
    void oversizedFileIsRejectedAndNothingIsSaved() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("big"));
        byte[] big = Arrays.copyOf(TestFiles.jpeg(10, 10), 10 * 1024 * 1024 + 1);

        mvc.perform(report(citizen).file(data(REPORT)).file(image("big.jpg", "image/jpeg", big)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        mvc.perform(get("/api/v1/issues/mine").header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken())))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void fileTypeIsCheckedByContentNotByName() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("fake"));
        byte[] script = "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.US_ASCII);

        mvc.perform(report(citizen).file(data(REPORT)).file(image("cute-cat.jpg", "image/jpeg", script)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        // A real photo sent as the voice note is rejected too
        mvc.perform(report(citizen).file(data(REPORT)).file(voice(TestFiles.jpeg(10, 10))))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void atMostFivePhotos() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("many"));
        MockMultipartHttpServletRequestBuilder request = report(citizen);
        request.file(data(REPORT));
        for (int i = 0; i < 6; i++) {
            request.file(image("p" + i + ".jpg", "image/jpeg", TestFiles.jpeg(10, 10)));
        }

        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));
    }

    @Test
    void invalidReportDataInMultipartIsValidated() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("baddata"));

        mvc.perform(report(citizen).file(data("{\"title\":\"x\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void voiceNoteIsOnlyForReporterAndResponsibleStaff() throws Exception {
        Session reporter = registerCitizen(uniqueEmail("voice-owner"));
        String voiceUrl = mediaUrl(reportWithPhotoAndVoice(reporter), "VOICE");

        mvc.perform(get(voiceUrl))
                .andExpect(status().isUnauthorized());
        Session neighbour = registerCitizen(uniqueEmail("voice-neighbour"));
        mvc.perform(get(voiceUrl).header(HttpHeaders.AUTHORIZATION, bearer(neighbour.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        Session otherDepartment = staffSession("AUTHORITY", departmentIdByCode("ROADS"));
        mvc.perform(get(voiceUrl).header(HttpHeaders.AUTHORIZATION, bearer(otherDepartment.accessToken())))
                .andExpect(status().isForbidden());

        mvc.perform(get(voiceUrl).header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/webm"))
                .andExpect(content().bytes(TestFiles.webmVoice()));
        Session responsible = staffSession("AUTHORITY", departmentIdByCode("WATER"));
        mvc.perform(get(voiceUrl).header(HttpHeaders.AUTHORIZATION, bearer(responsible.accessToken())))
                .andExpect(status().isOk());
        mvc.perform(get(voiceUrl).header(HttpHeaders.AUTHORIZATION, bearer(adminSession().accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    void unknownFileReturns404() throws Exception {
        mvc.perform(get("/api/v1/media/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
