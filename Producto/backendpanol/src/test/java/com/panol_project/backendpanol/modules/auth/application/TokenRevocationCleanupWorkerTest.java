package com.panol_project.backendpanol.modules.auth.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.panol_project.backendpanol.modules.auth.domain.TokenRevocationPort;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class TokenRevocationCleanupWorkerTest {

    @Mock
    private TokenRevocationPort tokenRevocationPort;

    @Test
    void purgeExpiredRevocationsDebeDelegarYLoggearCuandoEliminaFilas() {
        when(tokenRevocationPort.deleteExpiredRevocations(any(OffsetDateTime.class), eq(250))).thenReturn(3);

        TokenRevocationCleanupWorker worker = new TokenRevocationCleanupWorker(tokenRevocationPort, 250);
        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            worker.purgeExpiredRevocations();

            verify(tokenRevocationPort).deleteExpiredRevocations(any(OffsetDateTime.class), eq(250));
            assertTrue(appender.list.stream().anyMatch(event ->
                    event.getLevel() == Level.INFO &&
                            event.getFormattedMessage().contains("token_revocation_cleanup deleted=3 batch_size=250")));
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void purgeExpiredRevocationsConBatchInvalidoDebeNormalizarA500YSinRuidoSiNoHayFilas() {
        when(tokenRevocationPort.deleteExpiredRevocations(any(OffsetDateTime.class), eq(500))).thenReturn(0);

        TokenRevocationCleanupWorker worker = new TokenRevocationCleanupWorker(tokenRevocationPort, 0);
        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            worker.purgeExpiredRevocations();

            verify(tokenRevocationPort).deleteExpiredRevocations(any(OffsetDateTime.class), eq(500));
            assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.INFO));
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void purgeExpiredRevocationsDebeCapturarErroresDelRepositorio() {
        when(tokenRevocationPort.deleteExpiredRevocations(any(OffsetDateTime.class), eq(500)))
                .thenThrow(new IllegalStateException("boom"));

        TokenRevocationCleanupWorker worker = new TokenRevocationCleanupWorker(tokenRevocationPort, 500);
        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            assertDoesNotThrow(worker::purgeExpiredRevocations);

            verify(tokenRevocationPort).deleteExpiredRevocations(any(OffsetDateTime.class), eq(500));
            assertTrue(appender.list.stream().anyMatch(event ->
                    event.getLevel() == Level.ERROR &&
                            event.getFormattedMessage().contains("token_revocation_cleanup_failed batch_size=500")));
        } finally {
            detachAppender(appender);
        }
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(TokenRevocationCleanupWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(TokenRevocationCleanupWorker.class);
        logger.detachAppender(appender);
    }
}
