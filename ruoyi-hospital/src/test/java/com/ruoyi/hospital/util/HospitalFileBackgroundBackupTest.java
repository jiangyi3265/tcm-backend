package com.ruoyi.hospital.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.config.RuoYiConfig;

class HospitalFileBackgroundBackupTest
{
    @TempDir
    Path temporaryDirectory;

    private String previousProfile;
    private ProbeBackupService backup;
    private final HospitalFileStorage storage = new HospitalFileStorage();
    private final byte[] content = new byte[] { 1, 3, 5, 7 };

    @BeforeEach
    void setUp()
    {
        previousProfile = RuoYiConfig.getProfile();
        new RuoYiConfig().setProfile(temporaryDirectory.resolve("uploads").toString());
        backup = new ProbeBackupService();
        ReflectionTestUtils.setField(storage, "r2StorageService", backup);
    }

    @AfterEach
    void tearDown()
    {
        backup.release.countDown();
        backup.shutdownBackgroundBackups();
        new RuoYiConfig().setProfile(previousProfile);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void uploadReturnsWithCompleteLocalFileWhileRemoteBackupIsBlocked(boolean patientFile) throws Exception
    {
        String resource = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> store(patientFile));

        assertArrayEquals(content, Files.readAllBytes(storage.resolve(resource)));
        assertTrue(backup.started.await(2, TimeUnit.SECONDS));
        assertEquals(1, backup.release.getCount(), "Remote backup is still blocked when upload returns");
        assertEquals(storage.resolve(resource), backup.paths.get(0));
        assertTrue(backup.threads.get(0).startsWith("r2-file-backup-"));
    }

    @Test
    void fullQueueNeverRunsRemoteBackupOnTheRequestThreadOrLosesTheLocalFile() throws Exception
    {
        Path original = temporaryDirectory.resolve("existing.png");
        Files.write(original, content);
        // Occupy both workers and all pending slots with paths, without loading image bytes into the queue.
        for (int index = 0; index < 130; index++)
        {
            backup.backupResourceInBackground("hospital-private/existing.png", original, "image/png");
        }
        ThreadPoolExecutor executor = executor();
        assertEquals(128, executor.getQueue().size());
        assertEquals(2, executor.getMaximumPoolSize());
        assertTrue(executor.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy);

        String resource = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> store(true));

        assertArrayEquals(content, Files.readAllBytes(storage.resolve(resource)));
        assertEquals(128, executor.getQueue().size());
        assertFalse(backup.paths.contains(storage.resolve(resource)));
        assertTrue(backup.threads.stream().allMatch(name -> name.startsWith("r2-file-backup-")));
    }

    @Test
    void asynchronousFailureKeepsTheLocalFileForTheScheduledScan() throws Exception
    {
        backup.fail = true;
        backup.release.countDown();

        String resource = store(true);

        assertTrue(backup.finished.await(2, TimeUnit.SECONDS));
        assertArrayEquals(content, Files.readAllBytes(storage.resolve(resource)));
    }

    @Test
    void disabledRemoteBackupDoesNotQueueOrStartAWorker() throws Exception
    {
        backup.enabled = false;

        String resource = store(true);

        assertArrayEquals(content, Files.readAllBytes(storage.resolve(resource)));
        assertEquals(0, executor().getTaskCount());
        assertEquals(0, executor().getPoolSize());
        assertTrue(backup.paths.isEmpty());
    }

    @Test
    void shutdownDrainsFinishedTasksAndRejectsFurtherWorkWithoutDeletingLocalFiles() throws Exception
    {
        backup.release.countDown();
        String resource = store(true);
        assertTrue(backup.finished.await(2, TimeUnit.SECONDS));

        backup.shutdownBackgroundBackups();

        assertTrue(executor().isShutdown());
        assertTrue(executor().isTerminated());
        String nextResource = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> store(true));
        assertArrayEquals(content, Files.readAllBytes(storage.resolve(resource)));
        assertArrayEquals(content, Files.readAllBytes(storage.resolve(nextResource)));
        assertEquals(1, backup.paths.size());
    }

    @Test
    void explicitBackupRemainsSynchronousForExistingCallers() throws Exception
    {
        backup.enabled = false;
        String resource = store(true);
        backup.enabled = true;
        backup.release.countDown();

        storage.backupResource(resource);

        assertEquals(Thread.currentThread().getName(), backup.threads.get(0));
        assertEquals(0, executor().getTaskCount());
    }

    private String store(boolean patientFile) throws Exception
    {
        MockMultipartFile file = new MockMultipartFile("file", "scan.png", "image/png", content);
        return patientFile
                ? storage.storePatientFile(file, "Fixture Patient", "document", "2026-10-30")
                : storage.store(file, "document");
    }

    private ThreadPoolExecutor executor()
    {
        return (ThreadPoolExecutor) ReflectionTestUtils.getField(backup, "backgroundBackups");
    }

    private static class ProbeBackupService extends CloudflareR2StorageService
    {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);
        private final List<Path> paths = new CopyOnWriteArrayList<>();
        private final List<String> threads = new CopyOnWriteArrayList<>();
        private boolean enabled = true;
        private boolean fail;

        @Override
        public boolean isBackupEnabled()
        {
            return enabled;
        }

        @Override
        void uploadObject(String resource, Path source, String contentType)
        {
            paths.add(source);
            threads.add(Thread.currentThread().getName());
            started.countDown();
            try
            {
                release.await();
                if (fail) throw new IllegalStateException("Fixture remote failure");
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            finally
            {
                finished.countDown();
            }
        }
    }
}
