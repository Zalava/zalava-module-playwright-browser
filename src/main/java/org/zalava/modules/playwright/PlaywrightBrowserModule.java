package org.zalava.modules.playwright;

import java.util.List;
import java.util.Map;
import org.zalava.api.ModuleConfigurationDescriptor;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaVerificationContributor;

/** External, provider-scoped browser automation module. */
public final class PlaywrightBrowserModule implements ZalavaModule {

  public static final String MODULE_ID = "zalava-module-playwright-browser";
  static final String FACTORY_ID = "playwright-browser-factory";

  private final List<ProviderFactory> factories;

  public PlaywrightBrowserModule() {
    this(PlaywrightBrowserClient::new);
  }

  PlaywrightBrowserModule(BrowserClientFactory clientFactory) {
    factories = List.of(new PlaywrightBrowserProviderFactory(clientFactory));
  }

  @Override
  public ModuleDescriptor descriptor() {
    return new ModuleDescriptor(
        MODULE_ID,
        ModuleVersion.VALUE,
        "Playwright Browser",
        "Provider-scoped browser automation backed by Playwright for Java.");
  }

  @Override
  public List<ProviderFactory> providerFactories() {
    return factories;
  }

  @Override
  public ModuleConfigurationDescriptor configuration() {
    return new ModuleConfigurationDescriptor(
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                FACTORY_ID,
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of("headless", Map.of("type", "boolean")),
                    "additionalProperties",
                    false)),
            "additionalProperties",
            false));
  }

  @Override
  public List<ZalavaVerificationContributor> verificationContributors() {
    return List.of(new PlaywrightBrowserVerificationContributor());
  }
}
