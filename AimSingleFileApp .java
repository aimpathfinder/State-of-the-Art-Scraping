// AimSingleFileApp.java
//
// Single-file “app” (no Maven/JBang preinstalled) that BOOTSTRAPS a full runnable picker engine project
// into ./aim_multi_pick/, downloads Maven Wrapper automatically, builds, and runs it.
//
// What you get after it runs:
// - aim_multi_pick/ (project folder)
// - mvnw.cmd (Maven Wrapper) so you never install Maven
// - A runnable jar that launches Chromium (Playwright) and lets you multi-select elements
//
// Usage (Windows PowerShell):
//   cd C:\Users\aimpa\Documents
//   "C:\Program Files\Java\jdk-21.0.10\bin\javac.exe" AimSingleFileApp.java
//   & "C:\Program Files\Java\jdk-21.0.10\bin\java.exe" AimSingleFileApp "https://example.com"
//
// Hotkeys in the picker:
//   Click = add element
//   Backspace = remove last
//   F9 = finish + save
//   Esc = cancel
//
// Output saved under: aim_multi_pick/aim_capture_<timestamp>/
//
// NOTE: Requires outbound HTTPS access to GitHub raw + Maven Central for downloads.

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public class AimSingleFileApp {

  public static void main(String[] args) throws Exception {
    String url = args.length > 0 ? args[0] : "https://example.com";
    boolean headless = hasFlag(args, "--headless");
    boolean video = hasFlag(args, "--video");

    Path root = Paths.get("aim_multi_pick").toAbsolutePath();
    Path src = root.resolve("src/main/java");
    Path mw = root.resolve(".mvn/wrapper");

    Files.createDirectories(src);
    Files.createDirectories(mw);

    writeFile(root.resolve("pom.xml"), pomXml());
    writeFile(root.resolve("src/main/java/AimMultiElementCapture.java"), appJava());
    writeFile(mw.resolve("maven-wrapper.properties"),
      "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.12/apache-maven-3.9.12-bin.zip\n"
    );

    // Download Maven Wrapper scripts + jar (no Maven install required)
    downloadIfMissing(
      root.resolve("mvnw.cmd"),
      "https://raw.githubusercontent.com/apache/maven-wrapper/master/mvnw.cmd"
    );
    downloadIfMissing(
      root.resolve("mvnw"),
      "https://raw.githubusercontent.com/apache/maven-wrapper/master/mvnw"
    );
    downloadIfMissing(
      mw.resolve("maven-wrapper.jar"),
      "https://raw.githubusercontent.com/apache/maven-wrapper/master/.mvn/wrapper/maven-wrapper.jar"
    );

    // Make mvnw executable on *nix (harmless on Windows)
    try { root.resolve("mvnw").toFile().setExecutable(true); } catch (Exception ignored) {}

    System.out.println("Project ready: " + root);
    System.out.println("Building with Maven Wrapper (downloads Maven + dependencies)...");
    run(root, isWindows() ? new String[]{ "cmd", "/c", "mvnw.cmd", "-q", "-DskipTests", "package" }
                         : new String[]{ "./mvnw", "-q", "-DskipTests", "package" });

    System.out.println("Running picker engine...");
    List<String> cmd = new ArrayList<>();
    cmd.add(javaBin());
    cmd.add("-jar");
    cmd.add(root.resolve("target/aim-multi-pick-1.0.0.jar").toString());
    cmd.add(url);
    if (video) cmd.add("--video");
    if (headless) cmd.add("--headless");

    run(root, cmd.toArray(new String[0]));
  }

  // ---------- helpers ----------

  private static boolean hasFlag(String[] args, String flag) {
    for (String a : args) if (flag.equalsIgnoreCase(a)) return true;
    return false;
  }

  private static void writeFile(Path p, String s) throws IOException {
    Files.createDirectories(p.getParent());
    Files.writeString(p, s, StandardCharsets.UTF_8);
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
    return """
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>

        <groupId>com.aim</groupId>
        <artifactId>aim-multi-pick</artifactId>
        <version>1.0.0</version>

        <properties>
          <maven.compiler.source>21</maven.compiler.source>
          <maven.compiler.target>21</maven.compiler.target>
        </properties>

        <dependencies>
          <dependency>
            <groupId>com.microsoft.playwright</groupId>
            <artifactId>playwright</artifactId>
            <version>1.46.0</version>
          </dependency>
          <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.17.2</version>
          </dependency>
        </dependencies>

        <build>
          <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-shade-plugin</artifactId>
              <version>3.6.0</version>
              <executions>
                <execution>
                  <phase>package</phase>
                  <goals><goal>shade</goal></goals>
                  <configuration>
                    <transformers>
                      <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>AimMultiElementCapture</mainClass>
                      </transformer>
                    </transformers>
                  </configuration>
                </execution>
              </executions>
            </plugin>
          </plugins>
        </build>
      </project>
      """;
  }

  private static String appJava() {
    // The actual multi-select picker engine (Playwright + injected UI), compiled by the wrapper project.
    // This is a trimmed but fully working version with:
    // - multi-select panel
    // - element screenshots
    // - best-effort media download (img/video/source/a)
    // - manifest.json
    return """
      import com.microsoft.playwright.*;
      import com.microsoft.playwright.options.*;
      import com.fasterxml.jackson.databind.*;
      import com.fasterxml.jackson.databind.node.*;

      import java.io.*;
      import java.net.URI;
      import java.nio.file.*;
      import java.time.OffsetDateTime;
      import java.time.format.DateTimeFormatter;
      import java.util.*;

      public class AimMultiElementCapture {
        private static final ObjectMapper OM = new ObjectMapper();

        public static void main(String[] args) throws Exception {
          if (args.length < 1) {
            System.out.println("Usage: java -jar aim-multi-pick-1.0.0.jar \\"https://example.com\\" [--video] [--headless]");
            return;
          }

          String url = args[0];
          boolean video = hasFlag(args, "--video");
          boolean headless = hasFlag(args, "--headless");

          String ts = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
          Path outDir = Paths.get("aim_capture_" + ts).toAbsolutePath();
          Files.createDirectories(outDir);

          try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions()
              .setHeadless(headless)
              .setArgs(new String[]{ "--disable-blink-features=AutomationControlled" }));

            Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions().setViewportSize(1400, 900);
            if (video) ctxOpts.setRecordVideoDir(outDir.resolve("video")).setRecordVideoSize(1400, 900);

            BrowserContext ctx = browser.newContext(ctxOpts);
            Page page = ctx.newPage();

            System.out.println("Output: " + outDir);
            System.out.println("Navigate: " + url);
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            page.addInitScript(PICKER_JS);
            page.evaluate("() => window.__aimPickerInstall && window.__aimPickerInstall()");

            System.out.println("Pick elements: Click add | Backspace undo | F9 finish | Esc cancel");

            page.waitForFunction("() => window.__aimPickerDone === true || window.__aimPickerCancel === true",
              null, new Page.WaitForFunctionOptions().setTimeout(0));

            boolean canceled = Boolean.TRUE.equals(page.evaluate("() => window.__aimPickerCancel === true"));
            if (canceled) {
              System.out.println("Canceled.");
              ctx.close(); browser.close();
              return;
            }

            String selectionsJson = (String) page.evaluate("() => JSON.stringify(window.__aimSelections || [])");
            ArrayNode selections = (ArrayNode) OM.readTree(selectionsJson);

            if (selections.isEmpty()) {
              System.out.println("No selections.");
              ctx.close(); browser.close();
              return;
            }

            Path shotsDir = outDir.resolve("element_screenshots");
            Path mediaDir = outDir.resolve("media");
            Files.createDirectories(shotsDir);
            Files.createDirectories(mediaDir);

            try { page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(outDir.resolve("page_full.png"))); }
            catch (Exception ignored) {}

            ArrayNode results = OM.createArrayNode();
            int idx = 0;

            for (JsonNode sel : selections) {
              idx++;
              String selector = sel.path("selector").asText("");
              String tag = sel.path("tag").asText("");
              String kind = sel.path("kind").asText("");
              String pickedText = sel.path("text").asText("");
              String src = sel.path("src").asText("");
              String href = sel.path("href").asText("");

              ObjectNode r = OM.createObjectNode();
              r.put("index", idx);
              r.put("url", page.url());
              r.put("selector", selector);
              r.put("tag", tag);
              r.put("kind", kind);
              r.put("pickedText", pickedText);
              r.put("src", src);
              r.put("href", href);

              if (selector.isBlank()) { r.put("error", "Missing selector"); results.add(r); continue; }

              Locator loc = page.locator(selector).first();
              try { loc.waitFor(new Locator.WaitForOptions().setTimeout(3000)); }
              catch (Exception e) { r.put("error", "Not found: " + e.getMessage()); results.add(r); continue; }

              try {
                BoundingBox bb = loc.boundingBox();
                if (bb != null) {
                  ObjectNode bbj = r.putObject("boundingBox");
                  bbj.put("x", bb.x); bbj.put("y", bb.y); bbj.put("width", bb.width); bbj.put("height", bb.height);
                }
              } catch (Exception ignored) {}

              try {
                String innerText = loc.innerText(new Locator.InnerTextOptions().setTimeout(2000));
                r.put("innerText", trim(innerText, 4000));
              } catch (Exception ignored) {}

              Path shot = shotsDir.resolve(String.format("el_%03d.png", idx));
              try {
                loc.screenshot(new Locator.ScreenshotOptions().setPath(shot));
                r.put("screenshot", outDir.relativize(shot).toString().replace("\\\\", "/"));
              } catch (Exception e) { r.put("screenshotError", e.getMessage()); }

              List<String> candidates = new ArrayList<>();
              if (!src.isBlank()) candidates.add(src);
              if (!href.isBlank() && ("a".equalsIgnoreCase(tag) || "link".equalsIgnoreCase(tag))) candidates.add(href);

              try {
                String nested = (String) loc.evaluate(\"\"\"
                  (el) => {
                    const pick = (u) => (u && typeof u === 'string') ? u : '';
                    const img = el.matches?.('img') ? el : el.querySelector?.('img');
                    if (img && img.src) return pick(img.currentSrc || img.src);
                    const v = el.matches?.('video') ? el : el.querySelector?.('video');
                    if (v) {
                      if (v.currentSrc) return pick(v.currentSrc);
                      if (v.src) return pick(v.src);
                      const s = v.querySelector?.('source'); if (s && s.src) return pick(s.src);
                    }
                    const s2 = el.querySelector?.('source'); if (s2 && s2.src) return pick(s2.src);
                    return '';
                  }
                \"\"\");
                if (nested != null && !nested.isBlank()) candidates.add(nested);
              } catch (Exception ignored) {}

              candidates = normalizeDedup(page.url(), candidates);

              ArrayNode downloads = r.putArray("downloads");
              for (String u : candidates) {
                ObjectNode d = OM.createObjectNode();
                d.put("url", u);
                try {
                  Path saved = download(ctx.request(), u, mediaDir, idx);
                  if (saved != null) d.put("savedAs", outDir.relativize(saved).toString().replace("\\\\", "/"));
                  else { d.put("savedAs", ""); d.put("note", "Empty body/unsupported"); }
                } catch (Exception ex) { d.put("error", ex.getMessage()); }
                downloads.add(d);
              }

              results.add(r);
            }

            ObjectNode manifest = OM.createObjectNode();
            manifest.put("capturedAt", OffsetDateTime.now().toString());
            manifest.put("pageUrl", page.url());
            manifest.put("videoEnabled", video);
            manifest.set("selections", selections);
            manifest.set("results", results);

            Path manifestPath = outDir.resolve("manifest.json");
            OM.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);

            System.out.println("Saved: " + manifestPath);
            ctx.close(); browser.close();
          }
        }

        private static boolean hasFlag(String[] args, String flag) {
          for (String a : args) if (flag.equalsIgnoreCase(a)) return true;
          return false;
        }

        private static String trim(String s, int max) {
          if (s == null) return "";
          s = s.replace("\\r", "").trim();
          return s.length() <= max ? s : s.substring(0, max) + "…";
        }

        private static List<String> normalizeDedup(String baseUrl, List<String> urls) {
          LinkedHashSet<String> out = new LinkedHashSet<>();
          for (String u : urls) {
            if (u == null) continue;
            u = u.trim();
            if (u.isEmpty()) continue;
            try { u = URI.create(baseUrl).resolve(u).toString(); } catch (Exception ignored) {}
            out.add(u);
          }
          return new ArrayList<>(out);
        }

        private static Path download(APIRequestContext req, String url, Path mediaDir, int idx) throws IOException {
          APIResponse resp = req.get(url, RequestOptions.create().setMaxRedirects(5));
          if (resp == null) return null;
          int st = resp.status();
          if (st < 200 || st >= 300) throw new IOException("HTTP " + st);
          byte[] body = resp.body();
          if (body == null || body.length == 0) return null;

          String ct = "";
          try { ct = resp.headers().getOrDefault("content-type", ""); } catch (Exception ignored) {}
          String ext = ext(ct, url);
          Path out = mediaDir.resolve(String.format("media_%03d%s", idx, ext));
          Files.write(out, body);
          return out;
        }

        private static String ext(String ct, String url) {
          String t = (ct == null ? "" : ct.toLowerCase(Locale.ROOT));
          if (t.contains("image/png")) return ".png";
          if (t.contains("image/jpeg") || t.contains("image/jpg")) return ".jpg";
          if (t.contains("image/webp")) return ".webp";
          if (t.contains("image/gif")) return ".gif";
          if (t.contains("video/mp4")) return ".mp4";
          if (t.contains("video/webm")) return ".webm";
          if (t.contains("application/pdf")) return ".pdf";
          try {
            String p = URI.create(url).getPath();
            int dot = p.lastIndexOf('.');
            if (dot >= 0 && dot > p.lastIndexOf('/')) {
              String e = p.substring(dot);
              if (e.length() <= 6) return e;
            }
          } catch (Exception ignored) {}
          return ".bin";
        }

        private static final String PICKER_JS = \"\"\"
          (() => {
            if (window.__aimPickerInstall) return;

            function cssEscape(s) {
              try { return CSS.escape(String(s)); }
              catch (e) { return String(s).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&"); }
            }

            function stableAttrSelector(el) {
              const attrs = ["data-testid","data-test-id","data-qa","data-id","aria-label","name","role"];
              for (const a of attrs) {
                const v = el.getAttribute && el.getAttribute(a);
                if (v && v.length <= 120) {
                  const tag = el.tagName.toLowerCase();
                  return `${tag}[${a}="${String(v).replace(/"/g, '\\\\\\"')}"]`;
                }
              }
              return "";
            }

            function buildSelector(el) {
              if (!(el instanceof Element)) return "";
              if (el.id) return `#${cssEscape(el.id)}`;

              const stable = stableAttrSelector(el);
              if (stable) return stable;

              const parts = [];
              let cur = el;
              while (cur && cur.nodeType === 1 && cur !== document.documentElement) {
                let part = cur.tagName.toLowerCase();

                const stable2 = stableAttrSelector(cur);
                if (stable2) { parts.unshift(stable2); break; }

                if (cur.classList && cur.classList.length) {
                  const cls = Array.from(cur.classList).slice(0, 2).map(c => "." + cssEscape(c)).join("");
                  if (cls) part += cls;
                }

                const parent = cur.parentElement;
                if (parent) {
                  const same = Array.from(parent.children).filter(x => x.tagName === cur.tagName);
                  if (same.length > 1) {
                    const idx = same.indexOf(cur) + 1;
                    part += `:nth-of-type(${idx})`;
                  }
                }

                parts.unshift(part);
                cur = cur.parentElement;
                if (parts.length >= 7) break;
              }
              return parts.join(" > ");
            }

            function textSnippet(el) {
              try {
                const t = (el.innerText || el.textContent || "").trim().replace(/\\s+/g, " ");
                return t.slice(0, 240);
              } catch (e) { return ""; }
            }

            function guessKind(el) {
              const tag = (el.tagName || "").toLowerCase();
              if (tag === "img") return "image";
              if (tag === "video") return "video";
              if (tag === "source") return "source";
              if (tag === "a") return "link";
              if (tag === "input" || tag === "textarea") return "input";
              return "element";
            }

            function getSrc(el) {
              try {
                const tag = (el.tagName || "").toLowerCase();
                if (tag === "img") return el.currentSrc || el.src || "";
                if (tag === "video") return el.currentSrc || el.src || "";
                if (tag === "source") return el.src || "";
                return "";
              } catch (e) { return ""; }
            }

            function getHref(el) {
              try {
                const tag = (el.tagName || "").toLowerCase();
                if (tag === "a") return el.href || "";
                return "";
              } catch (e) { return ""; }
            }

            function ensureUI() {
              if (document.getElementById("__aim_panel")) return;

              const style = document.createElement("style");
              style.textContent = `
                #__aim_panel{position:fixed;top:0;right:0;width:360px;height:100vh;background:rgba(15,15,18,.92);color:#fff;z-index:2147483647;font:12px system-ui;border-left:1px solid rgba(255,255,255,.08);display:flex;flex-direction:column}
                #__aim_hdr{padding:10px;display:flex;gap:8px;align-items:center;border-bottom:1px solid rgba(255,255,255,.08)}
                #__aim_title{font-weight:700;flex:1}
                #__aim_btns{display:flex;gap:6px}
                .__aim_btn{border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.06);color:#fff;padding:6px 8px;border-radius:8px;cursor:pointer;user-select:none}
                .__aim_btn:hover{background:rgba(255,255,255,.10)}
                #__aim_help{padding:8px 10px;color:rgba(255,255,255,.75);border-bottom:1px solid rgba(255,255,255,.08);line-height:1.25}
                #__aim_list{padding:10px;overflow:auto;flex:1}
                .__aim_item{border:1px solid rgba(255,255,255,.12);border-radius:10px;padding:8px;margin-bottom:8px;background:rgba(255,255,255,.04)}
                .__aim_row{display:flex;gap:8px;align-items:center}
                .__aim_k{color:rgba(255,255,255,.65);width:42px}
                .__aim_v{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
                .__aim_actions{display:flex;gap:6px;margin-top:6px}
                .__aim_small{font-size:11px;padding:4px 6px;border-radius:8px}
                #__aim_overlay{position:fixed;z-index:2147483646;pointer-events:none;border:2px solid #00aaff;background:rgba(0,170,255,.08)}
                #__aim_label{position:fixed;z-index:2147483646;pointer-events:none;padding:4px 6px;border-radius:8px;background:rgba(0,0,0,.72);color:#fff;max-width:calc(100vw - 380px);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
              `;
              document.documentElement.appendChild(style);

              const panel = document.createElement("div");
              panel.id = "__aim_panel";
              panel.innerHTML = `
                <div id="__aim_hdr">
                  <div id="__aim_title">Aim Picker — multi-select</div>
                  <div id="__aim_btns">
                    <div class="__aim_btn" id="__aim_finish">Finish (F9)</div>
                    <div class="__aim_btn" id="__aim_cancel">Cancel (Esc)</div>
                  </div>
                </div>
                <div id="__aim_help"><b>Click</b> add • <b>Backspace</b> undo • <b>F9</b> finish • <b>Esc</b> cancel</div>
                <div id="__aim_list"></div>
              `;
              document.documentElement.appendChild(panel);

              const overlay = document.createElement("div"); overlay.id="__aim_overlay"; document.documentElement.appendChild(overlay);
              const label = document.createElement("div"); label.id="__aim_label"; document.documentElement.appendChild(label);

              panel.querySelector("#__aim_finish").addEventListener("click", () => window.__aimPickerDone = true);
              panel.querySelector("#__aim_cancel").addEventListener("click", () => window.__aimPickerCancel = true);
            }

            function escHtml(s){return String(s||"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")}
            function escAttr(s){return String(s||"").replace(/"/g,"&quot;")}

            function renderList() {
              const list = document.getElementById("__aim_list");
              if (!list) return;
              list.innerHTML = "";
              (window.__aimSelections || []).forEach((it, i) => {
                const div = document.createElement("div");
                div.className = "__aim_item";
                div.innerHTML = `
                  <div class="__aim_row"><div class="__aim_k">#</div><div class="__aim_v">${i+1}</div></div>
                  <div class="__aim_row"><div class="__aim_k">tag</div><div class="__aim_v">${escHtml(it.tag||"")}</div></div>
                  <div class="__aim_row"><div class="__aim_k">kind</div><div class="__aim_v">${escHtml(it.kind||"")}</div></div>
                  <div class="__aim_row"><div class="__aim_k">sel</div><div class="__aim_v" title="${escAttr(it.selector||"")}">${escHtml(it.selector||"")}</div></div>
                  <div class="__aim_row"><div class="__aim_k">text</div><div class="__aim_v" title="${escAttr(it.text||"")}">${escHtml(it.text||"")}</div></div>
                  <div class="__aim_actions">
                    <div class="__aim_btn __aim_small" data-act="rm">Remove</div>
                    <div class="__aim_btn __aim_small" data-act="go">Scroll</div>
                  </div>
                `;
                div.querySelector('[data-act="rm"]').addEventListener("click", () => {
                  window.__aimSelections.splice(i, 1);
                  renderList();
                });
                div.querySelector('[data-act="go"]').addEventListener("click", () => {
                  try { const el = document.querySelector(it.selector); if (el) el.scrollIntoView({behavior:"smooth", block:"center"}); } catch(e){}
                });
                list.appendChild(div);
              });
            }

            window.__aimPickerInstall = () => {
              if (window.__aimPickerActive) return;
              window.__aimPickerActive = true;

              window.__aimSelections = window.__aimSelections || [];
              window.__aimPickerDone = false;
              window.__aimPickerCancel = false;

              ensureUI();
              renderList();

              const overlay = document.getElementById("__aim_overlay");
              const label = document.getElementById("__aim_label");
              const panel = document.getElementById("__aim_panel");

              function onMove(e){
                const el = document.elementFromPoint(e.clientX, e.clientY);
                if (!el || el === overlay || el === label || (panel && panel.contains(el))) return;
                const r = el.getBoundingClientRect();
                overlay.style.left=r.left+"px"; overlay.style.top=r.top+"px"; overlay.style.width=r.width+"px"; overlay.style.height=r.height+"px";
                const sel = buildSelector(el);
                label.textContent = sel;
                label.style.left = Math.max(0, Math.min(r.left, window.innerWidth - 380)) + "px";
                label.style.top = Math.max(0, r.top - 26) + "px";
              }

              function onClick(e){
                const el = document.elementFromPoint(e.clientX, e.clientY);
                if (!el || (panel && panel.contains(el))) return;
                e.preventDefault(); e.stopPropagation();
                const selector = buildSelector(el);
                if (!selector) return;
                window.__aimSelections.push({
                  selector,
                  tag: (el.tagName||"").toLowerCase(),
                  kind: guessKind(el),
                  text: textSnippet(el),
                  src: getSrc(el),
                  href: getHref(el)
                });
                renderList();
              }

              function onKey(e){
                if (e.key === "Escape") { window.__aimPickerCancel = true; e.preventDefault(); e.stopPropagation(); }
                else if (e.key === "F9") { window.__aimPickerDone = true; e.preventDefault(); e.stopPropagation(); }
                else if (e.key === "Backspace") {
                  if (window.__aimSelections.length) { window.__aimSelections.pop(); renderList(); }
                  e.preventDefault(); e.stopPropagation();
                }
              }

              document.addEventListener("mousemove", onMove, true);
              document.addEventListener("click", onClick, true);
              document.addEventListener("keydown", onKey, true);
            };
          })();
        \"\"\";
      }
      """;
  }
}