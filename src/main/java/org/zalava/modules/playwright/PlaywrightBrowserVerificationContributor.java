package org.zalava.modules.playwright;

import java.util.List;
import java.util.Map;
import org.zalava.api.ZalavaVerificationContributor;
import org.zalava.api.ZalavaVerificationDescriptor;
import org.zalava.api.ZalavaVerificationStep;

final class PlaywrightBrowserVerificationContributor implements ZalavaVerificationContributor {
  @Override
  public List<ZalavaVerificationDescriptor> verifications() {
    return List.of(
        new ZalavaVerificationDescriptor(
            "web-browser",
            "playwright-browser",
            List.of("navigateTo", "getText", "closeBrowser"),
            List.of(
                ZalavaVerificationStep.toolInvocation(
                    "Open a stable page",
                    "playwright-browser",
                    "navigateTo",
                    true,
                    false,
                    Map.of(
                        "actorId",
                        "bootstrap-verification",
                        "arguments",
                        Map.of("url", "https://example.com"))),
                ZalavaVerificationStep.toolInvocation(
                    "Read the page heading",
                    "playwright-browser",
                    "getText",
                    false,
                    false,
                    Map.of(
                        "actorId",
                        "bootstrap-verification",
                        "arguments",
                        Map.of("selector", "h1"))),
                ZalavaVerificationStep.toolInvocation(
                    "Close the browser session",
                    "playwright-browser",
                    "closeBrowser",
                    true,
                    false,
                    Map.of("actorId", "bootstrap-verification", "arguments", Map.of())))));
  }
}
