package org.zalava.modules.playwright;

import org.zalava.InvocationContext;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.SeaOperationResult;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import org.zalava.SeaToolInputSchemas;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

final class PlaywrightBrowserProvider implements SeaProvider {

    private static final List<SeaToolDescriptor> TOOLS = List.of(
            tool("navigateTo", "Navigate to a URL and return title plus page text.", true, Map.of("url", SeaToolInputSchemas.string()), "url"),
            tool("clickElement", "Click an element matching a CSS selector.", true, Map.of("selector", SeaToolInputSchemas.string()), "selector"),
            tool("fillInput", "Fill an input field matching a CSS selector.", true, Map.of("selector", SeaToolInputSchemas.string(), "value", SeaToolInputSchemas.string()), "selector"),
            tool("getText", "Extract text content from elements matching a CSS selector.", false, Map.of("selector", SeaToolInputSchemas.string())),
            tool("takeScreenshot", "Take a full-page screenshot.", true, Map.of()),
            tool("evaluateJavaScript", "Evaluate JavaScript in the current page context.", true, Map.of("expression", SeaToolInputSchemas.string()), "expression"),
            tool("waitForSelector", "Wait for an element matching a CSS selector to become visible.", false, Map.of("selector", SeaToolInputSchemas.string(), "timeoutMs", SeaToolInputSchemas.integer()), "selector"),
            tool("closeBrowser", "Close the browser session and release resources.", true, Map.of())
    );

    private final BrowserClient client;
    private final ProviderDescriptor descriptor = new ProviderDescriptor(
            "playwright-browser", PlaywrightBrowserModule.MODULE_ID, "web-browser", "Playwright Browser",
            "Provider-scoped browser automation backed by Playwright for Java.", ModuleVersion.VALUE,
            ProviderCapabilities.toolsOnly(), List.of("sea_backed", "web-browser", "network", "browser-session"),
            Map.of("engine", "playwright")
    );

    PlaywrightBrowserProvider(BrowserClient client) {
        this.client = client;
    }

    @Override public ProviderDescriptor descriptor() { return descriptor; }
    @Override public ProviderCapabilities capabilities() { return descriptor.capabilities(); }
    @Override public List<SeaToolDescriptor> listTools() { return TOOLS; }

    @Override
    public SeaOperationResult callTool(String toolName, JsonNode arguments, InvocationContext context) {
        try {
            String result = switch (toolName) {
                case "navigateTo" -> client.navigateTo(required(arguments, "url"));
                case "clickElement" -> client.clickElement(required(arguments, "selector"));
                case "fillInput" -> client.fillInput(required(arguments, "selector"), arguments.path("value").asText(""));
                case "getText" -> client.getText(arguments.path("selector").asText("body"));
                case "takeScreenshot" -> client.takeScreenshot();
                case "evaluateJavaScript" -> client.evaluateJavaScript(required(arguments, "expression"));
                case "waitForSelector" -> client.waitForSelector(required(arguments, "selector"), timeout(arguments));
                case "closeBrowser" -> client.closeBrowser();
                default -> throw new IllegalArgumentException("Unknown browser tool: " + toolName);
            };
            return new SeaOperationResult(true, Map.of("operation", toolName, "result", result), metadata());
        } catch (BrowserOperationException ex) {
            return new SeaOperationResult(false, Map.of("code", "BROWSER_OPERATION_FAILED", "operation", toolName), metadata());
        }
    }

    @Override public void close() { client.close(); }

    private static SeaToolDescriptor tool(String name, String description, boolean sideEffecting,
                                          Map<String, Object> properties, String... required) {
        List<String> tags = sideEffecting
                ? List.of("sea_backed", "web-browser", "network", "browser-session", "mutating")
                : List.of("sea_backed", "web-browser", "network", "browser-session");
        return new SeaToolDescriptor(name, description, sideEffecting, tags,
                SeaToolInputSchemas.object(properties, required));
    }

    private static String required(JsonNode arguments, String fieldName) {
        String value = arguments.path(fieldName).asText(null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " is required");
        return value;
    }

    private static int timeout(JsonNode arguments) {
        int value = arguments.path("timeoutMs").asInt(5_000);
        return value > 0 ? value : 5_000;
    }

    private Map<String, Object> metadata() {
        return Map.of("providerId", descriptor.providerId(), "engine", "playwright");
    }
}
