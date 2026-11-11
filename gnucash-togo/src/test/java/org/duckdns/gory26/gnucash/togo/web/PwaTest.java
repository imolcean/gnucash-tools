package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.togo.AppTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The manifest and icons load before login, so the home-screen app can show its icon. */
class PwaTest extends AppTest {

    @Test
    void theManifestIsPublicAndNamesTheIcons() throws Exception {
        String manifest = mvc.perform(get("/manifest.webmanifest")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(manifest).contains("\"display\": \"standalone\"")
                .contains("/icons/icon-192.png").contains("/icons/icon-512.png")
                .doesNotContain("maskable");
    }

    @Test
    void theIconsArePublic() throws Exception {
        for(String icon : new String[]{"icon.svg", "icon-192.png", "icon-512.png", "apple-touch-icon.png", "favicon-32.png"}) {
            mvc.perform(get("/icons/" + icon)).andExpect(status().isOk());
        }
    }

    @Test
    void theLoginPageLinksTheManifestAndIcons() throws Exception {
        String page = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("rel=\"manifest\" href=\"/manifest.webmanifest\"")
                .contains("rel=\"apple-touch-icon\" href=\"/icons/apple-touch-icon.png\"")
                .contains("href=\"/icons/favicon-32.png\"");
    }

    @Test
    void stylesAndScriptsLoadOnTheLoginPage() throws Exception {
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/webjars/htmx.org/dist/htmx.min.js")).andExpect(status().isOk());
        mvc.perform(get("/webjars/picocss__pico/css/pico.min.css")).andExpect(status().isOk());
    }
}
