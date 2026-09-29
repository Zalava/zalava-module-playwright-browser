package org.zalava.modules.playwright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.zalava.SeaModule;
import org.zalava.SeaOperationResult;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import org.zalava.testing.ConfigFixture;
import org.zalava.testing.ModuleContractKit;
import org.zalava.testing.ProviderFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exercises the real built module JAR at the stable {@code module-api} boundary through the released
 * contract kit. Provider behavior is asserted against an injected browser-client double so no real
 * browser is launched; host-owned resolution, permissions and persistence stay covered by SEA.
 */
class PlaywrightBrowserModuleTest {

    private static final String MODULE_ID = "zalava-module-playwright-browser";
    private static final String FACTORY_ID = "playwright-browser-factory";
    private static final String PROVIDER_ID = "playwright-browser";
    private static final String MODULE_CLASS = "org.zalava.modules.playwright.PlaywrightBrowserModule";
    private static final String CLIENT_FACTORY_TYPE = "org.zalava.modules.playwright.BrowserClientFactory";
    private static final String CLIENT_TYPE = "org.zalava.modules.playwright.BrowserClient";
    private static final String OPERATION_EXCEPTION_TYPE = "org.zalava.modules.playwright.BrowserOperationException";

    private ModuleContractKit kit;

    @BeforeEach
    void loadTheBuiltArtifact() {
        Path artifact = Path.of(System.getProperty("module.artifact"));
        String version = System.getProperty("module.version");
        kit = ModuleContractKit.load(artifact, List.of(), MODULE_ID, version);
    }

    @AfterEach
    void closeTheArtifact() throws Exception {
        if (kit != null) {
            kit.close();
        }
    }

    @Test
    void loadsTheModuleFromTheBuiltArtifact() {
        assertThat(kit.module().getClass().getClassLoader()).isNotSameAs(getClass().getClassLoader());
        assertThat(kit.module().getClass().getProtectionDomain().getCodeSource().getLocation().toString())
                .endsWith(".jar");
    }

