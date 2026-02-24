// AimSingleFileApp.java
//
// Single-file BOOTSTRAPPER that creates a full runnable “program-like” element picker
// into ./aim_pick_program/, downloads Maven Wrapper automatically, builds, and runs it.
//
// FIXES INCLUDED (your last issues):
// 1) Selection profile LOAD now calls aimLoadSelectionProfile("name") correctly (string, not array)
// 2) Selection profile items are HIGHLIGHTED on load (purple dashed outline)
// 3) Selection profile items are EDITABLE + DELETABLE in the UI, and delete AUTO-SAVES to disk
// 4) After Install export, it OPENS the output folder automatically (Explorer/Finder/xdg-open)
//
// Usage (Windows PowerShell):
//   cd C:\Users\aimpa\Documents
//   & "C:\Program Files\Java\jdk-21.0.10\bin\javac.exe" .\AimSingleFileApp.java
//   & "C:\Program Files\Java\jdk-21.0.10\bin\java.exe" AimSingleFileApp
//
// Optional:
//   & "C:\Program Files\Java\jdk-21.0.10\bin\java.exe" AimSingleFileApp "https://example.com" --profile default
//
// NOTE: Save as AimSingleFileApp.java (do not paste into PowerShell)

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public class AimSingleFileApp {

  public static void main(String[] args) throws Exception {
    String url = firstNonFlagArg(args);
    boolean headless = hasFlag(args, "--headless");
    boolean video = hasFlag(args, "--video");
    String profile = argValue(args, "--profile", "");

    Path root = Paths.get("aim_pick_program").toAbsolutePath();
    Path srcJava = root.resolve("src/main/java");
    Path srcRes  = root.resolve("src/main/resources");
    Path mw      = root.resolve(".mvn/wrapper");
    Path profilesDir = root.resolve("profiles");
    Path selDir = profilesDir.resolve("selection_profiles");

    Files.createDirectories(srcJava);
    Files.createDirectories(srcRes);
    Files.createDirectories(mw);
    Files.createDirectories(profilesDir);
    Files.createDirectories(selDir);

    writeFile(root.resolve("pom.xml"), pomXml());

    String ap = aimPickerProgramJavaNoTextBlocks();
    writeFile(srcJava.resolve("AimPickerProgram.java"), ap);

    writeFile(srcRes.resolve("picker.js"), pickerJs());
    writeFile(srcRes.resolve("viewer_template.html"), viewerTemplateHtml());

    writeFileIfMissing(profilesDir.resolve("default.properties"), defaultProfileProperties());
    writeFileIfMissing(profilesDir.resolve("stealth.properties"), stealthProfileProperties());

    writeFileIfMissing(profilesDir.resolve("url_profiles.json"),
      "{\n" +
      "  \"profiles\": [\n" +
      "    {\"name\": \"Example\", \"url\": \"https://example.com\"}\n" +
      "  ]\n" +
      "}\n"
    );

    writeFileIfMissing(selDir.resolve("sample.json"),
      "{\n" +
      "  \"name\": \"sample\",\n" +
      "  \"createdAt\": \"\",\n" +
      "  \"notes\": \"Replace selectors with your own.\",\n" +
      "  \"items\": [\n" +
      "    {\"selector\": \"h1\", \"tag\": \"h1\", \"kind\": \"element\", \"text\": \"\"}\n" +
      "  ]\n" +
      "}\n"
    );

    writeFile(mw.resolve("maven-wrapper.properties"),
      "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.12/apache-maven-3.9.12-bin.zip\n"
    );

    downloadIfMissing(root.resolve("mvnw.cmd"),
      "https://raw.githubusercontent.com/takari/maven-wrapper/master/mvnw.cmd");
    downloadIfMissing(root.resolve("mvnw"),
      "https://raw.githubusercontent.com/takari/maven-wrapper/master/mvnw");
    downloadIfMissing(mw.resolve("maven-wrapper.jar"),
      "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar");

    try { root.resolve("mvnw").toFile().setExecutable(true); } catch (Exception ignored) {}

    System.out.println("Project ready: " + root);
    System.out.println("Building with Maven Wrapper...");
    run(root, isWindows()
      ? new String[]{ "cmd", "/c", "mvnw.cmd", "-q", "-DskipTests", "package" }
      : new String[]{ "./mvnw", "-q", "-DskipTests", "package" }
    );

    System.out.println("Running program...");
    List<String> cmd = new ArrayList<>();
    cmd.add(javaBin());
    cmd.add("-jar");
    cmd.add(root.resolve("target/aim-pick-program-1.0.0.jar").toString());

    if (url != null && !url.isBlank()) cmd.add(url);
    if (profile != null && !profile.isBlank()) { cmd.add("--profile"); cmd.add(profile); }
    if (video) cmd.add("--video");
    if (headless) cmd.add("--headless");

    run(root, cmd.toArray(new String[0]));
  }

  // ---------- helpers ----------

  private static boolean hasFlag(String[] args, String flag) {
    for (String a : args) if (flag.equalsIgnoreCase(a)) return true;
    return false;
  }

  private static String argValue(String[] args, String key, String def) {
    for (int i = 0; i < args.length; i++) {
      if (key.equalsIgnoreCase(args[i]) && i + 1 < args.length) return args[i + 1];
    }
    return def;
  }

  private static String firstNonFlagArg(String[] args) {
    if (args == null) return null;
    for (int i = 0; i < args.length; i++) {
      String a = String.valueOf(args[i]);
      if (a.startsWith("--")) {
        if (("--profile".equalsIgnoreCase(a)) && i + 1 < args.length) i++;
        continue;
      }
      if (!a.trim().isEmpty()) return a.trim();
    }
    return null;
  }

  private static void writeFile(Path p, String s) throws IOException {
    Files.createDirectories(p.getParent());
    Files.writeString(p, s, StandardCharsets.UTF_8);
  }

  private static void writeFileIfMissing(Path p, String s) throws IOException {
    if (Files.exists(p) && Files.size(p) > 0) return;
    writeFile(p, s);
  }

  private static void downloadIfMissing(Path out, String url) throws Exception {
    if (Files.exists(out) && Files.size(out) > 0) return;
    System.out.println("Downloading: " + url);
    HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(30))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
      .header("User-Agent", "AimSingleFileApp/1.0")
      .GET()
      .build();
    HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new IOException("Download failed HTTP " + resp.statusCode() + " for " + url);
    }
    Files.write(out, resp.body());
  }

  private static void run(Path workdir, String[] cmd) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(workdir.toFile());
    pb.redirectErrorStream(true);
    Process p = pb.start();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = br.readLine()) != null) System.out.println(line);
    }
    int code = p.waitFor();
    if (code != 0) throw new RuntimeException("Command failed (" + code + "): " + String.join(" ", cmd));
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static String javaBin() {
    String home = System.getProperty("java.home");
    Path j = Paths.get(home, "bin", isWindows() ? "java.exe" : "java");
    return Files.exists(j) ? j.toString() : "java";
  }

  // ---------- generated project files ----------

  private static String pomXml() {
    return String.join("\n",
      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"",
      "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
      "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">",
      "  <modelVersion>4.0.0</modelVersion>",
      "  <groupId>com.aim</groupId>",
      "  <artifactId>aim-pick-program</artifactId>",
      "  <version>1.0.0</version>",
      "  <properties>",
      "    <maven.compiler.source>21</maven.compiler.source>",
      "    <maven.compiler.target>21</maven.compiler.target>",
      "  </properties>",
      "  <dependencies>",
      "    <dependency>",
      "      <groupId>com.microsoft.playwright</groupId>",
      "      <artifactId>playwright</artifactId>",
      "      <version>1.46.0</version>",
      "    </dependency>",
      "    <dependency>",
      "      <groupId>com.fasterxml.jackson.core</groupId>",
      "      <artifactId>jackson-databind</artifactId>",
      "      <version>2.17.2</version>",
      "    </dependency>",
      "  </dependencies>",
      "  <build>",
      "    <plugins>",
      "      <plugin>",
      "        <groupId>org.apache.maven.plugins</groupId>",
      "        <artifactId>maven-shade-plugin</artifactId>",
      "        <version>3.6.0</version>",
      "        <executions>",
      "          <execution>",
      "            <phase>package</phase>",
      "            <goals><goal>shade</goal></goals>",
      "            <configuration>",
      "              <transformers>",
      "                <transformer implementation=\"org.apache.maven.plugins.shade.resource.ManifestResourceTransformer\">",
      "                  <mainClass>AimPickerProgram</mainClass>",
      "                </transformer>",
      "              </transformers>",
      "            </configuration>",
      "          </execution>",
      "        </executions>",
      "      </plugin>",
      "    </plugins>",
      "  </build>",
      "</project>",
      ""
    );
  }

  private static String defaultProfileProperties() {
    return String.join("\n",
      "viewportWidth=1400",
      "viewportHeight=900",
      "userAgent=",
      "locale=",
      "timezoneId=",
      "storageStatePath=",
      "extraChromiumArgs=",
      ""
    );
  }

  private static String stealthProfileProperties() {
    return String.join("\n",
      "viewportWidth=1400",
      "viewportHeight=900",
      "extraChromiumArgs=--disable-blink-features=AutomationControlled,--disable-features=IsolateOrigins,site-per-process",
      ""
    );
  }

  private static String aimPickerProgramJavaNoTextBlocks() {
    List<String> L = new ArrayList<>();

    L.add("import com.microsoft.playwright.*;");
    L.add("import com.microsoft.playwright.options.*;");
    L.add("import com.fasterxml.jackson.databind.*;");
    L.add("import com.fasterxml.jackson.databind.node.*;");
    L.add("");
    L.add("import java.io.*;");
    L.add("import java.net.URI;");
    L.add("import java.nio.charset.StandardCharsets;");
    L.add("import java.nio.file.*;");
    L.add("import java.time.OffsetDateTime;");
    L.add("import java.time.format.DateTimeFormatter;");
    L.add("import java.util.*;");
    L.add("import java.util.List;");
    L.add("");
    L.add("public class AimPickerProgram {");
    L.add("  private static final ObjectMapper OM = new ObjectMapper();");
    L.add("");
    L.add("  public static void main(String[] args) throws Exception {");
    L.add("    String startUrl = firstNonFlagArg(args);");
    L.add("    boolean video = hasFlag(args, \"--video\");");
    L.add("    boolean headless = hasFlag(args, \"--headless\");");
    L.add("    String profileName = argValue(args, \"--profile\", \"default\");");
    L.add("");
    L.add("    if (startUrl == null || startUrl.isBlank()) {");
    L.add("      ArrayNode ups = loadUrlProfiles();");
    L.add("      if (ups.size() > 0) startUrl = ups.get(0).path(\"url\").asText(\"https://example.com\");");
    L.add("      else startUrl = \"https://example.com\";");
    L.add("    }");
    L.add("");
    L.add("    runInteractive(startUrl.trim(), profileName.trim(), video, headless);");
    L.add("  }");
    L.add("");
    L.add("  private static void runInteractive(String startUrl, String profileName, boolean video, boolean headless) throws Exception {");
    L.add("    Profile prof = Profile.load(profileName);");
    L.add("    String ts = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(\"yyyyMMdd_HHmmss\"));");
    L.add("    Path outDir = Paths.get(\"aim_capture_\"+ts).toAbsolutePath();");
    L.add("    Files.createDirectories(outDir);");
    L.add("");
    L.add("    String pickerJs = readResourceText(\"/picker.js\");");
    L.add("    String viewerTemplate = readResourceText(\"/viewer_template.html\");");
    L.add("");
    L.add("    try (Playwright pw = Playwright.create()) {");
    L.add("      BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions().setHeadless(headless);");
    L.add("      java.util.List<String> argsList = new ArrayList<>();");
    L.add("      if (!prof.extraChromiumArgs.isEmpty()) argsList.addAll(prof.extraChromiumArgs);");
    L.add("      else argsList.add(\"--disable-blink-features=AutomationControlled\");");
    L.add("      launch.setArgs(argsList);");
    L.add("");
    L.add("      Browser browser = pw.chromium().launch(launch);");
    L.add("");
    L.add("      Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions().setViewportSize(prof.viewportW, prof.viewportH);");
    L.add("      if (!prof.userAgent.isBlank()) ctxOpts.setUserAgent(prof.userAgent);");
    L.add("      if (!prof.locale.isBlank()) ctxOpts.setLocale(prof.locale);");
    L.add("      if (!prof.timezoneId.isBlank()) ctxOpts.setTimezoneId(prof.timezoneId);");
    L.add("      if (!prof.storageStatePath.isBlank()) {");
    L.add("        Path ss = Paths.get(prof.storageStatePath);");
    L.add("        if (!ss.isAbsolute()) ss = Paths.get(\"\").toAbsolutePath().resolve(ss).normalize();");
    L.add("        if (Files.exists(ss)) ctxOpts.setStorageStatePath(ss);");
    L.add("      }");
    L.add("      if (video) ctxOpts.setRecordVideoDir(outDir.resolve(\"video\")).setRecordVideoSize(prof.viewportW, prof.viewportH);");
    L.add("");
    L.add("      BrowserContext ctx = browser.newContext(ctxOpts);");
    L.add("      ctx.addInitScript(pickerJs);");
    L.add("");
    L.add("      Page page = ctx.newPage();");
    L.add("      System.out.println(\"Output:   \" + outDir);");
    L.add("      System.out.println(\"Profile:  \" + profileName);");
    L.add("      System.out.println(\"Navigate: \" + startUrl);");
    L.add("      navigateWithRetry(page, startUrl);");
    L.add("");
    L.add("      page.exposeFunction(\"aimGetConfig\", (Object[] a) -> {");
    L.add("        try {");
    L.add("          ObjectNode cfg = OM.createObjectNode();");
    L.add("          ArrayNode bps = cfg.putArray(\"browserProfiles\");");
    L.add("          for (String bp : listBrowserProfiles()) bps.add(bp);");
    L.add("          cfg.put(\"currentBrowserProfile\", profileName);");
    L.add("          cfg.set(\"urlProfiles\", loadUrlProfiles());");
    L.add("          cfg.put(\"currentUrl\", page.url());");
    L.add("          ArrayNode sp = cfg.putArray(\"selectionProfiles\");");
    L.add("          for (String s : listSelectionProfiles()) sp.add(s);");
    L.add("          return OM.writeValueAsString(cfg);");
    L.add("        } catch (Exception e) {");
    L.add("          return \"{\\\"error\\\":\\\"\" + escJson(e.toString()) + \"\\\"}\";");
    L.add("        }");
    L.add("      });");
    L.add("");
    L.add("      page.exposeFunction(\"aimSaveUrlProfiles\", (Object[] a) -> {");
    L.add("        try {");
    L.add("          String json = (a != null && a.length > 0) ? String.valueOf(a[0]) : \"\";");
    L.add("          JsonNode root = OM.readTree(json);");
    L.add("          JsonNode profiles = root.path(\"profiles\");");
    L.add("          if (!profiles.isArray()) return \"ERR: profiles must be array\";");
    L.add("          ObjectNode out = OM.createObjectNode();");
    L.add("          out.set(\"profiles\", profiles);");
    L.add("          Path p = Paths.get(\"profiles\",\"url_profiles.json\");");
    L.add("          Files.createDirectories(p.getParent());");
    L.add("          OM.writerWithDefaultPrettyPrinter().writeValue(p.toFile(), out);");
    L.add("          return Files.exists(p) ? \"OK\" : \"ERR: write failed\";");
    L.add("        } catch (Exception e) {");
    L.add("          return \"ERR: \" + e;");
    L.add("        }");
    L.add("      });");
    L.add("");
    L.add("      page.exposeFunction(\"aimSaveBrowserProfile\", (Object[] a) -> {");
    L.add("        try {");
    L.add("          String json = (a != null && a.length > 0) ? String.valueOf(a[0]) : \"\";");
    L.add("          JsonNode root = OM.readTree(json);");
    L.add("          String name = root.path(\"name\").asText(\"\").trim();");
    L.add("          String content = root.path(\"content\").asText(\"\");");
    L.add("          if (name.isBlank()) return \"ERR: missing name\";");
    L.add("          if (!name.matches(\"[A-Za-z0-9._-]{1,80}\")) return \"ERR: invalid name (use letters/numbers/._-)\";");
    L.add("          Path p = Paths.get(\"profiles\", name + \".properties\");");
    L.add("          Files.createDirectories(p.getParent());");
    L.add("          Files.writeString(p, content.replace(\"\\r\\n\", \"\\n\"), StandardCharsets.UTF_8);");
    L.add("          if (!Files.exists(p)) return \"ERR: file not created\";");
    L.add("          java.util.List<String> now = listBrowserProfiles();");
    L.add("          if (!now.contains(name)) return \"ERR: saved but not visible in list (unexpected)\";");
    L.add("          long sz = Files.size(p);");
    L.add("          return \"OK: saved profiles/\" + name + \".properties (\" + sz + \" bytes)\";");
    L.add("        } catch (Exception e) {");
    L.add("          return \"ERR: \" + e;");
    L.add("        }");
    L.add("      });");
    L.add("");
    L.add("      page.exposeFunction(\"aimLoadBrowserProfile\", (Object[] a) -> {");
    L.add("        try {");
    L.add("          String name = (a != null && a.length > 0) ? String.valueOf(a[0]).trim() : \"\";");
    L.add("          if (name.isBlank()) return \"\";");
    L.add("          Path p = Paths.get(\"profiles\", name + \".properties\");");
    L.add("          if (!Files.exists(p)) return \"\";");
    L.add("          return Files.readString(p, StandardCharsets.UTF_8);");
    L.add("        } catch (Exception e) {");
    L.add("          return \"\";");
    L.add("        }");
    L.add("      });");
    L.add("");
    L.add("      page.exposeFunction(\"aimLoadSelectionProfile\", (Object[] a) -> {");
    L.add("        try {");
    L.add("          String name = (a != null && a.length > 0) ? String.valueOf(a[0]).trim() : \"\";");
    L.add("          Path p = Paths.get(\"profiles\",\"selection_profiles\", name + \".json\");");
    L.add("          if (!Files.exists(p)) return \"{}\";");
    L.add("          return Files.readString(p, StandardCharsets.UTF_8);");
    L.add("        } catch (Exception e) {");
    L.add("          return \"{}\";");
    L.add("        }");
    L.add("      });");
    L.add("");
    L.add("      page.exposeFunction(\"aimSaveSelectionProfile\", (Object[] a) -> {");
    L.add("        try {");
    L.add("          String json = (a != null && a.length > 0) ? String.valueOf(a[0]) : \"\";");
    L.add("          JsonNode root = OM.readTree(json);");
    L.add("          String name = root.path(\"name\").asText(\"\").trim();");
    L.add("          if (name.isBlank()) return \"ERR: missing name\";");
    L.add("          if (!name.matches(\"[A-Za-z0-9._-]{1,80}\")) return \"ERR: invalid name (use letters/numbers/._-)\";");
    L.add("          JsonNode items = root.path(\"items\");");
    L.add("          if (!items.isArray()) return \"ERR: items must be array\";");
    L.add("          Path p = Paths.get(\"profiles\",\"selection_profiles\", name + \".json\");");
    L.add("          Files.createDirectories(p.getParent());");
    L.add("          OM.writerWithDefaultPrettyPrinter().writeValue(p.toFile(), root);");
    L.add("          if (!Files.exists(p)) return \"ERR: file not created\";");
    L.add("          java.util.List<String> now = listSelectionProfiles();");
    L.add("          if (!now.contains(name)) return \"ERR: saved but not visible in list (unexpected)\";");
    L.add("          long sz = Files.size(p);");
    L.add("          return \"OK: saved profiles/selection_profiles/\" + name + \".json (\" + sz + \" bytes)\";");
    L.add("        } catch (Exception e) {");
    L.add("          return \"ERR: \" + e;");
    L.add("        }");
    L.add("      });");
    L.add("");
    L.add("      page.exposeFunction(\"aimInstallFromProfile\", (Object[] a) -> {");
    L.add("        try {");
    L.add("          String json = (a != null && a.length > 0) ? String.valueOf(a[0]) : \"{}\";");
    L.add("          JsonNode req = OM.readTree(json);");
    L.add("          String sp = req.path(\"selProfile\").asText(\"\").trim();");
    L.add("          int selIndex = req.path(\"selIndex\").asInt(0);");
    L.add("          if (sp.isBlank()) return \"ERR: missing selProfile\";");
    L.add("          ArrayNode selections = selectionsFromSelectionProfile(sp, selIndex);");
    L.add("          if (selections.isEmpty()) return \"ERR: no selections\";");
    L.add("          exportInstall(page, ctx, outDir, viewerTemplate, selections, profileName + \" / \" + sp, video);");
    L.add("          openFolder(outDir);");
    L.add("          return \"OK\";");
    L.add("        } catch (Exception e) {");
    L.add("          return \"ERR: \" + e;");
    L.add("        }");
    L.add("      });");
    L.add("");
    L.add("      page.evaluate(\"() => window.__aimPickerInstall && window.__aimPickerInstall()\" );");
    L.add("");
    L.add("      try {");
    L.add("        page.waitForFunction(\"() => window.__aimPickerDone === true || window.__aimPickerCancel === true\", null, new Page.WaitForFunctionOptions().setTimeout(0));");
    L.add("      } catch (PlaywrightException closed) {");
    L.add("        System.out.println(\"Page closed. Exiting.\");");
    L.add("        safeClose(ctx, browser);");
    L.add("        return;");
    L.add("      }");
    L.add("      boolean canceled = Boolean.TRUE.equals(page.evaluate(\"() => window.__aimPickerCancel === true\"));");
    L.add("      if (canceled) { System.out.println(\"Canceled.\"); safeClose(ctx, browser); return; }");
    L.add("");
    L.add("      String selectionsJson = (String) page.evaluate(\"() => JSON.stringify(window.__aimSelections || [])\");");
    L.add("      ArrayNode selections = (ArrayNode) OM.readTree(selectionsJson);");
    L.add("      if (selections.isEmpty()) { System.out.println(\"No selections.\"); safeClose(ctx, browser); return; }");
    L.add("      exportInstall(page, ctx, outDir, viewerTemplate, selections, profileName, video);");
    L.add("      openFolder(outDir);");
    L.add("      safeClose(ctx, browser);");
    L.add("    }");
    L.add("  }");
    L.add("");
    L.add("  private static void openFolder(Path dir) {");
    L.add("    try {");
    L.add("      String os = System.getProperty(\"os.name\",\"\").toLowerCase(Locale.ROOT);");
    L.add("      if (os.contains(\"win\")) new ProcessBuilder(\"cmd\",\"/c\",\"start\",\"\", dir.toAbsolutePath().toString()).start();");
    L.add("      else if (os.contains(\"mac\")) new ProcessBuilder(\"open\", dir.toAbsolutePath().toString()).start();");
    L.add("      else new ProcessBuilder(\"xdg-open\", dir.toAbsolutePath().toString()).start();");
    L.add("    } catch (Exception ignored) {}");
    L.add("  }");
    L.add("");
    L.add("  private static void exportInstall(Page page, BrowserContext ctx, Path outDir, String viewerTemplate, ArrayNode selections, String label, boolean video) throws Exception {");
    L.add("    Path shotsDir = outDir.resolve(\"element_screenshots\");");
    L.add("    Path mediaDir = outDir.resolve(\"media\");");
    L.add("    Files.createDirectories(shotsDir);");
    L.add("    Files.createDirectories(mediaDir);");
    L.add("");
    L.add("    try { page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(outDir.resolve(\"page_full.png\"))); } catch (Exception ignored) {}");
    L.add("");
    L.add("    ArrayNode results = OM.createArrayNode();");
    L.add("    int idx = 0;");
    L.add("    for (JsonNode sel : selections) {");
    L.add("      idx++;");
    L.add("      String selector = sel.path(\"selector\").asText(\"\");");
    L.add("      ObjectNode r = OM.createObjectNode();");
    L.add("      r.put(\"index\", idx);");
    L.add("      r.put(\"url\", page.url());");
    L.add("      r.put(\"selector\", selector);");
    L.add("      r.put(\"tag\", sel.path(\"tag\").asText(\"\"));");
    L.add("      r.put(\"kind\", sel.path(\"kind\").asText(\"\"));");
    L.add("      r.put(\"pickedText\", sel.path(\"text\").asText(\"\"));");
    L.add("      r.put(\"src\", sel.path(\"src\").asText(\"\"));");
    L.add("      r.put(\"href\", sel.path(\"href\").asText(\"\"));");
    L.add("      r.put(\"outerHtml\", sel.path(\"outerHtml\").asText(\"\"));");
    L.add("");
    L.add("      if (selector.isBlank()) { r.put(\"error\", \"Missing selector\"); results.add(r); continue; }");
    L.add("");
    L.add("      Locator loc = page.locator(selector).first();");
    L.add("      try { loc.waitFor(new Locator.WaitForOptions().setTimeout(3500)); } catch (Exception e) { r.put(\"error\", \"Not found: \" + e.getMessage()); results.add(r); continue; }");
    L.add("");
    L.add("      try { BoundingBox bb = loc.boundingBox(); if (bb != null) { ObjectNode bbj = r.putObject(\"boundingBox\"); bbj.put(\"x\", bb.x); bbj.put(\"y\", bb.y); bbj.put(\"width\", bb.width); bbj.put(\"height\", bb.height); } } catch (Exception ignored) {}");
    L.add("      try { String t = loc.innerText(new Locator.InnerTextOptions().setTimeout(2000)); r.put(\"innerText\", trim(t, 4000)); } catch (Exception ignored) {}");
    L.add("");
    L.add("      Path shot = shotsDir.resolve(String.format(\"el_%03d.png\", idx));");
    L.add("      try { loc.screenshot(new Locator.ScreenshotOptions().setPath(shot)); r.put(\"screenshot\", outDir.relativize(shot).toString().replace(\"\\\\\\\\\", \"/\")); } catch (Exception e) { r.put(\"screenshotError\", e.getMessage()); }");
    L.add("");
    L.add("      List<String> candidates = new ArrayList<>();");
    L.add("      String src = sel.path(\"src\").asText(\"\");");
    L.add("      String href = sel.path(\"href\").asText(\"\");");
    L.add("      if (!src.isBlank()) candidates.add(src);");
    L.add("      if (!href.isBlank()) candidates.add(href);");
    L.add("      try {");
    L.add("        String nested = (String) loc.evaluate(\"(el) => {\\n\" +");
    L.add("          \"  const pick=(u)=> (u && typeof u==='string')?u:'';\\n\" +");
    L.add("          \"  const img = el.matches?.('img') ? el : el.querySelector?.('img');\\n\" +");
    L.add("          \"  if (img && img.src) return pick(img.currentSrc || img.src);\\n\" +");
    L.add("          \"  const v = el.matches?.('video') ? el : el.querySelector?.('video');\\n\" +");
    L.add("          \"  if (v) { if (v.currentSrc) return pick(v.currentSrc); if (v.src) return pick(v.src); const s=v.querySelector?.('source'); if(s && s.src) return pick(s.src); }\\n\" +");
    L.add("          \"  const s2 = el.querySelector?.('source'); if (s2 && s2.src) return pick(s2.src);\\n\" +");
    L.add("          \"  return '';\\n\" +");
    L.add("          \"}\");");
    L.add("        if (nested != null && !nested.isBlank()) candidates.add(nested);");
    L.add("      } catch (Exception ignored) {}");
    L.add("");
    L.add("      candidates = normalizeDedup(page.url(), candidates);");
    L.add("      ArrayNode downloads = r.putArray(\"downloads\");");
    L.add("      for (String u : candidates) {");
    L.add("        ObjectNode d = OM.createObjectNode(); d.put(\"url\", u);");
    L.add("        try { Path saved = download(ctx.request(), u, mediaDir, idx); if (saved != null) d.put(\"savedAs\", outDir.relativize(saved).toString().replace(\"\\\\\\\\\", \"/\")); else { d.put(\"savedAs\", \"\"); d.put(\"note\", \"Empty body/unsupported\"); } }");
    L.add("        catch (Exception ex) { d.put(\"error\", ex.getMessage()); }");
    L.add("        downloads.add(d);");
    L.add("      }");
    L.add("");
    L.add("      results.add(r);");
    L.add("    }");
    L.add("");
    L.add("    ObjectNode manifest = OM.createObjectNode();");
    L.add("    manifest.put(\"capturedAt\", OffsetDateTime.now().toString());");
    L.add("    manifest.put(\"pageUrl\", page.url());");
    L.add("    manifest.put(\"label\", label);");
    L.add("    manifest.put(\"videoEnabled\", video);");
    L.add("    manifest.set(\"selections\", selections);");
    L.add("    manifest.set(\"results\", results);");
    L.add("");
    L.add("    Path manifestPath = outDir.resolve(\"manifest.json\");");
    L.add("    OM.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);");
    L.add("");
    L.add("    Path viewer = outDir.resolve(\"capture_viewer.html\");");
    L.add("    Files.writeString(viewer, viewerTemplate, StandardCharsets.UTF_8);");
    L.add("");
    L.add("    System.out.println(\"Saved:  \" + manifestPath);");
    L.add("    System.out.println(\"Viewer: \" + viewer);");
    L.add("  }");
    L.add("");
    L.add("  static class Profile {");
    L.add("    final String name,userAgent,locale,timezoneId,storageStatePath;");
    L.add("    final int viewportW, viewportH;");
    L.add("    final java.util.List<String> extraChromiumArgs;");
    L.add("    Profile(String name, Properties p){");
    L.add("      this.name=name;");
    L.add("      this.userAgent=p.getProperty(\"userAgent\", \"\").trim();");
    L.add("      this.locale=p.getProperty(\"locale\", \"\").trim();");
    L.add("      this.timezoneId=p.getProperty(\"timezoneId\", \"\").trim();");
    L.add("      this.storageStatePath=p.getProperty(\"storageStatePath\", \"\").trim();");
    L.add("      this.viewportW=intOr(p.getProperty(\"viewportWidth\",\"1400\"),1400);");
    L.add("      this.viewportH=intOr(p.getProperty(\"viewportHeight\",\"900\"),900);");
    L.add("      String args=p.getProperty(\"extraChromiumArgs\", \"\").trim();");
    L.add("      if(args.isBlank()) this.extraChromiumArgs=new ArrayList<>();");
    L.add("      else {");
    L.add("        java.util.List<String> list=new ArrayList<>();");
    L.add("        for(String part: args.split(\",\")){ String a=part.trim(); if(!a.isBlank()) list.add(a); }");
    L.add("        this.extraChromiumArgs=list;");
    L.add("      }");
    L.add("    }");
    L.add("    static Profile load(String profileName){");
    L.add("      try{");
    L.add("        String n=(profileName==null||profileName.isBlank())?\"default\":profileName.trim();");
    L.add("        Path p=Paths.get(\"profiles\", n+\".properties\");");
    L.add("        Properties props=new Properties();");
    L.add("        if(Files.exists(p)) try(InputStream is=Files.newInputStream(p)){ props.load(is);} ");
    L.add("        if(!props.containsKey(\"viewportWidth\")) props.setProperty(\"viewportWidth\",\"1400\");");
    L.add("        if(!props.containsKey(\"viewportHeight\")) props.setProperty(\"viewportHeight\",\"900\");");
    L.add("        return new Profile(n, props);");
    L.add("      }catch(Exception e){");
    L.add("        Properties props=new Properties(); props.setProperty(\"viewportWidth\",\"1400\"); props.setProperty(\"viewportHeight\",\"900\");");
    L.add("        return new Profile(\"default\", props);");
    L.add("      }");
    L.add("    }");
    L.add("  }");
    L.add("");
    L.add("  private static java.util.List<String> listBrowserProfiles(){");
    L.add("    try{ Path dir=Paths.get(\"profiles\"); if(!Files.exists(dir)) return java.util.List.of(\"default\");");
    L.add("      java.util.List<String> out=new ArrayList<>();");
    L.add("      try(DirectoryStream<Path> ds=Files.newDirectoryStream(dir, \"*.properties\")){");
    L.add("        for(Path p: ds){ String fn=p.getFileName().toString(); if(fn.toLowerCase(Locale.ROOT).endsWith(\".properties\")) out.add(fn.substring(0, fn.length()-\".properties\".length())); }");
    L.add("      }");
    L.add("      if(out.isEmpty()) out.add(\"default\"); out.sort(String.CASE_INSENSITIVE_ORDER); return out;");
    L.add("    }catch(Exception e){ return java.util.List.of(\"default\"); }");
    L.add("  }");
    L.add("");
    L.add("  private static java.util.List<String> listSelectionProfiles(){");
    L.add("    try{ Path dir=Paths.get(\"profiles\",\"selection_profiles\"); if(!Files.exists(dir)) Files.createDirectories(dir);");
    L.add("      java.util.List<String> out=new ArrayList<>();");
    L.add("      try(DirectoryStream<Path> ds=Files.newDirectoryStream(dir, \"*.json\")){");
    L.add("        for(Path p: ds){ String fn=p.getFileName().toString(); if(fn.toLowerCase(Locale.ROOT).endsWith(\".json\")) out.add(fn.substring(0, fn.length()-\".json\".length())); }");
    L.add("      }");
    L.add("      if(out.isEmpty()) out.add(\"sample\"); out.sort(String.CASE_INSENSITIVE_ORDER); return out;");
    L.add("    }catch(Exception e){ return java.util.List.of(\"sample\"); }");
    L.add("  }");
    L.add("");
    L.add("  private static ArrayNode loadUrlProfiles(){");
    L.add("    try{");
    L.add("      Path p = Paths.get(\"profiles\",\"url_profiles.json\");");
    L.add("      if(!Files.exists(p)) return OM.createArrayNode();");
    L.add("      JsonNode root = OM.readTree(Files.readString(p, StandardCharsets.UTF_8));");
    L.add("      JsonNode profiles = root.path(\"profiles\");");
    L.add("      if(!profiles.isArray()) return OM.createArrayNode();");
    L.add("      return (ArrayNode) profiles;");
    L.add("    }catch(Exception e){");
    L.add("      return OM.createArrayNode();");
    L.add("    }");
    L.add("  }");
    L.add("");
    L.add("  private static ArrayNode loadSelectionProfileItems(String name) throws IOException {");
    L.add("    Path p = Paths.get(\"profiles\",\"selection_profiles\", name + \".json\");");
    L.add("    if (!Files.exists(p)) throw new FileNotFoundException(\"Selection profile not found: \" + p);");
    L.add("    JsonNode root = OM.readTree(Files.readString(p, StandardCharsets.UTF_8));");
    L.add("    JsonNode items = root.path(\"items\");");
    L.add("    if (!items.isArray()) throw new IOException(\"Invalid selection profile JSON: missing items[]\");");
    L.add("    return (ArrayNode) items;");
    L.add("  }");
    L.add("");
    L.add("  private static ArrayNode selectionsFromSelectionProfile(String name, int selIndex) throws Exception {");
    L.add("    ArrayNode items = loadSelectionProfileItems(name);");
    L.add("    ArrayNode out = OM.createArrayNode();");
    L.add("    int i = 0;");
    L.add("    for (JsonNode it : items) {");
    L.add("      i++;");
    L.add("      if (selIndex > 0 && i != selIndex) continue;");
    L.add("      String sel = it.path(\"selector\").asText(\"\");");
    L.add("      if (sel.isBlank()) continue;");
    L.add("      ObjectNode s = OM.createObjectNode();");
    L.add("      s.put(\"selector\", sel);");
    L.add("      s.put(\"tag\", it.path(\"tag\").asText(\"\"));");
    L.add("      s.put(\"kind\", it.path(\"kind\").asText(\"\"));");
    L.add("      s.put(\"text\", it.path(\"text\").asText(\"\"));");
    L.add("      s.put(\"src\", \"\");");
    L.add("      s.put(\"href\", \"\");");
    L.add("      s.put(\"outerHtml\", \"\");");
    L.add("      out.add(s);");
    L.add("    }");
    L.add("    return out;");
    L.add("  }");
    L.add("");
    L.add("  private static boolean isRetryableNavError(Throwable ex) {");
    L.add("    String m = String.valueOf(ex.getMessage()).toLowerCase(Locale.ROOT);");
    L.add("    return m.contains(\"err_network_changed\")");
    L.add("      || m.contains(\"err_internet_disconnected\")");
    L.add("      || m.contains(\"err_address_unreachable\")");
    L.add("      || m.contains(\"err_name_not_resolved\")");
    L.add("      || m.contains(\"err_network_access_denied\")");
    L.add("      || m.contains(\"err_connection_closed\")");
    L.add("      || m.contains(\"err_connection_reset\")");
    L.add("      || m.contains(\"err_connection_refused\")");
    L.add("      || m.contains(\"err_timed_out\")");
    L.add("      || m.contains(\"navigation interrupted\");");
    L.add("  }");
    L.add("");
    L.add("  private static void navigateWithRetry(Page page, String url) {");
    L.add("    int maxAttempts = 6;");
    L.add("    long[] delaysMs = new long[]{ 250, 600, 1200, 2000, 3000, 4500 };");
    L.add("    RuntimeException last = null;");
    L.add("    for (int attempt = 1; attempt <= maxAttempts; attempt++) {");
    L.add("      try {");
    L.add("        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(60000));");
    L.add("        return;");
    L.add("      } catch (RuntimeException ex) {");
    L.add("        last = ex;");
    L.add("        if (!isRetryableNavError(ex) || attempt == maxAttempts) throw ex;");
    L.add("        System.out.println(\"Navigation failed (attempt \" + attempt + \"/\" + maxAttempts + \"): \" + ex.getMessage());");
    L.add("        try { Thread.sleep(delaysMs[Math.min(attempt - 1, delaysMs.length - 1)]); } catch (InterruptedException ignored) {}");
    L.add("        try { page.waitForTimeout(150); } catch (Exception ignored) {}");
    L.add("      }");
    L.add("    }");
    L.add("    if (last != null) throw last;");
    L.add("  }");
    L.add("");
    L.add("  private static void safeClose(BrowserContext ctx, Browser browser) { try { ctx.close(); } catch (Exception ignored) {} try { browser.close(); } catch (Exception ignored) {} }");
    L.add("  private static boolean hasFlag(String[] args, String flag) { for (String a : args) if (flag.equalsIgnoreCase(a)) return true; return false; }");
    L.add("  private static String argValue(String[] args, String key, String def) { for (int i=0;i<args.length;i++) if (key.equalsIgnoreCase(args[i]) && i+1<args.length) return args[i+1]; return def; }");
    L.add("  private static String firstNonFlagArg(String[] args) { if (args==null) return null; for (int i=0;i<args.length;i++){ String a=String.valueOf(args[i]); if (a.startsWith(\"--\")) { if ((\"--profile\".equalsIgnoreCase(a)) && i+1<args.length) i++; continue; } if(!a.trim().isEmpty()) return a.trim(); } return null; }");
    L.add("  private static int intOr(String s, int def){ try{ return Integer.parseInt(String.valueOf(s).trim()); }catch(Exception e){ return def; } }");
    L.add("  private static String trim(String s, int max) { if (s == null) return \"\"; s = s.replace(\"\\r\", \"\").trim(); return s.length() <= max ? s : s.substring(0, max) + \"...\"; }");
    L.add("  private static String escJson(String s){ return String.valueOf(s).replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\").replace(\"\\n\",\" \").replace(\"\\r\",\" \"); }");
    L.add("");
    L.add("  private static List<String> normalizeDedup(String baseUrl, List<String> urls) {");
    L.add("    LinkedHashSet<String> out = new LinkedHashSet<>();");
    L.add("    for (String u : urls) {");
    L.add("      if (u == null) continue;");
    L.add("      u = u.trim();");
    L.add("      if (u.isEmpty()) continue;");
    L.add("      try { u = URI.create(baseUrl).resolve(u).toString(); } catch (Exception ignored) {}");
    L.add("      out.add(u);");
    L.add("    }");
    L.add("    return new ArrayList<>(out);");
    L.add("  }");
    L.add("");
    L.add("  private static Path download(APIRequestContext req, String url, Path mediaDir, int idx) throws IOException {");
    L.add("    APIResponse resp = req.get(url, RequestOptions.create().setMaxRedirects(5));");
    L.add("    if (resp == null) return null;");
    L.add("    int st = resp.status();");
    L.add("    if (st < 200 || st >= 300) throw new IOException(\"HTTP \" + st);");
    L.add("    byte[] body = resp.body();");
    L.add("    if (body == null || body.length == 0) return null;");
    L.add("    String ct = \"\";");
    L.add("    try { ct = resp.headers().getOrDefault(\"content-type\", \"\"); } catch (Exception ignored) {}");
    L.add("    String ext = ext(ct, url);");
    L.add("    Path out = mediaDir.resolve(String.format(\"media_%03d%s\", idx, ext));");
    L.add("    Files.write(out, body);");
    L.add("    return out;");
    L.add("  }");
    L.add("");
    L.add("  private static String ext(String ct, String url) {");
    L.add("    String t = (ct == null ? \"\" : ct.toLowerCase(Locale.ROOT));");
    L.add("    if (t.contains(\"image/png\")) return \".png\";");
    L.add("    if (t.contains(\"image/jpeg\") || t.contains(\"image/jpg\")) return \".jpg\";");
    L.add("    if (t.contains(\"image/webp\")) return \".webp\";");
    L.add("    if (t.contains(\"image/gif\")) return \".gif\";");
    L.add("    if (t.contains(\"video/mp4\")) return \".mp4\";");
    L.add("    if (t.contains(\"video/webm\")) return \".webm\";");
    L.add("    if (t.contains(\"application/pdf\")) return \".pdf\";");
    L.add("    try {");
    L.add("      String p = URI.create(url).getPath();");
    L.add("      int dot = p.lastIndexOf('.');");
    L.add("      if (dot >= 0 && dot > p.lastIndexOf('/')) {");
    L.add("        String e = p.substring(dot);");
    L.add("        if (e.length() <= 6) return e;");
    L.add("      }");
    L.add("    } catch (Exception ignored) {}");
    L.add("    return \".bin\";");
    L.add("  }");
    L.add("");
    L.add("  private static String readResourceText(String path) throws IOException {");
    L.add("    try (InputStream is = AimPickerProgram.class.getResourceAsStream(path)) {");
    L.add("      if (is == null) throw new FileNotFoundException(\"Missing resource: \" + path);");
    L.add("      return new String(is.readAllBytes(), StandardCharsets.UTF_8);");
    L.add("    }");
    L.add("  }");
    L.add("");
    L.add("}");

    return String.join("\n", L);
  }

  private static String viewerTemplateHtml() {
    return String.join("\n",
      "<!doctype html>",
      "<html>",
      "<head>",
      "  <meta charset=\"utf-8\">",
      "  <title>Aim Capture Viewer</title>",
      "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">",
      "  <style>",
      "    body{font-family:system-ui,Segoe UI,Roboto,Arial;margin:0;background:#0b0c10;color:#e8e8ea}",
      "    header{padding:14px 16px;border-bottom:1px solid rgba(255,255,255,.08);position:sticky;top:0;background:#0b0c10}",
      "    .row{display:flex;gap:12px;align-items:center;flex-wrap:wrap}",
      "    input{padding:8px 10px;border-radius:10px;border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.06);color:#fff;min-width:280px}",
      "    a{color:#7dd3fc}",
      "    main{padding:16px;display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:12px}",
      "    .card{border:1px solid rgba(255,255,255,.12);border-radius:14px;background:rgba(255,255,255,.04);padding:12px}",
      "    .k{opacity:.7;font-size:12px}",
      "    .v{word-break:break-word}",
      "    img{max-width:100%;border-radius:12px;border:1px solid rgba(255,255,255,.12)}",
      "    video{width:100%;border-radius:12px;border:1px solid rgba(255,255,255,.12)}",
      "    pre{white-space:pre-wrap;word-break:break-word;background:rgba(0,0,0,.35);padding:10px;border-radius:12px;border:1px solid rgba(255,255,255,.12);max-height:240px;overflow:auto}",
      "    .badge{display:inline-block;padding:2px 8px;border-radius:999px;border:1px solid rgba(255,255,255,.16);background:rgba(255,255,255,.08);font-size:12px}",
      "  </style>",
      "</head>",
      "<body>",
      "<header>",
      "  <div class=\"row\">",
      "    <div class=\"badge\">Aim Capture Viewer</div>",
      "    <div class=\"k\">Loads <b>manifest.json</b> from this folder</div>",
      "  </div>",
      "  <div class=\"row\" style=\"margin-top:10px\">",
      "    <input id=\"q\" placeholder=\"filter by selector/text/tag/kind...\">",
      "    <a id=\"openPage\" href=\"#\" target=\"_blank\" rel=\"noopener\">open captured page</a>",
      "  </div>",
      "</header>",
      "<main id=\"grid\"></main>",
      "<script>",
      "(async () => {",
      "  const res = await fetch('./manifest.json', {cache:'no-store'});",
      "  const m = await res.json();",
      "  document.getElementById('openPage').href = m.pageUrl || '#';",
      "  const items = (m.results || []).map(r => ({",
      "    index: r.index, selector: r.selector || '', tag: r.tag || '', kind: r.kind || '',",
      "    pickedText: r.pickedText || '', innerText: r.innerText || '', outerHtml: r.outerHtml || '',",
      "    screenshot: r.screenshot || '', downloads: (r.downloads || []).map(d => d.savedAs).filter(Boolean)",
      "  }));",
      "  const grid = document.getElementById('grid');",
      "  const q = document.getElementById('q');",
      "  const esc = s => String(s||'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');",
      "  function render(){",
      "    const needle = (q.value||'').toLowerCase().trim();",
      "    grid.innerHTML = '';",
      "    items.filter(it => {",
      "      if(!needle) return true;",
      "      const hay = (it.selector+' '+it.tag+' '+it.kind+' '+it.pickedText+' '+it.innerText).toLowerCase();",
      "      return hay.includes(needle);",
      "    }).forEach(it => {",
      "      const card = document.createElement('div');",
      "      card.className = 'card';",
      "      const dlLinks = it.downloads.map(p => `<div><a href=\"./${p}\" target=\"_blank\" rel=\"noopener\">${p}</a></div>`).join('');",
      "      const mediaHtml = it.downloads.map(p => {",
      "        const lower = p.toLowerCase();",
      "        if(lower.endsWith('.mp4')||lower.endsWith('.webm')) return `<video controls src=\"./${p}\"></video>`;",
      "        if(lower.endsWith('.png')||lower.endsWith('.jpg')||lower.endsWith('.jpeg')||lower.endsWith('.webp')||lower.endsWith('.gif')) return `<img src=\"./${p}\">`;",
      "        return '';",
      "      }).join('');",
      "      card.innerHTML = `",
      "        <div class=\"row\" style=\"justify-content:space-between\">",
      "          <div class=\"badge\">#${it.index}</div>",
      "          <div class=\"badge\">${esc(it.kind)}</div>",
      "          <div class=\"badge\">${esc(it.tag)}</div>",
      "        </div>",
      "        <div style=\"margin-top:10px\"><div class=\"k\">selector</div><div class=\"v\">${esc(it.selector)}</div></div>",
      "        <div style=\"margin-top:10px\"><div class=\"k\">text</div><div class=\"v\">${esc(it.pickedText||it.innerText||'')}</div></div>",
      "        ${it.screenshot ? `<div style=\"margin-top:10px\"><div class=\"k\">screenshot</div><img src=\"./${it.screenshot}\"></div>` : ''}",
      "        ${mediaHtml ? `<div style=\"margin-top:10px\"><div class=\"k\">media preview</div>${mediaHtml}</div>` : ''}",
      "        ${dlLinks ? `<div style=\"margin-top:10px\"><div class=\"k\">downloaded files</div>${dlLinks}</div>` : ''}",
      "        ${it.outerHtml ? `<div style=\"margin-top:10px\"><div class=\"k\">outerHTML</div><pre>${esc(it.outerHtml)}</pre></div>` : ''}",
      "      `;",
      "      grid.appendChild(card);",
      "    });",
      "  }",
      "  q.addEventListener('input', render);",
      "  render();",
      "})();",
      "</script>",
      "</body>",
      "</html>",
      ""
    );
  }

  // picker.js with fixed selection profile load + highlight-on-load + delete + autosave
  private static String pickerJs() {
    return String.join("\n",
      "(() => {",
      "  if (window.__aimPickerInstall) return;",
      "",
      "  function safeParseJson(s, def){ try { return JSON.parse(String(s)); } catch(e){ return def; } }",
      "  function esc(s){ return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }",
      "  function escAttr(s){ return String(s||'').replace(/\"/g,'&quot;'); }",
      "",
      "  (function(){",
      "    const _open = window.open;",
      "    window.open = function(){ if (window.__aimPickMode) return null; return _open.apply(this, arguments); };",
      "    const _assign = location.assign.bind(location);",
      "    const _replace = location.replace.bind(location);",
      "    location.assign = function(u){ if (window.__aimPickMode) return; return _assign(u); };",
      "    location.replace = function(u){ if (window.__aimPickMode) return; return _replace(u); };",
      "    const _push = history.pushState.bind(history);",
      "    const _rep  = history.replaceState.bind(history);",
      "    history.pushState = function(){ if (window.__aimPickMode) return; return _push.apply(history, arguments); };",
      "    history.replaceState = function(){ if (window.__aimPickMode) return; return _rep.apply(history, arguments); };",
      "  })();",
      "",
      "  function cssEscape(s) {",
      "    try { return CSS.escape(String(s)); }",
      "    catch (e) { return String(s).replace(/[^a-zA-Z0-9_-]/g, \"\\\\$&\"); }",
      "  }",
      "",
      "  function stableAttrSelector(el) {",
      "    const attrs = [\"data-testid\",\"data-test-id\",\"data-qa\",\"data-id\",\"aria-label\",\"name\",\"role\"];",
      "    for (const a of attrs) {",
      "      const v = el.getAttribute && el.getAttribute(a);",
      "      if (v && v.length <= 140) {",
      "        const tag = el.tagName.toLowerCase();",
      "        return `${tag}[${a}=\"${String(v).replace(/\"/g,'\\\\\"')}\"]`;",
      "      }",
      "    }",
      "    return \"\";",
      "  }",
      "",
      "  function buildSelector(el) {",
      "    if (!(el instanceof Element)) return \"\";",
      "    if (el.id) return `#${cssEscape(el.id)}`;",
      "    const stable = stableAttrSelector(el);",
      "    if (stable) return stable;",
      "    const parts = [];",
      "    let cur = el;",
      "    while (cur && cur.nodeType === 1 && cur !== document.documentElement) {",
      "      let part = cur.tagName.toLowerCase();",
      "      const stable2 = stableAttrSelector(cur);",
      "      if (stable2) { parts.unshift(stable2); break; }",
      "      if (cur.classList && cur.classList.length) {",
      "        const cls = Array.from(cur.classList).slice(0, 2).map(c => '.' + cssEscape(c)).join('');",
      "        if (cls) part += cls;",
      "      }",
      "      const parent = cur.parentElement;",
      "      if (parent) {",
      "        const same = Array.from(parent.children).filter(x => x.tagName === cur.tagName);",
      "        if (same.length > 1) {",
      "          const idx = same.indexOf(cur) + 1;",
      "          part += `:nth-of-type(${idx})`;",
      "        }",
      "      }",
      "      parts.unshift(part);",
      "      cur = cur.parentElement;",
      "      if (parts.length >= 8) break;",
      "    }",
      "    return parts.join(' > ');",
      "  }",
      "",
      "  function textSnippet(el) {",
      "    try {",
      "      const t = (el.innerText || el.textContent || '').trim().replace(/\\s+/g, ' ');",
      "      return t.slice(0, 240);",
      "    } catch (e) { return ''; }",
      "  }",
      "",
      "  function guessKind(el) {",
      "    const tag = (el.tagName || '').toLowerCase();",
      "    if (tag === 'img') return 'image';",
      "    if (tag === 'video') return 'video';",
      "    if (tag === 'source') return 'source';",
      "    if (tag === 'a') return 'link';",
      "    if (tag === 'input' || tag === 'textarea' || tag === 'select') return 'input';",
      "    return 'element';",
      "  }",
      "",
      "  function getSrc(el) {",
      "    try {",
      "      const tag = (el.tagName || '').toLowerCase();",
      "      if (tag === 'img') return el.currentSrc || el.src || '';",
      "      if (tag === 'video') return el.currentSrc || el.src || '';",
      "      if (tag === 'source') return el.src || '';",
      "      return '';",
      "    } catch (e) { return ''; }",
      "  }",
      "",
      "  function getHref(el) {",
      "    try {",
      "      const tag = (el.tagName || '').toLowerCase();",
      "      if (tag === 'a') return el.href || '';",
      "      return '';",
      "    } catch (e) { return ''; }",
      "  }",
      "",
      "  async function getConfig(){",
      "    if (!window.aimGetConfig) return null;",
      "    const json = await window.aimGetConfig();",
      "    return safeParseJson(json, null);",
      "  }",
      "",
      "  function ensureUI() {",
      "    if (document.getElementById('__aim_panel')) return;",
      "",
      "    const style = document.createElement('style');",
      "    style.textContent = `",
      "      #__aim_panel{position:fixed;top:0;right:0;width:460px;height:100vh;background:rgba(15,15,18,.94);color:#fff;z-index:2147483647;font:12px system-ui;border-left:1px solid rgba(255,255,255,.08);display:flex;flex-direction:column}",
      "      #__aim_hdr{padding:10px;display:flex;gap:8px;align-items:center;border-bottom:1px solid rgba(255,255,255,.08)}",
      "      #__aim_title{font-weight:700;flex:1}",
      "      .__aim_btn{border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.06);color:#fff;padding:6px 8px;border-radius:8px;cursor:pointer;user-select:none}",
      "      .__aim_btn:hover{background:rgba(255,255,255,.10)}",
      "      #__aim_help{padding:8px 10px;color:rgba(255,255,255,.75);border-bottom:1px solid rgba(255,255,255,.08);line-height:1.25}",
      "      #__aim_tabs{display:flex;gap:8px;padding:10px;border-bottom:1px solid rgba(255,255,255,.08)}",
      "      .__aim_tab{padding:6px 10px;border-radius:999px;border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.06);cursor:pointer}",
      "      .__aim_tab.active{background:rgba(255,255,255,.12)}",
      "      #__aim_body{padding:10px;overflow:auto;flex:1}",
      "      .__aim_row{display:flex;gap:8px;align-items:center;margin-bottom:8px}",
      "      label{opacity:.8}",
      "      input,select,textarea{width:100%;box-sizing:border-box;padding:7px 8px;border-radius:10px;border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.06);color:#fff}",
      "      textarea{min-height:84px;resize:vertical}",
      "      .__aim_card{border:1px solid rgba(255,255,255,.12);border-radius:12px;background:rgba(255,255,255,.04);padding:10px;margin-bottom:10px}",
      "      .__aim_k{opacity:.7;font-size:11px}",
      "      .__aim_item{border:1px solid rgba(255,255,255,.12);border-radius:10px;padding:8px;margin-top:8px;background:rgba(255,255,255,.04)}",
      "      .__aim_selected_outline{outline:3px solid #22d3ee !important; outline-offset:2px !important}",
      "      #__aim_overlay{position:fixed;z-index:2147483646;pointer-events:none;border:2px solid #00aaff;background:rgba(0,170,255,.08)}",
      "      #__aim_label{position:fixed;z-index:2147483646;pointer-events:none;padding:4px 6px;border-radius:8px;background:rgba(0,0,0,.72);color:#fff;max-width:calc(100vw - 480px);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}",
      "      .__aim_badge{display:inline-block;padding:2px 8px;border-radius:999px;border:1px solid rgba(255,255,255,.16);background:rgba(255,255,255,.08);font-size:11px}",
      "    `;",
      "    document.documentElement.appendChild(style);",
      "",
      "    const panel = document.createElement('div');",
      "    panel.id = '__aim_panel';",
      "    panel.innerHTML = `",
      "      <div id=\"__aim_hdr\">",
      "        <div id=\"__aim_title\">Aim Picker Program</div>",
      "        <span class=\"__aim_badge\" id=\"__aim_mode\">PICK: ON</span>",
      "        <div class=\"__aim_btn\" id=\"__aim_toggle\">F8</div>",
      "        <div class=\"__aim_btn\" id=\"__aim_finish\">Install</div>",
      "        <div class=\"__aim_btn\" id=\"__aim_cancel\">Esc</div>",
      "      </div>",
      "      <div id=\"__aim_help\">",
      "        <b>Pick mode:</b> click selects (no navigation).<br>",
      "        <b>Ctrl+Click</b> bypass • <b>Backspace</b> undo • <b>Install</b> exports picked items",
      "      </div>",
      "      <div id=\"__aim_tabs\">",
      "        <div class=\"__aim_tab active\" data-tab=\"sites\">Sites</div>",
      "        <div class=\"__aim_tab\" data-tab=\"select\">Selection Profiles</div>",
      "        <div class=\"__aim_tab\" data-tab=\"picked\">Picked</div>",
      "      </div>",
      "      <div id=\"__aim_body\"></div>",
      "    `;",
      "    document.documentElement.appendChild(panel);",
      "",
      "    const overlay = document.createElement('div'); overlay.id='__aim_overlay'; document.documentElement.appendChild(overlay);",
      "    const label = document.createElement('div'); label.id='__aim_label'; document.documentElement.appendChild(label);",
      "",
      "    panel.querySelector('#__aim_finish').addEventListener('click', () => window.__aimPickerDone = true);",
      "    panel.querySelector('#__aim_cancel').addEventListener('click', () => window.__aimPickerCancel = true);",
      "    panel.querySelector('#__aim_toggle').addEventListener('click', () => togglePickMode());",
      "",
      "    panel.querySelectorAll('.__aim_tab').forEach(t => {",
      "      t.addEventListener('click', () => {",
      "        panel.querySelectorAll('.__aim_tab').forEach(x => x.classList.remove('active'));",
      "        t.classList.add('active');",
      "        window.__aimTab = t.getAttribute('data-tab');",
      "        renderBody();",
      "      });",
      "    });",
      "  }",
      "",
      "  function updateModePill() {",
      "    const pill = document.getElementById('__aim_mode');",
      "    if (!pill) return;",
      "    pill.textContent = window.__aimPickMode ? 'PICK: ON' : 'PICK: OFF';",
      "  }",
      "",
      "  function togglePickMode() {",
      "    window.__aimPickMode = !window.__aimPickMode;",
      "    updateModePill();",
      "  }",
      "",
      "  function renderBody(){",
      "    const body = document.getElementById('__aim_body');",
      "    if(!body) return;",
      "    const tab = window.__aimTab || 'sites';",
      "    if(tab === 'sites') return renderSites(body);",
      "    if(tab === 'select') return renderSelectionProfiles(body);",
      "    return renderPicked(body);",
      "  }",
      "",
      "  const DEFAULT_PROFILE_TEXT = [",
      "    'viewportWidth=1400',",
      "    'viewportHeight=900',",
      "    'userAgent=',",
      "    'locale=',",
      "    'timezoneId=',",
      "    'storageStatePath=',",
      "    'extraChromiumArgs=',",
      "    ''",
      "  ].join('\\n');",
      "",
      "  async function renderSites(body){",
      "    const cfg = await getConfig();",
      "    body.innerHTML = '';",
      "",
      "    const card = document.createElement('div'); card.className='__aim_card';",
      "    card.innerHTML = `",
      "      <div class=\"__aim_k\">Browser Profile (profiles/*.properties) — create/edit/save here</div>",
      "      <div class=\"__aim_row\">",
      "        <select id=\"__aim_browser_profile\"></select>",
      "        <div class=\"__aim_btn\" id=\"__aim_bp_load\">Load</div>",
      "      </div>",
      "      <div class=\"__aim_row\">",
      "        <input id=\"__aim_bp_new\" placeholder=\"new profile name (letters/numbers/._-)\" />",
      "        <div class=\"__aim_btn\" id=\"__aim_bp_new_btn\">New</div>",
      "      </div>",
      "      <textarea id=\"__aim_bp_text\" spellcheck=\"false\"></textarea>",
      "      <div class=\"__aim_row\" style=\"margin-top:8px\">",
      "        <div class=\"__aim_btn\" id=\"__aim_bp_save\">Save Browser Profile</div>",
      "        <div style=\"flex:1\"></div>",
      "        <span class=\"__aim_k\" id=\"__aim_bp_status\"></span>",
      "      </div>",
      "      <div class=\"__aim_k\" style=\"margin-top:10px\">URL</div>",
      "      <div class=\"__aim_row\">",
      "        <input id=\"__aim_url\" placeholder=\"https://...\">",
      "        <div class=\"__aim_btn\" id=\"__aim_go\">Go</div>",
      "      </div>",
      "      <div class=\"__aim_k\" style=\"margin-top:8px\">Saved URL Profiles (profiles/url_profiles.json)</div>",
      "      <div class=\"__aim_row\">",
      "        <select id=\"__aim_url_profile\"></select>",
      "        <div class=\"__aim_btn\" id=\"__aim_use_profile\">Use</div>",
      "      </div>",
      "      <div class=\"__aim_row\">",
      "        <input id=\"__aim_new_name\" placeholder=\"URL profile name\">",
      "        <div class=\"__aim_btn\" id=\"__aim_add\">Add/Update</div>",
      "      </div>",
      "      <div class=\"__aim_row\">",
      "        <div class=\"__aim_btn\" id=\"__aim_delete\">Delete</div>",
      "        <div style=\"flex:1\"></div>",
      "        <span class=\"__aim_k\" id=\"__aim_status\"></span>",
      "      </div>",
      "    `;",
      "    body.appendChild(card);",
      "",
      "    const bpSel = card.querySelector('#__aim_browser_profile');",
      "    (cfg?.browserProfiles||['default']).forEach(p => {",
      "      const o=document.createElement('option'); o.value=p; o.textContent=p; bpSel.appendChild(o);",
      "    });",
      "    bpSel.value = cfg?.currentBrowserProfile || 'default';",
      "",
      "    const bpText = card.querySelector('#__aim_bp_text');",
      "    const bpStatus = card.querySelector('#__aim_bp_status');",
      "    const setBPStatus = (s) => bpStatus.textContent = s || '';",
      "",
      "    async function loadBP(name){",
      "      if (!window.aimLoadBrowserProfile) { bpText.value = DEFAULT_PROFILE_TEXT; setBPStatus('ERR: missing binding'); return; }",
      "      const raw = await window.aimLoadBrowserProfile(String(name||''));",
      "      bpText.value = raw && String(raw).trim().length ? String(raw) : DEFAULT_PROFILE_TEXT;",
      "      setBPStatus('Loaded: ' + name);",
      "    }",
      "",
      "    card.querySelector('#__aim_bp_load').addEventListener('click', () => loadBP(bpSel.value));",
      "    bpSel.addEventListener('change', () => loadBP(bpSel.value));",
      "    await loadBP(bpSel.value);",
      "",
      "    card.querySelector('#__aim_bp_new_btn').addEventListener('click', () => {",
      "      bpText.value = DEFAULT_PROFILE_TEXT;",
      "      setBPStatus('New profile: fill name then Save');",
      "    });",
      "",
      "    card.querySelector('#__aim_bp_save').addEventListener('click', async () => {",
      "      const nameFromNew = (card.querySelector('#__aim_bp_new').value||'').trim();",
      "      const name = nameFromNew || (bpSel.value||'').trim();",
      "      if (!name) return setBPStatus('Missing profile name');",
      "      if (!/^[A-Za-z0-9._-]{1,80}$/.test(name)) return setBPStatus('Invalid name (use letters/numbers/._-)');",
      "      if (!window.aimSaveBrowserProfile) return setBPStatus('ERR: missing binding');",
      "",
      "      setBPStatus('Saving...');",
      "      const res = await window.aimSaveBrowserProfile(JSON.stringify({name, content: bpText.value||''}));",
      "      setBPStatus(res);",
      "",
      "      const cfg2 = await getConfig();",
      "      const okVisible = (cfg2?.browserProfiles||[]).includes(name);",
      "      if (!okVisible) setBPStatus((res||'') + ' | ERR: not visible after save');",
      "",
      "      await renderSites(body);",
      "      const bpSel2 = document.getElementById('__aim_browser_profile');",
      "      if (bpSel2) bpSel2.value = name;",
      "    });",
      "",
      "    const urlInput = card.querySelector('#__aim_url');",
      "    urlInput.value = (cfg?.currentUrl || location.href);",
      "",
      "    const upSel = card.querySelector('#__aim_url_profile');",
      "    const ups = (cfg?.urlProfiles || []);",
      "    ups.forEach(p => {",
      "      const o=document.createElement('option'); o.value=p.name||''; o.textContent=(p.name||'(unnamed)') + ' — ' + (p.url||'');",
      "      upSel.appendChild(o);",
      "    });",
      "",
      "    const status = card.querySelector('#__aim_status');",
      "    const setStatus = (s) => status.textContent = s || '';",
      "",
      "    card.querySelector('#__aim_go').addEventListener('click', () => {",
      "      const u = (urlInput.value||'').trim();",
      "      if(!u.startsWith('http://') && !u.startsWith('https://')) return setStatus('URL must start with http:// or https://');",
      "      location.href = u;",
      "    });",
      "",
      "    card.querySelector('#__aim_use_profile').addEventListener('click', () => {",
      "      const name = upSel.value;",
      "      const p = ups.find(x => (x.name||'') === name);",
      "      if(!p) return;",
      "      urlInput.value = p.url || '';",
      "      setStatus('Loaded URL profile: ' + name);",
      "    });",
      "",
      "    card.querySelector('#__aim_add').addEventListener('click', async () => {",
      "      const name = (card.querySelector('#__aim_new_name').value||'').trim();",
      "      const u = (urlInput.value||'').trim();",
      "      if(!name) return setStatus('Missing URL profile name');",
      "      if(!u.startsWith('http://') && !u.startsWith('https://')) return setStatus('URL must start with http:// or https://');",
      "      const next = { profiles: [] };",
      "      const existing = ups.filter(x => (x.name||'') !== name);",
      "      next.profiles = existing.concat([{name, url:u}]);",
      "      const res = await (window.aimSaveUrlProfiles ? window.aimSaveUrlProfiles(JSON.stringify(next)) : 'ERR: missing binding');",
      "      setStatus(res);",
      "      renderBody();",
      "    });",
      "",
      "    card.querySelector('#__aim_delete').addEventListener('click', async () => {",
      "      const name = upSel.value;",
      "      if(!name) return;",
      "      const next = { profiles: ups.filter(x => (x.name||'') !== name) };",
      "      const res = await (window.aimSaveUrlProfiles ? window.aimSaveUrlProfiles(JSON.stringify(next)) : 'ERR: missing binding');",
      "      setStatus(res);",
      "      renderBody();",
      "    });",
      "  }",
      "",
      // --- selection profiles (FIXED) ---
      "  async function renderSelectionProfiles(body){",
      "    const cfg = await getConfig();",
      "    body.innerHTML = '';",
      "",
      "    const HL_CLASS = '__aim_profile_outline';",
      "",
      "    (function ensureProfileStyle(){",
      "      if (document.getElementById('__aim_profile_style')) return;",
      "      const st = document.createElement('style');",
      "      st.id='__aim_profile_style';",
      "      st.textContent = `",
      "        .${HL_CLASS}{ outline:2px dashed #a78bfa !important; outline-offset:2px !important }",
      "      `;",
      "      document.documentElement.appendChild(st);",
      "    })();",
      "",
      "    function clearProfileHighlights(){",
      "      try { document.querySelectorAll('.' + HL_CLASS).forEach(el => el.classList.remove(HL_CLASS)); } catch(e){}",
      "    }",
      "",
      "    function applyProfileHighlights(items){",
      "      clearProfileHighlights();",
      "      try {",
      "        (items||[]).forEach(it => {",
      "          const sel = (it && it.selector) ? String(it.selector).trim() : '';",
      "          if(!sel) return;",
      "          try { document.querySelectorAll(sel).forEach(el => el.classList.add(HL_CLASS)); } catch(e){}",
      "        });",
      "      } catch(e){}",
      "    }",
      "",
      "    async function loadProfileByName(name){",
      "      if (!window.aimLoadSelectionProfile) return { name, items: [] };",
      "      const raw = await window.aimLoadSelectionProfile(String(name||'').trim());",
      "      const parsed = safeParseJson(raw, null);",
      "      if (!parsed || typeof parsed !== 'object') return { name, items: [] };",
      "      if (!parsed.name) parsed.name = name;",
      "      if (!Array.isArray(parsed.items)) parsed.items = [];",
      "      return parsed;",
      "    }",
      "",
      "    async function saveProfileObject(profileObj){",
      "      if (!window.aimSaveSelectionProfile) return 'ERR: missing binding';",
      "      const out = {",
      "        name: String(profileObj.name||'').trim(),",
      "        createdAt: profileObj.createdAt || new Date().toISOString(),",
      "        notes: String(profileObj.notes||''),",
      "        items: Array.isArray(profileObj.items) ? profileObj.items : []",
      "      };",
      "      if(!out.name) return 'ERR: missing name';",
      "      if (!/^[A-Za-z0-9._-]{1,80}$/.test(out.name)) return 'ERR: invalid name (use letters/numbers/._-)';",
      "      return await window.aimSaveSelectionProfile(JSON.stringify(out));",
      "    }",
      "",
      "    const card = document.createElement('div'); card.className='__aim_card';",
      "    card.innerHTML = `",
      "      <div class=\"__aim_k\">Selection Profiles (profiles/selection_profiles/*.json)</div>",
      "      <div class=\"__aim_row\">",
      "        <select id=\"__aim_sp\"></select>",
      "        <div class=\"__aim_btn\" id=\"__aim_load_sp\">Load</div>",
      "        <div class=\"__aim_btn\" id=\"__aim_apply_hl\">Highlight</div>",
      "        <div class=\"__aim_btn\" id=\"__aim_clear_hl\">Clear</div>",
      "      </div>",
      "      <div class=\"__aim_row\">",
      "        <input id=\"__aim_sp_name\" placeholder=\"new profile name (letters/numbers/._-)\"/>",
      "        <div class=\"__aim_btn\" id=\"__aim_save_picked\">Save PICKED as profile</div>",
      "      </div>",
      "      <div class=\"__aim_k\" style=\"margin-top:8px\">Profile Items (edit/delete here)</div>",
      "      <div id=\"__aim_sp_items\"></div>",
      "      <div class=\"__aim_k\" style=\"margin-top:10px\">Install From Profile</div>",
      "      <div class=\"__aim_row\">",
      "        <select id=\"__aim_sp_item\"></select>",
      "        <div class=\"__aim_btn\" id=\"__aim_install_sp\">Install</div>",
      "      </div>",
      "      <div class=\"__aim_k\" style=\"margin-top:10px\">Profile JSON (advanced)</div>",
      "      <textarea id=\"__aim_sp_json\" spellcheck=\"false\"></textarea>",
      "      <div class=\"__aim_row\" style=\"margin-top:8px\">",
      "        <div class=\"__aim_btn\" id=\"__aim_save_json\">Save JSON</div>",
      "        <div style=\"flex:1\"></div>",
      "        <span class=\"__aim_k\" id=\"__aim_sp_status\"></span>",
      "      </div>",
      "    `;",
      "    body.appendChild(card);",
      "",
      "    const spSel     = card.querySelector('#__aim_sp');",
      "    const statusEl  = card.querySelector('#__aim_sp_status');",
      "    const itemsHost = card.querySelector('#__aim_sp_items');",
      "    const jsonArea  = card.querySelector('#__aim_sp_json');",
      "    const spItemSel = card.querySelector('#__aim_sp_item');",
      "    const setStatus = (s) => statusEl.textContent = s || '';",
      "",
      "    (cfg?.selectionProfiles || ['sample']).forEach(name => {",
      "      const o = document.createElement('option');",
      "      o.value = name;",
      "      o.textContent = name;",
      "      spSel.appendChild(o);",
      "    });",
      "",
      "    let currentProfile = { name: spSel.value || 'sample', items: [] };",
      "",
      "    function syncJsonFromCurrent(){ jsonArea.value = JSON.stringify(currentProfile, null, 2); }",
      "",
      "    function parseJsonArea(){",
      "      const parsed = safeParseJson(jsonArea.value, null);",
      "      if(!parsed || typeof parsed !== 'object') return null;",
      "      if(!parsed.name || !Array.isArray(parsed.items)) return null;",
      "      return parsed;",
      "    }",
      "",
      "    function rebuildInstallDropdown(){",
      "      spItemSel.innerHTML = '';",
      "      const o0 = document.createElement('option');",
      "      o0.value = '0';",
      "      o0.textContent = 'ALL';",
      "      spItemSel.appendChild(o0);",
      "      (currentProfile.items||[]).forEach((it, idx) => {",
      "        const o = document.createElement('option');",
      "        o.value = String(idx + 1);",
      "        o.textContent = (idx+1) + ': ' + ((it.tag||'') ? (it.tag+' ') : '') + (it.selector||'');",
      "        spItemSel.appendChild(o);",
      "      });",
      "    }",
      "",
      "    function renderItemsList(){",
      "      itemsHost.innerHTML = '';",
      "      const items = currentProfile.items || [];",
      "      if(!items.length){",
      "        const empty = document.createElement('div');",
      "        empty.className='__aim_k';",
      "        empty.textContent='(no items)';",
      "        itemsHost.appendChild(empty);",
      "        return;",
      "      }",
      "      items.forEach((it, idx) => {",
      "        const div = document.createElement('div');",
      "        div.className='__aim_item';",
      "        const sel  = String(it.selector||'');",
      "        const tag  = String(it.tag||'');",
      "        const kind = String(it.kind||'');",
      "        const text = String(it.text||'');",
      "        div.innerHTML = `",
      "          <div class=\"__aim_row\" style=\"justify-content:space-between\">",
      "            <span class=\"__aim_badge\">#${idx+1}</span>",
      "            <span class=\"__aim_badge\">${esc(kind)}</span>",
      "            <span class=\"__aim_badge\">${esc(tag)}</span>",
      "          </div>",
      "          <div class=\"__aim_k\" style=\"margin-top:6px\">selector</div>",
      "          <input data-f=\"selector\" value=\"${escAttr(sel)}\"/>",
      "          <div class=\"__aim_k\" style=\"margin-top:6px\">text</div>",
      "          <input data-f=\"text\" value=\"${escAttr(text)}\"/>",
      "          <div class=\"__aim_row\" style=\"margin-top:8px\">",
      "            <div class=\"__aim_btn\" data-act=\"scroll\">Scroll</div>",
      "            <div class=\"__aim_btn\" data-act=\"remove\">Delete</div>",
      "          </div>",
      "        `;",
      "",
      "        div.querySelectorAll('input[data-f]').forEach(inp => {",
      "          inp.addEventListener('input', () => {",
      "            const f = inp.getAttribute('data-f');",
      "            currentProfile.items[idx][f] = inp.value || '';",
      "            syncJsonFromCurrent();",
      "            applyProfileHighlights(currentProfile.items);",
      "            rebuildInstallDropdown();",
      "          });",
      "        });",
      "",
      "        div.querySelector('[data-act=\"scroll\"]').addEventListener('click', () => {",
      "          try { const el = document.querySelector(sel); if(el) el.scrollIntoView({behavior:'smooth', block:'center', inline:'center'}); } catch(e){}",
      "        });",
      "",
      "        div.querySelector('[data-act=\"remove\"]').addEventListener('click', async () => {",
      "          try { document.querySelectorAll(sel).forEach(el => el.classList.remove(HL_CLASS)); } catch(e){}",
      "          currentProfile.items.splice(idx, 1);",
      "          syncJsonFromCurrent();",
      "          renderItemsList();",
      "          applyProfileHighlights(currentProfile.items);",
      "          rebuildInstallDropdown();",
      "          setStatus('Saving after delete...');",
      "          const res = await saveProfileObject(currentProfile);",
      "          setStatus(res);",
      "        });",
      "",
      "        itemsHost.appendChild(div);",
      "      });",
      "    }",
      "",
      "    async function loadSelectedProfile(){",
      "      const name = (spSel.value || '').trim() || 'sample';",
      "      clearProfileHighlights();",
      "      currentProfile = await loadProfileByName(name);",
      "      syncJsonFromCurrent();",
      "      renderItemsList();",
      "      rebuildInstallDropdown();",
      "      applyProfileHighlights(currentProfile.items);",
      "      setStatus('Loaded + highlighted: ' + name);",
      "    }",
      "",
      "    card.querySelector('#__aim_load_sp').addEventListener('click', loadSelectedProfile);",
      "    spSel.addEventListener('change', loadSelectedProfile);",
      "",
      "    card.querySelector('#__aim_apply_hl').addEventListener('click', () => {",
      "      const parsed = parseJsonArea();",
      "      if(parsed){ currentProfile = parsed; renderItemsList(); rebuildInstallDropdown(); }",
      "      applyProfileHighlights(currentProfile.items);",
      "      setStatus('Highlighted current items');",
      "    });",
      "",
      "    card.querySelector('#__aim_clear_hl').addEventListener('click', () => {",
      "      clearProfileHighlights();",
      "      setStatus('Cleared highlights');",
      "    });",
      "",
      "    card.querySelector('#__aim_save_picked').addEventListener('click', async () => {",
      "      const name = (card.querySelector('#__aim_sp_name').value||'').trim();",
      "      if(!name) return setStatus('Missing name');",
      "      if (!/^[A-Za-z0-9._-]{1,80}$/.test(name)) return setStatus('Invalid name (use letters/numbers/._-)');",
      "      const items = (window.__aimSelections||[])",
      "        .map(it => ({ selector: it.selector||'', tag: it.tag||'', kind: it.kind||'', text: it.text||'' }))",
      "        .filter(x => x.selector);",
      "      if(!items.length) return setStatus('No picked selections to save');",
      "      const profile = { name, createdAt: new Date().toISOString(), notes:'', items };",
      "      setStatus('Saving...');",
      "      const res = await saveProfileObject(profile);",
      "      setStatus(res);",
      "      renderBody();",
      "    });",
      "",
      "    card.querySelector('#__aim_save_json').addEventListener('click', async () => {",
      "      const parsed = parseJsonArea();",
      "      if(!parsed) return setStatus('Invalid JSON or missing name/items[]');",
      "      if (!/^[A-Za-z0-9._-]{1,80}$/.test(String(parsed.name))) return setStatus('Invalid name (use letters/numbers/._-)');",
      "      currentProfile = parsed;",
      "      renderItemsList();",
      "      rebuildInstallDropdown();",
      "      applyProfileHighlights(currentProfile.items);",
      "      setStatus('Saving...');",
      "      const res = await saveProfileObject(currentProfile);",
      "      setStatus(res);",
      "      renderBody();",
      "    });",
      "",
      "    card.querySelector('#__aim_install_sp').addEventListener('click', async () => {",
      "      const selProfile = (spSel.value || '').trim();",
      "      const selIndex = parseInt(spItemSel.value||'0', 10) || 0;",
      "      if(!window.aimInstallFromProfile) return setStatus('ERR: missing binding');",
      "      setStatus('Installing...');",
      "      const res = await window.aimInstallFromProfile(JSON.stringify({selProfile, selIndex}));",
      "      setStatus(res === 'OK' ? 'Install exported + folder opened' : res);",
      "    });",
      "",
      "    await loadSelectedProfile();",
      "  }",
      "",
      "  function renderPicked(body){",
      "    body.innerHTML = '';",
      "    const listCard = document.createElement('div'); listCard.className='__aim_card';",
      "    listCard.innerHTML = `<div class=\"__aim_k\">Picked items</div><div id=\"__aim_list\"></div>`;",
      "    body.appendChild(listCard);",
      "    const list = listCard.querySelector('#__aim_list');",
      "    list.innerHTML = '';",
      "    (window.__aimSelections||[]).forEach((it,i) => {",
      "      const div=document.createElement('div'); div.className='__aim_item';",
      "      div.innerHTML = `",
      "        <div class=\"__aim_row\" style=\"justify-content:space-between\">",
      "          <span class=\"__aim_badge\">#${i+1}</span>",
      "          <span class=\"__aim_badge\">${esc(it.kind||'')}</span>",
      "          <span class=\"__aim_badge\">${esc(it.tag||'')}</span>",
      "        </div>",
      "        <div class=\"__aim_k\" style=\"margin-top:6px\">selector</div>",
      "        <div title=\"${escAttr(it.selector||'')}\">${esc(it.selector||'')}</div>",
      "        <div class=\"__aim_k\" style=\"margin-top:6px\">text</div>",
      "        <div title=\"${escAttr(it.text||'')}\">${esc(it.text||'')}</div>",
      "        <div class=\"__aim_row\" style=\"margin-top:8px\">",
      "          <div class=\"__aim_btn\" data-act=\"rm\">Remove</div>",
      "          <div class=\"__aim_btn\" data-act=\"go\">Scroll</div>",
      "        </div>",
      "      `;",
      "      div.querySelector('[data-act=\"rm\"]').addEventListener('click', () => {",
      "        try { const el=document.querySelector(it.selector); if(el) el.classList.remove('__aim_selected_outline'); } catch(e){}",
      "        window.__aimSelections.splice(i,1);",
      "        renderBody();",
      "      });",
      "      div.querySelector('[data-act=\"go\"]').addEventListener('click', () => {",
      "        try { const el=document.querySelector(it.selector); if(el) el.scrollIntoView({behavior:'smooth', block:'center', inline:'center'}); } catch(e){}",
      "      });",
      "      list.appendChild(div);",
      "    });",
      "  }",
      "",
      "  window.__aimPickerInstall = () => {",
      "    if (window.__aimPickerActive) return;",
      "    window.__aimPickerActive = true;",
      "    window.__aimSelections = window.__aimSelections || [];",
      "    window.__aimPickerDone = false;",
      "    window.__aimPickerCancel = false;",
      "    window.__aimPickMode = true;",
      "    window.__aimTab = 'sites';",
      "",
      "    ensureUI();",
      "    updateModePill();",
      "    renderBody();",
      "",
      "    const overlay = document.getElementById('__aim_overlay');",
      "    const label = document.getElementById('__aim_label');",
      "    const panel = document.getElementById('__aim_panel');",
      "    const isUI = (el) => panel && panel.contains(el);",
      "",
      "    function onMove(e){",
      "      const el = document.elementFromPoint(e.clientX, e.clientY);",
      "      if (!el || el === overlay || el === label || isUI(el)) return;",
      "      const r = el.getBoundingClientRect();",
      "      overlay.style.left=r.left+'px'; overlay.style.top=r.top+'px';",
      "      overlay.style.width=r.width+'px'; overlay.style.height=r.height+'px';",
      "      const sel = buildSelector(el);",
      "      label.textContent = sel;",
      "      label.style.left = Math.max(0, Math.min(r.left, window.innerWidth - 480)) + 'px';",
      "      label.style.top = Math.max(0, r.top - 26) + 'px';",
      "    }",
      "",
      "    function blockIfPicking(e){",
      "      const el = document.elementFromPoint(e.clientX, e.clientY);",
      "      if (!el || isUI(el)) return;",
      "      if (e.ctrlKey) return;",
      "      if (!window.__aimPickMode) return;",
      "      e.preventDefault();",
      "      e.stopPropagation();",
      "      if (e.stopImmediatePropagation) e.stopImmediatePropagation();",
      "    }",
      "",
      "    function onClickCapture(e){",
      "      const el = document.elementFromPoint(e.clientX, e.clientY);",
      "      if (!el || isUI(el)) return;",
      "      if (e.ctrlKey) return;",
      "      if (!window.__aimPickMode) return;",
      "      e.preventDefault();",
      "      e.stopPropagation();",
      "      if (e.stopImmediatePropagation) e.stopImmediatePropagation();",
      "",
      "      const selector = buildSelector(el);",
      "      if (!selector) return;",
      "",
      "      try { el.classList.add('__aim_selected_outline'); } catch(e){}",
      "      let outerHtml = '';",
      "      try { outerHtml = el.outerHTML || ''; } catch(e){}",
      "",
      "      window.__aimSelections.push({",
      "        selector,",
      "        tag: (el.tagName||'').toLowerCase(),",
      "        kind: guessKind(el),",
      "        text: textSnippet(el),",
      "        src: getSrc(el),",
      "        href: getHref(el),",
      "        outerHtml",
      "      });",
      "",
      "      if ((window.__aimTab||'sites') === 'picked') renderBody();",
      "    }",
      "",
      "    function onKey(e){",
      "      if (e.key === 'Escape') { window.__aimPickerCancel = true; e.preventDefault(); e.stopPropagation(); }",
      "      else if (e.key === 'F8') { togglePickMode(); e.preventDefault(); e.stopPropagation(); }",
      "      else if (e.key === 'Backspace') {",
      "        if (window.__aimSelections.length) {",
      "          const last = window.__aimSelections.pop();",
      "          try { const el=document.querySelector(last.selector); if(el) el.classList.remove('__aim_selected_outline'); } catch(e){}",
      "          if ((window.__aimTab||'sites') === 'picked') renderBody();",
      "        }",
      "        e.preventDefault(); e.stopPropagation();",
      "      }",
      "    }",
      "",
      "    document.addEventListener('mousemove', onMove, true);",
      "    document.addEventListener('pointerdown', blockIfPicking, true);",
      "    document.addEventListener('mousedown', blockIfPicking, true);",
      "    document.addEventListener('touchstart', blockIfPicking, true);",
      "    document.addEventListener('click', onClickCapture, true);",
      "    document.addEventListener('keydown', onKey, true);",
      "  };",
      "})();",
      ""
    );
  }
}