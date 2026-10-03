package org.zalava.modules.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.nio.file.Files;
import java.nio.file.Path;

final class PlaywrightBrowserClient implements BrowserClient {

  private static final int MAX_TEXT_LENGTH = 10_000;
  private final boolean headless;
  private Playwright playwright;
  private Browser browser;
  private Page page;

  PlaywrightBrowserClient(boolean headless) {
    this.headless = headless;
  }

  @Override
  public synchronized String navigateTo(String url) {
    return execute(
        "navigateTo",
        () -> {
          Page current = page();
          current.navigate(url);
          current.waitForLoadState();
          return "Title: "
              + current.title()
              + "\n\nContent:\n"
              + truncate(current.innerText("body"));
        });
  }

  @Override
  public synchronized String clickElement(String selector) {
    return execute(
        "clickElement",
        () -> {
          Page current = page();
          current.click(selector);
          current.waitForLoadState();
          return "Clicked element: "
              + selector
              + "\nCurrent URL: "
              + current.url()
              + "\nTitle: "
              + current.title();
        });
  }

  @Override
  public synchronized String fillInput(String selector, String value) {
    return execute(
        "fillInput",
        () -> {
          page().fill(selector, value);
          return "Filled input " + selector + " with value.";
        });
  }

  @Override
  public synchronized String getText(String selector) {
    return execute("getText", () -> truncate(page().innerText(selector)));
  }

  @Override
  public synchronized String takeScreenshot() {
    return execute(
        "takeScreenshot",
        () -> {
          Path screenshot = Files.createTempFile("zalava-playwright-screenshot-", ".png");
          page().screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(true));
          return "Screenshot saved.";
        });
  }

  @Override
  public synchronized String evaluateJavaScript(String expression) {
    return execute(
        "evaluateJavaScript",
        () -> {
          Object result = page().evaluate(expression);
          return result == null ? "null" : result.toString();
        });
  }

  @Override
  public synchronized String waitForSelector(String selector, int timeoutMs) {
    return execute(
        "waitForSelector",
        () -> {
          page()
              .waitForSelector(
                  selector,
                  new Page.WaitForSelectorOptions()
                      .setState(WaitForSelectorState.VISIBLE)
                      .setTimeout(timeoutMs));
          return "Element " + selector + " is now visible.";
        });
  }

  @Override
  public synchronized String closeBrowser() {
    try {
      closeResources();
      return "Browser session closed.";
    } catch (RuntimeException ex) {
      throw new BrowserOperationException("closeBrowser", ex);
    }
  }

  private Page page() {
    if (playwright == null) {
      playwright = Playwright.create();
      browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    }
    if (page == null || page.isClosed()) {
      page = browser.newPage();
    }
    return page;
  }

  private void closeResources() {
    if (page != null && !page.isClosed()) page.close();
    page = null;
    if (browser != null && browser.isConnected()) browser.close();
    browser = null;
    if (playwright != null) playwright.close();
    playwright = null;
  }

  private static String truncate(String text) {
    if (text == null || text.length() <= MAX_TEXT_LENGTH) return text == null ? "" : text;
    return text.substring(0, MAX_TEXT_LENGTH) + "\n\n[Content truncated]";
  }

  private static String execute(String operation, ThrowingSupplier supplier) {
    try {
      return supplier.get();
    } catch (Exception ex) {
      throw new BrowserOperationException(operation, ex);
    }
  }

  @FunctionalInterface
  private interface ThrowingSupplier {
    String get() throws Exception;
  }
}
