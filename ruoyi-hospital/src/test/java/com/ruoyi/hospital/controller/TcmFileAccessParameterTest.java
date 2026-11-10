package com.ruoyi.hospital.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.hospital.service.ITcmPatientFileService;
import com.ruoyi.hospital.util.HospitalFileStorage;
import com.ruoyi.hospital.util.SignedFileUrlService;

class TcmFileAccessParameterTest
{
    private static final String RESOURCE = "hospital-private/acceptance/private-fixture.txt";
    private static final String SIGNATURE = "private-signature";
    private static final String EXPIRY = "9999999999999";
    @TempDir Path directory;
    private SignedFileUrlService signer;
    private HospitalFileStorage storage;
    private ITcmPatientFileService files;
    private MockMvc mvc;

    @BeforeEach void setup()
    {
        signer = mock(SignedFileUrlService.class);
        storage = mock(HospitalFileStorage.class);
        files = mock(ITcmPatientFileService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TcmFileAccessController(signer, storage, files))
                .setControllerAdvice(new TcmExceptionHandler()).build();
    }

    private void assertBadRequest(MockHttpServletRequestBuilder request) throws Exception
    {
        mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"message\":\"Invalid or missing request parameter.\"}", true));
        verifyNoInteractions(signer, storage, files);
    }

    @Test void completelyMissingParametersReturn400WithoutFileAccess() throws Exception
    {
        assertBadRequest(get("/api/public/files/access"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"resource", "expires", "signature"})
    void eachMissingParameterReturns400WithoutEchoingValues(String missing) throws Exception
    {
        MockHttpServletRequestBuilder request = get("/api/public/files/access");
        if (!"resource".equals(missing)) request.param("resource", RESOURCE);
        if (!"expires".equals(missing)) request.param("expires", EXPIRY);
        if (!"signature".equals(missing)) request.param("signature", SIGNATURE);
        assertBadRequest(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"private-not-a-number", "", " ", "999999999999999999999999", "12.5"})
    void malformedExpiryReturns400WithoutEchoingValues(String expiry) throws Exception
    {
        assertBadRequest(get("/api/public/files/access").param("resource", RESOURCE)
                .param("expires", expiry).param("signature", SIGNATURE));
    }

    @Test void invalidSignatureStillReturns403WithoutAccessingFiles() throws Exception
    {
        mvc.perform(get("/api/public/files/access").param("resource", RESOURCE)
                .param("expires", EXPIRY).param("signature", SIGNATURE)).andExpect(status().isForbidden());
        verify(signer).isValid(RESOURCE, Long.parseLong(EXPIRY), SIGNATURE);
        verifyNoInteractions(storage, files);
    }

    @Test void validSignedRequestStillReturnsExactFileBytes() throws Exception
    {
        byte[] bytes = "Synthetic public-file acceptance".getBytes(StandardCharsets.UTF_8);
        Path file = directory.resolve("fixture.txt");
        Files.write(file, bytes);
        when(signer.isValid(RESOURCE, Long.parseLong(EXPIRY), SIGNATURE)).thenReturn(true);
        when(storage.resolve(RESOURCE)).thenReturn(file);
        mvc.perform(get("/api/public/files/access").param("resource", RESOURCE)
                .param("expires", EXPIRY).param("signature", SIGNATURE))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
        verify(storage, never()).restoreResource(anyString());
    }
}
