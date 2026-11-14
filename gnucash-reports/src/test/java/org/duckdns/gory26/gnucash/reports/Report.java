package org.duckdns.gory26.gnucash.reports;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A report as {@code gnucash-cli} wrote it: {@code file} is its path in the container, {@code html} its content.
 */
record Report(String file, String html) {

    private static final Pattern SVG = Pattern.compile("<svg.*?</svg>", Pattern.DOTALL);

    /** The Sankey diagram, exactly as the report wrote it. */
    String svg() {
        Matcher matcher = SVG.matcher(html);
        if(!matcher.find()) {
            throw new AssertionError("the report has no diagram:\n" + text());
        }
        return matcher.group();
    }

    /** A total the diagram carries for programs, e.g. {@code data-net-income}. */
    BigDecimal data(String name) {
        Matcher matcher = Pattern.compile(" data-" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(svg());
        if(!matcher.find()) {
            throw new AssertionError("the diagram has no data-" + name);
        }
        return new BigDecimal(matcher.group(1));
    }

    /** Whether hovering over some node or ribbon shows exactly {@code text}. */
    boolean hasHoverText(String text) {
        return svg().contains("<title>" + escape(text) + "</title>");
    }

    /** Whether some node's hover text starts with {@code label}, i.e. a node of that name is drawn. */
    boolean hasNode(String label) {
        return svg().contains("<title>" + escape(label) + ": ");
    }

    /**
     * The amount printed after {@code label} in the report's text, as a standard report's table shows a total:
     * "Total Revenue €2,000.00".
     */
    BigDecimal amountAfter(String label) {
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "\\s*(-?)€([\\d,]+\\.\\d\\d)").matcher(text());
        if(!matcher.find()) {
            throw new AssertionError("no amount after \"" + label + "\" in:\n" + text());
        }
        return new BigDecimal(matcher.group(1) + matcher.group(2).replace(",", ""));
    }

    boolean mentions(String label) {
        return text().contains(label);
    }

    /** The report without the diagram and without markup. */
    private String text() {
        return SVG.matcher(html).replaceAll(" ")
                .replaceAll("(?s)<style.*?</style>", " ")
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
