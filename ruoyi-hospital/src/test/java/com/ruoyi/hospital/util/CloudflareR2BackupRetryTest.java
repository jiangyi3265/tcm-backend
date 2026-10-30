package com.ruoyi.hospital.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class CloudflareR2BackupRetryTest
{
    @TempDir
    Path directory;

    private final ConnectionProbeService service = new ConnectionProbeService();
    private final byte[] content = new byte[] { 2, 4, 6, 8 };
    private Path file;

    @BeforeEach
    void setUp() throws Exception
    {
        file = directory.resolve("scan.png");
        Files.write(file, content);
    }

    @AfterEach
    void close()
    {
        service.release.countDown();
        service.shutdownBackgroundBackups();
    }

    @Test
    void transientEofRetriesWithANewConnectionAndPreservesTheOriginalBytes() throws Exception
    {
        service.writeFailures = 1;

        enqueue();
        awaitFinishedTask();

        assertEquals(2, service.connections.size());
        assertTrue(service.connections.stream().allMatch(connection -> connection.disconnected));
        assertArrayEquals(content, service.connections.get(1).written.toByteArray());
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test
    void repeatedIoFailureStopsAfterThreeAttemptsAndKeepsTheLocalFile() throws Exception
    {
        service.writeFailures = Integer.MAX_VALUE;

        enqueue();
        awaitFinishedTask();

        assertEquals(3, service.connections.size());
        assertTrue(service.connections.stream().allMatch(connection -> connection.disconnected));
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test
    void permanentHttpFailureDoesNotRetryAndClosesTheConnection() throws Exception
    {
        service.status = 403;

        enqueue();
        awaitFinishedTask();

        assertEquals(1, service.connections.size());
        assertTrue(service.connections.get(0).disconnected);
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test
    void deletedLocalFileIsNotRetriedAfterAnIoFailure() throws Exception
    {
        service.blockFirstWrite = true;
        service.writeFailures = Integer.MAX_VALUE;
        enqueue();
        assertTrue(service.started.await(2, TimeUnit.SECONDS));

        Files.delete(file);
        service.release.countDown();
        awaitFinishedTask();

        assertEquals(1, service.connections.size());
        assertTrue(service.connections.get(0).disconnected);
        assertFalse(Files.exists(file));
    }

    @Test
    void shutdownPreventsANewAttemptAfterTheInFlightWriteFails() throws Exception
    {
        service.blockFirstWrite = true;
        service.writeFailures = Integer.MAX_VALUE;
        enqueue();
        assertTrue(service.started.await(2, TimeUnit.SECONDS));

        executor().shutdown();
        service.release.countDown();
        service.shutdownBackgroundBackups();

        assertTrue(executor().isTerminated());
        assertEquals(1, service.connections.size());
        assertTrue(service.connections.get(0).disconnected);
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test
    void interruptedWorkerDoesNotRetry() throws Exception
    {
        service.blockFirstWrite = true;
        service.writeFailures = Integer.MAX_VALUE;
        enqueue();
        assertTrue(service.started.await(2, TimeUnit.SECONDS));

        service.worker.interrupt();
        awaitFinishedTask();

        assertEquals(1, service.connections.size());
        assertTrue(service.connections.get(0).disconnected);
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test
    void explicitSynchronousBackupStillMakesOnlyOneAttempt() throws Exception
    {
        service.writeFailures = Integer.MAX_VALUE;

        service.backupResourceQuietly("hospital-private/fixture.png", file, "image/png");

        assertEquals(1, service.connections.size());
        assertTrue(service.connections.get(0).disconnected);
        assertEquals(0, executor().getTaskCount());
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    private void enqueue()
    {
        service.backupResourceInBackground("hospital-private/fixture.png", file, "image/png");
    }

    private ThreadPoolExecutor executor()
    {
        return (ThreadPoolExecutor) ReflectionTestUtils.getField(service, "backgroundBackups");
    }

    private void awaitFinishedTask() throws Exception
    {
        ThreadPoolExecutor executor = executor();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (executor.getCompletedTaskCount() < 1 && System.nanoTime() < deadline)
        {
            Thread.sleep(10);
        }
        assertEquals(1, executor.getCompletedTaskCount());
    }

    private static class ConnectionProbeService extends CloudflareR2StorageService
    {
        private final List<ProbeConnection> connections = new CopyOnWriteArrayList<>();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile Thread worker;
        private boolean blockFirstWrite;
        private int writeFailures;
        private int status = 200;

        @Override
        public boolean isBackupEnabled()
        {
            return true;
        }

        @Override
        HttpURLConnection openSignedConnection(String method, String key, byte[] body, String contentType) throws Exception
        {
            ProbeConnection connection = new ProbeConnection(this, connections.size() + 1);
            connections.add(connection);
            return connection;
        }
    }

    private static class ProbeConnection extends HttpURLConnection
    {
        private final ConnectionProbeService owner;
        private final int attempt;
        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private volatile boolean disconnected;

        ProbeConnection(ConnectionProbeService owner, int attempt) throws Exception
        {
            super(new URL("https://fixture.invalid"));
            this.owner = owner;
            this.attempt = attempt;
        }

        @Override
        public OutputStream getOutputStream()
        {
            return new OutputStream()
            {
                @Override
                public void write(int value) throws IOException
                {
                    owner.worker = Thread.currentThread();
                    owner.started.countDown();
                    if (owner.blockFirstWrite && attempt == 1)
                    {
                        try
                        {
                            owner.release.await();
                        }
                        catch (InterruptedException e)
                        {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted fixture write", e);
                        }
                    }
                    if (attempt <= owner.writeFailures) throw new EOFException("Fixture peer disconnected");
                    written.write(value);
                }
            };
        }

        @Override
        public int getResponseCode() { return owner.status; }

        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }

        @Override
        public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }

        @Override
        public void disconnect() { disconnected = true; }

        @Override
        public boolean usingProxy() { return false; }

        @Override
        public void connect() { }
    }
}
