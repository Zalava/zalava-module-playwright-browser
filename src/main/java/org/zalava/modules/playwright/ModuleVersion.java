package org.zalava.modules.playwright;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class ModuleVersion {

  static final String VALUE = load();

  private ModuleVersion() {}

  private static String load() {
    Properties properties = new Properties();
    try (InputStream stream = ModuleVersion.class.getResourceAsStream("/module.properties")) {
      if (stream == null) return "unknown";
      properties.load(stream);
      return properties.getProperty("module.version", "unknown");
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read module version", exception);
    }
  }
}
