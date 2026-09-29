package org.zalava.modules.playwright;

import org.zalava.SeaVerificationContributor;
import org.zalava.SeaVerificationDescriptor;
import org.zalava.SeaVerificationStep;

import java.util.List;
import java.util.Map;

final class PlaywrightBrowserVerificationContributor implements SeaVerificationContributor {
    @Override
    public List<SeaVerificationDescriptor> verifications() {
        return List.of(new SeaVerificationDescriptor("web-browser", "playwright-browser",
                List.of("navigateTo", "getText", "closeBrowser"), List.of(
                        SeaVerificationStep.toolInvocation("Open a stable page", "playwright-browser", "navigateTo", true, false,
                                Map.of("actorId", "bootstrap-verification", "arguments", Map.of("url", "https://example.com"))),
                        SeaVerificationStep.toolInvocation("Read the page heading", "playwright-browser", "getText", false, false,
                                Map.of("actorId", "bootstrap-verification", "arguments", Map.of("selector", "h1"))),
                        SeaVerificationStep.toolInvocation("Close the browser session", "playwright-browser", "closeBrowser", true, false,
                                Map.of("actorId", "bootstrap-verification", "arguments", Map.of()))
                )));
    }
}
