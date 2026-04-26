package com.example.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class StartupUrlBannerTest {

    @Mock
    private Environment environment;

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void redirectStdout() {
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Test
    void printUrlBanner_skipsWhenDisabled() {
        new StartupUrlBanner(environment, false, "http://localhost:3000").printUrlBanner();
        assertThat(captured.toString()).isEmpty();
    }

    @Test
    void printUrlBanner_usesLocalServerPortWhenPresent() {
        when(environment.getProperty("server.port", "8080")).thenReturn("8080");
        when(environment.getProperty("local.server.port", "8080")).thenReturn("9000");
        new StartupUrlBanner(environment, true, "http://localhost:3000").printUrlBanner();
        String out = captured.toString();
        assertThat(out).contains("http://localhost:9000/");
        assertThat(out).contains("http://localhost:3000/payments/import");
    }

    @Test
    void printUrlBanner_fallsBackToServerPort() {
        when(environment.getProperty("server.port", "8080")).thenReturn("7070");
        when(environment.getProperty("local.server.port", "7070")).thenReturn("7070");
        new StartupUrlBanner(environment, true, "http://x:1").printUrlBanner();
        assertThat(captured.toString()).contains("http://localhost:7070/");
    }

    @Test
    void constructor_stripsTrailingSlashFromImportBase() {
        when(environment.getProperty("server.port", "8080")).thenReturn("8080");
        when(environment.getProperty("local.server.port", "8080")).thenReturn("8080");
        new StartupUrlBanner(environment, true, "http://localhost:3000/").printUrlBanner();
        String out = captured.toString();
        assertThat(out).contains("http://localhost:3000/payments/import");
        assertThat(out).doesNotContain("3000//");
    }
}
