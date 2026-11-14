package org.duckdns.gory26.gnucash.reports;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each report as a reader sees it, compared with the baselines in {@code src/test/resources/baselines}: the
 * diagram's SVG text exactly, and the page as headless Firefox paints it within a small tolerance.
 * <p>
 * What was rendered is always written to {@code target/sankey}, so a failure can be looked at. After an intended
 * change, {@code ./mvnw -pl gnucash-reports -am verify -Dsankey.baselines.update=true} rewrites the baselines;
 * look at the new PNGs before committing them.
 */
class ScreenshotTest {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 900;

    /** A pixel counts as different when one of its colour channels is off by more than this (of 255). */
    private static final int CHANNEL_TOLERANCE = 48;
    /** Up to this many pixels in a thousand may differ: anti-aliasing varies a little between machines. */
    private static final int DIFFERENT_PIXELS_PER_MILLE = 2;

    private static final Path BASELINES = Path.of(System.getProperty("sankey.baselines.dir"));
    private static final Path OUTPUT = Path.of(System.getProperty("sankey.output.dir"));
    private static final boolean UPDATE = Boolean.getBoolean("sankey.baselines.update");

    private static final Map<String, Supplier<String>> BOOKS = Map.of(
            "sample", Books::sample,
            "rich", Books::rich,
            "loss", Books::loss,
            "many", Books::many);
    private static final Map<String, String> DATABASES = new ConcurrentHashMap<>();

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "income-expense,         Income Expense Sankey November,        sample",
            "income-expense-rich,    Income Expense Sankey November,        rich",
            "income-expense-merged,  Income Expense Sankey November merged, rich",
            "income-expense-loss,    Income Expense Sankey November,        loss",
            "income-expense-distinct, Income Expense Sankey November distinct, rich",
            // more second-level accounts than colours: the colours start over
            "income-expense-many-distinct, Income Expense Sankey November distinct, many",
            "income-expense-bare,    Income Expense Sankey November without labels, rich",
            // with trading flows, so that one picture shows them; they are hidden by default
            "cash-flow,              Cash Flow Sankey November with trading, sample",
            "cash-flow-rich,         Cash Flow Sankey November,             rich",
            // the exchange through Trading touches two selected accounts: the order must keep ribbons from crossing
            "cash-flow-rich-trading, Cash Flow Sankey November with trading, rich",
            "cash-flow-merged,       Cash Flow Sankey November merged,      rich",
            "cash-flow-distinct,     Cash Flow Sankey November distinct,    rich",
    })
    void looksLikeItsBaseline(String baseline, String reportName, String book) throws IOException {
        String database = DATABASES.computeIfAbsent(book, name -> BOOKS.get(name).get());
        Report report = GnuCash.report(reportName, database);
        byte[] svg = report.svg().replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        byte[] png = GnuCash.screenshot(report, WIDTH, HEIGHT);

        Files.createDirectories(OUTPUT);
        Files.write(OUTPUT.resolve(baseline + ".svg"), svg);
        Files.write(OUTPUT.resolve(baseline + ".png"), png);
        if(UPDATE) {
            Files.createDirectories(BASELINES);
            Files.write(BASELINES.resolve(baseline + ".svg"), svg);
            Files.write(BASELINES.resolve(baseline + ".png"), png);
            return;
        }

        assertThat(BASELINES.resolve(baseline + ".svg"))
                .as("baseline; create it with -Dsankey.baselines.update=true").exists();
        String expectedSvg = Files.readString(BASELINES.resolve(baseline + ".svg")).replace("\r\n", "\n");
        assertThat(new String(svg, StandardCharsets.UTF_8))
                .as("the diagram's SVG; the rendered one is %s", OUTPUT.resolve(baseline + ".svg"))
                .isEqualTo(expectedSvg);

        BufferedImage expected = ImageIO.read(BASELINES.resolve(baseline + ".png").toFile());
        BufferedImage actual = read(png);
        assertThat(actual.getWidth()).as("width").isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).as("height").isEqualTo(expected.getHeight());
        long pixels = (long) expected.getWidth() * expected.getHeight();
        assertThat(differentPixels(expected, actual) * 1000)
                .as("pixels that differ, times 1000, of %d; the rendered page is %s",
                        pixels, OUTPUT.resolve(baseline + ".png"))
                .isLessThanOrEqualTo(pixels * DIFFERENT_PIXELS_PER_MILLE);
    }

    private static long differentPixels(BufferedImage expected, BufferedImage actual) {
        long different = 0;
        for(int y = 0; y < expected.getHeight(); y++) {
            for(int x = 0; x < expected.getWidth(); x++) {
                if(differs(expected.getRGB(x, y), actual.getRGB(x, y))) {
                    different++;
                }
            }
        }
        return different;
    }

    private static boolean differs(int rgb, int otherRgb) {
        for(int shift = 0; shift <= 16; shift += 8) {
            if(Math.abs(((rgb >> shift) & 0xff) - ((otherRgb >> shift) & 0xff)) > CHANNEL_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private static BufferedImage read(byte[] png) {
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        }
        catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
