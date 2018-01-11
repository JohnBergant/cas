package org.apereo.cas.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Misagh Moayyed
 * @since 4.1
 */
@RunWith(JUnit4.class)
public class GroovyScriptAttributeReleasePolicyTests {

    private static final File JSON_FILE = new File(FileUtils.getTempDirectoryPath(), "groovyScriptAttributeReleasePolicy.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void verifySerializeAGroovyScriptAttributeReleasePolicyToJson() throws IOException {
        final GroovyScriptAttributeReleasePolicy policyWritten = new GroovyScriptAttributeReleasePolicy();

        MAPPER.writeValue(JSON_FILE, policyWritten);

        final RegisteredServiceAttributeReleasePolicy policyRead = MAPPER.readValue(JSON_FILE, GroovyScriptAttributeReleasePolicy.class);

        assertEquals(policyWritten, policyRead);
    }
}
