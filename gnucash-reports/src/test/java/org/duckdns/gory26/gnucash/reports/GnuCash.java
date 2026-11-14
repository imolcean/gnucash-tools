package org.duckdns.gory26.gnucash.reports;

import org.duckdns.gory26.gnucash.book.SampleBook;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GnuCash 5.16's command line in one shared container, set up the way a user's desktop is after installing
 * the reports: {@code fincore-reports/} in the user data folder and a {@code config-user.scm} that loads it.
 * The folder also holds the saved report configurations the tests run by name, since {@code gnucash-cli}
 * cannot pass report options.
 * <p>
 * Books are {@link SampleBook} databases; the container reaches the Postgres container through the host.
 */
final class GnuCash {

    /** What GnuCash puts between the parts of a full account name here: the image has no preferences backend. */
    static final String SEPARATOR = ".";

    private static final String STAGING = "/opt/fincore";
    private static final String USER_DATA = "/home/gnucash/userdata";
    private static final String WORK = "/home/gnucash/work";
    private static final String BOOK_HOST = "host.testcontainers.internal";

    private static final GenericContainer<?> CONTAINER;
    private static final AtomicInteger FILES = new AtomicInteger();

    static {
        // Must happen before the container is created, and SampleBook must have started Postgres by then.
        Testcontainers.exposeHostPorts(SampleBook.port());
        CONTAINER = new GenericContainer<>(DockerImageName.parse(System.getProperty("gnucash.image")))
                .withAccessToHost(true)
                .withEnv("GNC_CONFIG_HOME", USER_DATA)
                .withEnv("GNC_DATA_HOME", USER_DATA)
                .withCopyFileToContainer(MountableFile.forClasspathResource("fincore-reports"),
                        STAGING + "/fincore-reports")
                .withCopyFileToContainer(MountableFile.forClasspathResource("gnucash-config"), STAGING)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(Path.of(System.getProperty("sankey.scheme.tests.dir"))),
                        STAGING + "/tests");
        CONTAINER.start(); // stopped by Testcontainers' reaper when the JVM exits
        // Copied files belong to root; GnuCash wants to own its user data folder.
        run("mkdir -p " + USER_DATA + " " + WORK + " && cp -r " + STAGING + "/. " + USER_DATA + "/");
    }

    private GnuCash() {
    }

    /**
     * Runs the report or saved report configuration called {@code name} on {@code database} and returns its HTML.
     */
    static Report report(String name, String database) {
        String file = WORK + "/report-" + FILES.incrementAndGet() + ".html";
        String book = "postgres://postgres:postgres@" + BOOK_HOST + ":" + SampleBook.port() + "/" + database;
        ExecResult result = exec("gnucash-cli", "--report", "run", "--name", name, "--output-file", file, book);
        ExecResult html = exec("cat", file);
        if(html.getExitCode() != 0) {
            throw new IllegalStateException("gnucash-cli wrote no report \"" + name + "\" (exit code "
                    + result.getExitCode() + "):\n" + result.getStdout() + result.getStderr());
        }
        return new Report(file, html.getStdout());
    }

    /** The report as headless Firefox paints it, as PNG bytes. */
    static byte[] screenshot(Report report, int width, int height) {
        String png = report.file().replace(".html", ".png");
        ExecResult result = exec("firefox", "--headless", "--screenshot", png,
                "--window-size=" + width + "," + height, "file://" + report.file());
        try {
            return CONTAINER.copyFileFromContainer(png, InputStream::readAllBytes);
        }
        catch(RuntimeException e) {
            throw new IllegalStateException("Firefox wrote no screenshot (exit code " + result.getExitCode()
                    + "):\n" + result.getStdout() + result.getStderr(), e);
        }
    }

    /** Runs a Guile test script from {@code src/test/scheme} on a report source file; the result is Guile's. */
    static ExecResult guileTest(String script, String source) {
        return exec("guile", "--no-auto-compile", STAGING + "/tests/" + script,
                USER_DATA + "/fincore-reports/" + source);
    }

    private static void run(String shell) {
        ExecResult result = exec("sh", "-c", shell);
        if(result.getExitCode() != 0) {
            throw new IllegalStateException(shell + " failed:\n" + result.getStdout() + result.getStderr());
        }
    }

    private static ExecResult exec(String... command) {
        try {
            return CONTAINER.execInContainer(command);
        }
        catch(IOException e) {
            throw new UncheckedIOException(e);
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
