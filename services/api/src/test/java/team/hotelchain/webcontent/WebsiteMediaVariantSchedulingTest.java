package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import team.hotelchain.config.TimeConfiguration;
import team.hotelchain.payment.ReservationExpiryJob;
import team.hotelchain.payment.ReservationExpiryService;
import team.hotelchain.webcontent.storage.WebsiteMediaStorageGateway;

class WebsiteMediaVariantSchedulingTest {
    @TempDir Path storage;

    @Test
    void reservationExpiryRunsWhileMediaEncodingBlocksItsSingleSchedulerThread() throws Exception {
        Files.createFile(storage.resolve("source.png"));
        CountDownLatch encodingStarted = new CountDownLatch(1);
        CountDownLatch releaseEncoder = new CountDownLatch(1);
        CountDownLatch expiryDuringEncoding = new CountDownLatch(1);
        AtomicReference<Thread> encodingThread = new AtomicReference<>();
        AtomicReference<Thread> expiryThread = new AtomicReference<>();
        WebsiteMediaVariantService variants = mock(WebsiteMediaVariantService.class);
        WebsiteMediaVariantEncoder encoder = mock(WebsiteMediaVariantEncoder.class);
        WebsiteMediaStorageGateway mediaStorage = mock(WebsiteMediaStorageGateway.class);
        ReservationExpiryService expiry = mock(ReservationExpiryService.class);
        var claim = new WebsiteMediaVariantService.VariantClaim(
                UUID.randomUUID(), UUID.randomUUID(), 640, "source.png", 1, UUID.randomUUID());
        when(variants.claimNext()).thenReturn(Optional.of(claim)).thenReturn(Optional.empty());
        when(mediaStorage.materialize(any(String.class), any(Path.class)))
                .thenReturn(storage.resolve("source.png"));
        when(encoder.encode(any(Path.class), any(Path.class), anyInt())).thenAnswer(invocation -> {
            encodingThread.set(Thread.currentThread());
            encodingStarted.countDown();
            if (!releaseEncoder.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("encoder release timed out");
            return new WebsiteMediaVariantEncoder.Result("image/webp", 2048, 640, 360);
        });
        doAnswer(invocation -> {
            if (encodingStarted.getCount() == 0 && releaseEncoder.getCount() == 1) {
                expiryThread.set(Thread.currentThread());
                expiryDuringEncoding.countDown();
            }
            return 0;
        }).when(expiry).expireDue(50);

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(TimeConfiguration.class, WebsiteMediaVariantJob.class, ReservationExpiryJob.class)
                .withBean(WebsiteMediaVariantService.class, () -> variants)
                .withBean(WebsiteMediaVariantEncoder.class, () -> encoder)
                .withBean(WebsiteMediaStorageGateway.class, () -> mediaStorage)
                .withBean(ReservationExpiryService.class, () -> expiry)
                .withPropertyValues("reservation.expiry-job-enabled=true", "reservation.expiry-scan-delay=10ms",
                        "website.media.variant-scan-delay=10ms", "website.media.storage-dir=" + storage)
                .run(context -> {
                    try {
                        assertThat(context).hasNotFailed();
                        assertThat(encodingStarted.await(5, TimeUnit.SECONDS)).as("media encoding started").isTrue();
                        assertThat(expiryDuringEncoding.await(5, TimeUnit.SECONDS))
                                .as("reservation expiry executes before media encoding is released").isTrue();
                        assertThat(expiryThread.get()).isNotSameAs(encodingThread.get());
                        var mediaScheduler = context.getBean("websiteMediaVariantScheduler", ThreadPoolTaskScheduler.class);
                        assertThat(mediaScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isOne();
                        assertThat(context.getBean("taskScheduler")).isNotSameAs(mediaScheduler);
                    } finally {
                        releaseEncoder.countDown();
                    }
                });
    }
}