    @Test
    void exposesTheModuleOwnedDescriptorAndConfigurationContract() {
        assertThat(kit.moduleId()).isEqualTo(MODULE_ID);
        assertThat(kit.version()).isEqualTo(System.getProperty("module.version"));
        assertThat(kit.module().descriptor().displayName()).isEqualTo("Playwright Browser");
        assertThat(kit.module().verificationContributors()).hasSize(1);

        Map<String, Object> schema = kit.module().configuration().jsonSchema();
        assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey(FACTORY_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> factory = (Map<String, Object>) properties.get(FACTORY_ID);
        assertThat(factory).containsEntry("type", "object").containsEntry("additionalProperties", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> factoryProperties = (Map<String, Object>) factory.get("properties");
        assertThat(factoryProperties).containsKey("headless");
    }

    @Test
    void createsTheConfiguredProviderAndDeclaresItsTools() {
        try (ProviderFixture providers = kit.providers(configuration(true))) {
            SeaProvider provider = providers.requireProvider(PROVIDER_ID);
            assertThat(provider.descriptor().moduleId()).isEqualTo(MODULE_ID);
            assertThat(provider.descriptor().providerType()).isEqualTo("web-browser");
            assertThat(provider.listTools().stream().map(SeaToolDescriptor::name)).containsExactly(
                    "navigateTo", "clickElement", "fillInput", "getText", "takeScreenshot",
                    "evaluateJavaScript", "waitForSelector", "closeBrowser");
            assertThat(provider.listTools()).filteredOn(tool -> tool.name().equals("getText")).singleElement()
                    .satisfies(tool -> assertThat(tool.sideEffecting()).isFalse());
            assertThat(provider.listTools()).filteredOn(tool -> tool.name().equals("fillInput")).singleElement()
                    .satisfies(tool -> assertThat(tool.sideEffecting()).isTrue());
            assertThat(provider.listTools()).filteredOn(tool -> tool.name().equals("navigateTo")).singleElement()
                    .satisfies(tool -> assertThat(tool.inputSchema()).containsEntry("required", List.of("url")));
        }
    }

    @Test
    void rejectsMissingAndUnknownToolsBeforeTouchingTheBrowser() {
        try (ProviderFixture providers = kit.providers(configuration(true))) {
            SeaProvider provider = providers.requireProvider(PROVIDER_ID);
            assertThatThrownBy(() -> providers.invoke(PROVIDER_ID, "navigateTo", arguments()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("url is required");
            assertThatThrownBy(() -> provider.callTool("unknown", arguments(), org.zalava.InvocationContext.system()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown browser tool: unknown");
        }
    }

    @Test
    void dispatchesOperationsAndClosesOnlyItsProviderSessionThroughTheInjectedClient() throws Exception {
        RecordingBrowserSessions sessions = new RecordingBrowserSessions(classLoader());
        try (ModuleContractKit injected = ModuleContractKit.of(injectedModule(sessions))) {
            try (ProviderFixture providers = injected.providers(configuration(false))) {
                SeaOperationResult navigate = providers.invoke(PROVIDER_ID, "navigateTo",
                        arguments().put("url", "https://example.com"));
                SeaOperationResult fill = providers.invoke(PROVIDER_ID, "fillInput",
                        arguments().put("selector", "#query").put("value", "sea"));
                SeaOperationResult wait = providers.invoke(PROVIDER_ID, "waitForSelector",
                        arguments().put("selector", "main").put("timeoutMs", 250));

                assertThat(navigate.success()).isTrue();
                assertThat(fill.content())
                        .isEqualTo(Map.of("operation", "fillInput", "result", "filled:#query=sea"));
                assertThat(wait.content())
                        .isEqualTo(Map.of("operation", "waitForSelector", "result", "waited:main:250"));
            }
        }
        assertThat(sessions.calls).containsExactly("navigateTo", "fillInput", "waitForSelector", "closeBrowser");
        assertThat(sessions.headless).containsExactly(false);
    }

    @Test
    void returnsStableFailureWithoutExposingBrowserDetails() throws Exception {
        RecordingBrowserSessions sessions = new RecordingBrowserSessions(classLoader());
        sessions.failNavigation = true;
        try (ModuleContractKit injected = ModuleContractKit.of(injectedModule(sessions))) {
            try (ProviderFixture providers = injected.providers(configuration(true))) {
                SeaOperationResult result = providers.invoke(PROVIDER_ID, "navigateTo",
                        arguments().put("url", "https://example.com"));

                assertThat(result.success()).isFalse();
                assertThat(result.content())
                        .isEqualTo(Map.of("code", "BROWSER_OPERATION_FAILED", "operation", "navigateTo"));
                assertThat(result.content().toString()).doesNotContain("secret");
            }
        }
    }

    private ClassLoader classLoader() {
        return kit.module().getClass().getClassLoader();
    }

    private SeaModule injectedModule(RecordingBrowserSessions sessions) throws Exception {
        ClassLoader loader = classLoader();
        Class<?> clientFactoryType = Class.forName(CLIENT_FACTORY_TYPE, true, loader);
        sessions.initialize(
                Class.forName(CLIENT_TYPE, true, loader),
                Class.forName(OPERATION_EXCEPTION_TYPE, true, loader));
        Object factory = Proxy.newProxyInstance(loader, new Class<?>[] {clientFactoryType}, sessions);
        Class<?> moduleType = Class.forName(MODULE_CLASS, true, loader);
        Constructor<?> constructor = moduleType.getDeclaredConstructor(clientFactoryType);
        constructor.setAccessible(true);
        return (SeaModule) constructor.newInstance(factory);
    }

    private static ConfigFixture configuration(boolean headless) {
        return ConfigFixture.empty().factoryConfiguration(MODULE_ID, FACTORY_ID, Map.of("headless", headless));
    }

    private static ObjectNode arguments() {
        return JsonNodeFactory.instance.objectNode();
    }

    /** Factory and client invocation handler backed by the module classloader's own SPI types. */
    private static final class RecordingBrowserSessions implements InvocationHandler {

        private final ClassLoader moduleClassLoader;
        private final List<String> calls = new ArrayList<>();
        private final List<Boolean> headless = new ArrayList<>();
        private boolean failNavigation;
        private Class<?> clientType;
        private Class<?> operationExceptionType;

        RecordingBrowserSessions(ClassLoader moduleClassLoader) {
            this.moduleClassLoader = moduleClassLoader;
        }

        void initialize(Class<?> clientType, Class<?> operationExceptionType) {
            this.clientType = clientType;
            this.operationExceptionType = operationExceptionType;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "create" -> {
                    headless.add((Boolean) args[0]);
                    yield Proxy.newProxyInstance(moduleClassLoader, new Class<?>[] {clientType}, this);
                }
                case "close" -> {
                    calls.add("closeBrowser");
                    yield null;
                }
                case "navigateTo" -> client("navigateTo", () -> "navigated:" + args[0]);
                case "clickElement" -> client("clickElement", () -> "clicked:" + args[0]);
                case "fillInput" -> client("fillInput", () -> "filled:" + args[0] + "=" + args[1]);
                case "getText" -> client("getText", () -> "text:" + args[0]);
                case "takeScreenshot" -> client("takeScreenshot", () -> "screenshot");
                case "evaluateJavaScript" -> client("evaluateJavaScript", () -> "evaluated:" + args[0]);
                case "waitForSelector" -> client("waitForSelector", () -> "waited:" + args[0] + ":" + args[1]);
                case "closeBrowser" -> client("closeBrowser", () -> "closed");
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "recording-browser-sessions";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private Object client(String name, Supplier<String> result) throws Throwable {
            calls.add(name);
            if (failNavigation && name.equals("navigateTo")) {
                Constructor<?> constructor =
                        operationExceptionType.getDeclaredConstructor(String.class, Throwable.class);
                constructor.setAccessible(true);
                throw (Throwable)
                        constructor.newInstance("navigateTo", new IllegalStateException("host-path:/secret"));
            }
            return result.get();
        }
    }
}
