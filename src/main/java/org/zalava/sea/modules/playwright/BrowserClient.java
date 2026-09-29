package org.zalava.modules.playwright;

interface BrowserClient extends AutoCloseable {
    String navigateTo(String url);
    String clickElement(String selector);
    String fillInput(String selector, String value);
    String getText(String selector);
    String takeScreenshot();
    String evaluateJavaScript(String expression);
    String waitForSelector(String selector, int timeoutMs);
    String closeBrowser();

    @Override
    default void close() {
        closeBrowser();
    }
}
