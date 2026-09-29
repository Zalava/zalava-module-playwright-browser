package org.zalava.modules.playwright;

@FunctionalInterface
interface BrowserClientFactory {
    BrowserClient create(boolean headless);
}
