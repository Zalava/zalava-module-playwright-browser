package org.zalava.modules.playwright;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.microsoft.playwright.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.zalava.*;
import tools.jackson.databind.json.JsonMapper;

class BrowserLifecycleTest {
  @Test
  void lazilyOpensReusesAndReopensPagesAndClosesResources() throws Exception {
    Playwright playwright = mock(Playwright.class);
    BrowserType type = mock(BrowserType.class);
    Browser browser = mock(Browser.class);
    Page page = mock(Page.class);
    when(playwright.chromium()).thenReturn(type);
    when(type.launch(any())).thenReturn(browser);
    when(browser.newPage()).thenReturn(page);
    when(browser.isConnected()).thenReturn(true);
    when(page.title()).thenReturn("Title");
    when(page.url()).thenReturn("https://example.com");
    when(page.innerText("body")).thenReturn("x".repeat(10001));
    when(page.innerText("empty")).thenReturn(null);
    when(page.innerText("small")).thenReturn("text");
    when(page.evaluate("value")).thenReturn(42);
    doAnswer(
            call -> {
              Files.deleteIfExists(((Page.ScreenshotOptions) call.getArgument(0)).path);
              return new byte[0];
            })
        .when(page)
        .screenshot(any(Page.ScreenshotOptions.class));
    try (var factory = mockStatic(Playwright.class)) {
      factory.when(Playwright::create).thenReturn(playwright);
      var client = new PlaywrightBrowserClient(true);
      assertThat(client.navigateTo("https://example.com")).contains("Title", "[Content truncated]");
      assertThat(client.clickElement("button")).contains("Clicked");
      assertThat(client.fillInput("input", "text")).contains("Filled");
      assertThat(client.getText("empty")).isEmpty();
      assertThat(client.getText("small")).isEqualTo("text");
      assertThat(client.takeScreenshot()).contains("Screenshot");
      assertThat(client.evaluateJavaScript("value")).isEqualTo("42");
      assertThat(client.evaluateJavaScript("null")).isEqualTo("null");
      assertThat(client.waitForSelector("input", 100)).contains("input");
      verify(browser, times(1)).newPage();
      when(page.isClosed()).thenReturn(true);
      client.getText("small");
      verify(browser, times(2)).newPage();
      when(page.isClosed()).thenReturn(false);
      client.closeBrowser();
      verify(page).close();
      verify(browser).close();
      verify(playwright).close();
      client.close();
      client.getText("small");
      factory.verify(Playwright::create, times(2));
      when(page.isClosed()).thenReturn(true);
      when(browser.isConnected()).thenReturn(false);
      client.close();
    }
  }

  @Test
  void reportsBrowserFailuresAndAllowsCleanupRetry() {
    Playwright playwright = mock(Playwright.class);
    Browser browser = mock(Browser.class);
    BrowserType type = mock(BrowserType.class);
    Page page = mock(Page.class);
    when(playwright.chromium()).thenReturn(type);
    when(type.launch(any())).thenReturn(browser);
    when(browser.newPage()).thenReturn(page);
    when(browser.isConnected()).thenReturn(true);
    when(page.innerText(anyString())).thenThrow(new IllegalStateException("page failure"));
    try (var factory = mockStatic(Playwright.class)) {
      factory.when(Playwright::create).thenReturn(playwright);
      var client = new PlaywrightBrowserClient(false);
      assertThatThrownBy(() -> client.getText("body"))
          .isInstanceOf(BrowserOperationException.class);
      doThrow(new IllegalStateException("close failure")).when(page).close();
      assertThatThrownBy(client::closeBrowser).isInstanceOf(BrowserOperationException.class);
      doNothing().when(page).close();
      client.close();
    }
  }

  @Test
  void exposesVerificationJourneyAndDispatchesAllOperations() {
    var module = new PlaywrightBrowserModule();
    var journey = module.verificationContributors().getFirst().verifications().getFirst();
    assertThat(journey.requiredTools()).contains("navigateTo", "getText", "closeBrowser");
    assertThat(journey.steps()).hasSize(3);
    BrowserClient client = mock(BrowserClient.class);
    when(client.navigateTo(anyString())).thenReturn("page");
    when(client.clickElement(anyString())).thenReturn("clicked");
    when(client.fillInput(anyString(), anyString())).thenReturn("filled");
    when(client.takeScreenshot()).thenReturn("screenshot");
    when(client.closeBrowser()).thenReturn("closed");
    when(client.evaluateJavaScript(anyString())).thenReturn("value");
    var provider = new PlaywrightBrowserProvider(client);
    var arguments =
        new JsonMapper()
            .createObjectNode()
            .put("url", "https://example.com")
            .put("selector", "input")
            .put("value", "text")
            .put("expression", "1");
    for (String operation :
        List.of(
            "navigateTo",
            "clickElement",
            "fillInput",
            "takeScreenshot",
            "evaluateJavaScript",
            "closeBrowser")) {
      assertThat(provider.callTool(operation, arguments, InvocationContext.system()).success())
          .isTrue();
    }
    verify(client).closeBrowser();
  }

  @Test
  void validatesProviderInputsAndMapsBrowserFailures() {
    BrowserClient client = mock(BrowserClient.class);
    var provider = new PlaywrightBrowserProvider(client);
    var json = new JsonMapper();
    for (String operation :
        List.of(
            "navigateTo", "clickElement", "fillInput", "evaluateJavaScript", "waitForSelector")) {
      assertThatThrownBy(
              () ->
                  provider.callTool(operation, json.createObjectNode(), InvocationContext.system()))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(
              () ->
                  provider.callTool(
                      operation,
                      json.createObjectNode()
                          .put("url", " ")
                          .put("selector", " ")
                          .put("expression", " "),
                      InvocationContext.system()))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(
            () -> provider.callTool("unknown", json.createObjectNode(), InvocationContext.system()))
        .isInstanceOf(IllegalArgumentException.class);
    when(client.getText(anyString()))
        .thenThrow(new BrowserOperationException("getText", new IllegalStateException("broken")));
    assertThat(
            provider
                .callTool("getText", json.createObjectNode(), InvocationContext.system())
                .success())
        .isFalse();
    when(client.waitForSelector("button", 5000)).thenReturn("ready");
    assertThat(
            provider
                .callTool(
                    "waitForSelector",
                    json.createObjectNode().put("selector", "button").put("timeoutMs", -1),
                    InvocationContext.system())
                .success())
        .isTrue();
    provider.close();
    verify(client).close();
  }
}
