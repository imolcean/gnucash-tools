package org.duckdns.gory26.gnucash.reports;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Guile tests of the drawing module ({@code src/test/scheme}) with the image's Guile, so that
 * {@code ./mvnw verify} covers them. The tests themselves are Scheme: the module has no GnuCash in it.
 */
@Tag("docker")
class SankeyLayoutTest {

    @Test
    void theGuileTestsOfTheDrawingModulePass() {
        ExecResult result = GnuCash.guileTest("sankey-svg-test.scm", "sankey-svg.scm");

        assertThat(result.getExitCode())
                .as("guile sankey-svg-test.scm:\n%s%s", result.getStdout(), result.getStderr())
                .isZero();
        assertThat(result.getStdout()).contains("# of expected passes").doesNotContain("FAIL");
    }
}
