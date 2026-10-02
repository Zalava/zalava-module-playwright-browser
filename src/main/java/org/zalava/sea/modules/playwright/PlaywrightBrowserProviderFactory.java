package org.zalava.modules.playwright;

import java.util.List;
import org.zalava.ProviderFactory;
import org.zalava.ProviderFactoryContext;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.ZalavaProvider;

final class PlaywrightBrowserProviderFactory implements ProviderFactory {

  private final BrowserClientFactory clientFactory;

  PlaywrightBrowserProviderFactory(BrowserClientFactory clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public ProviderFactoryDescriptor descriptor() {
    return new ProviderFactoryDescriptor(
        PlaywrightBrowserModule.FACTORY_ID,
        PlaywrightBrowserModule.MODULE_ID,
        "web-browser",
        "Playwright Browser Factory",
        "Creates provider-scoped Playwright browser sessions.");
  }

  @Override
  public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
    Object configured = context.configuration().get("headless");
    boolean headless = !(configured instanceof Boolean value) || value;
    return List.of(new PlaywrightBrowserProvider(clientFactory.create(headless)));
  }
}
