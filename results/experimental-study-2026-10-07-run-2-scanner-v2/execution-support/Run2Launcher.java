import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import dev.kidocolors.backend.KidoColorsApplication;
import dev.kidocolors.backend.scanner.ScannerSettings;
import org.springframework.boot.SpringApplication;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;

/** Experiment support only: does not change or execute the scanner during preflight. */
public class Run2Launcher {
    static final ObjectMapper JSON = new ObjectMapper();
    static final UUID PILOT = UUID.fromString("8411729c-fa79-4356-b6f9-bc0972fab79f");
    static final String NAME = "Run 2 oficial - Scanner V2 - 100 sites - 2026-10-07";
    static final Path OUT = Path.of(System.getenv("RUN2_OUTPUT_PATH"));
    static void save(String file, Object value) throws Exception {
        Path path=OUT.resolve(file);
        if (Files.exists(path)) throw new IllegalStateException("Output already exists: " + file);
        Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8);
    }
    static Connection connection() throws Exception {
        Class.forName("org.postgresql.Driver");
        Connection c=DriverManager.getConnection(System.getenv("DB_URL"),System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"));
        c.setReadOnly(true); return c;
    }
    static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    static Map<String,Object> snapshot(Connection c) throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();
        var queries=Map.of(
            "study", "select id::text, to_jsonb(s)::text from study_run s where id=? order by id",
            "analyses", "select id::text, (to_jsonb(a)-'scanner_diagnostics_json')::text from analysis a where study_run_id=? order by id",
            "issues", "select i.id::text, to_jsonb(i)::text from accessibility_issue i join analysis a on a.id=i.analysis_id where a.study_run_id=? order by i.id");
        for (var entry:queries.entrySet()) {
            Map<String,String> hashes=new TreeMap<>();
            try(var statement=c.prepareStatement(entry.getValue())) {
                statement.setObject(1,PILOT);
                try(var rows=statement.executeQuery()) {while(rows.next()) hashes.put(rows.getString(1),digest(rows.getString(2)));}
            }
            result.put(entry.getKey(),hashes);
        }
        return result;
    }
    static int existingCount(Connection c) throws Exception {
        try(var s=c.prepareStatement("select count(*) from study_run where name=?")) {
            s.setString(1,NAME);try(var r=s.executeQuery()) {r.next();return r.getInt(1);}
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length>0 && args[0].equals("--snapshot-only")) {
            try(var c=connection()) {save("run1-database-after.json",snapshot(c));}
            System.out.println("Run 1 database snapshot preserved for integrity verification.");return;
        }
        if (args.length>0 && args[0].equals("--discover")) {
            try(var c=connection();var s=c.prepareStatement("select id,status,total_rows from study_run where name=?")) {
                s.setString(1,NAME);
                try(var r=s.executeQuery()) {
                    if (!r.next()) {System.out.println("Run 2 not yet inserted.");return;}
                    var ref=Map.of("study_id",r.getString(1));
                    String status=r.getString(2);int count=r.getInt(3);
                    if(r.next())throw new IllegalStateException("Duplicate Run 2 records");
                    save("study-reference.json",ref);
                    System.out.println("Run 2 reference preserved; status="+status+"; planned="+count);
                }
            }return;
        }
        try(var c=connection()) {
            if(existingCount(c)!=0)throw new IllegalStateException("Run 2 already exists; do not POST again.");
            var state=snapshot(c);
            if(((Map<?,?>)state.get("analyses")).size()!=100)throw new IllegalStateException("Unexpected pilot record count");
            save("run1-database-before.json",state);
        }
        var context=SpringApplication.run(KidoColorsApplication.class,args);
        var settings=context.getBean(ScannerSettings.class);
        Map<String,Object> config=new LinkedHashMap<>();
        config.put("scannerProtocol","scanner-v2");config.put("navigationWaitUntil","DOMCONTENTLOADED");
        config.put("timeoutMs",settings.getTimeoutMs());config.put("readinessTimeoutMs",settings.getReadinessTimeoutMs());
        config.put("settleMs",settings.getSettleMs());config.put("maxPageHeight",settings.getMaxPageHeight());
        config.put("maxElements",settings.getMaxElements());config.put("viewportWidth",1280);config.put("viewportHeight",720);config.put("maxRedirects",5);
        Map<?,?> manifest=JSON.readValue(OUT.resolve("manifest.json").toFile(),Map.class);
        if(!config.equals(manifest.get("expected_configuration"))) {context.close();throw new IllegalStateException("Effective protocol mismatch");}
        Map<String,Object> effective=new LinkedHashMap<>();effective.put("configuration",config);
        try(var c=context.getBean(javax.sql.DataSource.class).getConnection()) {
            String product=c.getMetaData().getDatabaseProductName();
            String url=c.getMetaData().getURL();
            boolean supabase=java.net.URI.create(url.substring(5)).getHost().endsWith("supabase.com")
                    || java.net.URI.create(url.substring(5)).getHost().endsWith("supabase.co");
            if(!"PostgreSQL".equals(product)||!supabase||!url.equals(System.getenv("DB_URL")))
                {context.close();throw new IllegalStateException("Unexpected database connection");}
            effective.put("database_product",product);effective.put("supabase_host_verified",supabase);
            try(var s=c.createStatement();var r=s.executeQuery("select 1")){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Database readiness failed");}
            effective.put("existing_run2_count",existingCount(c));
        }
        // Browser installation probe: no page is created, no URL is visited, no analysis is produced.
        try(var p=Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD","1")));
            var browser=p.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true).setTimeout(30000))) {
            effective.put("browser_version",browser.version());
        }
        effective.put("capture_storage_separate",settings.getStoragePath().equals(System.getenv("RUN2_CAPTURE_PATH")));
        if(!Boolean.TRUE.equals(effective.get("capture_storage_separate"))) {context.close();throw new IllegalStateException("Capture storage mismatch");}
        effective.put("active_profiles",List.of(context.getEnvironment().getActiveProfiles()));
        effective.put("ready_at_utc",java.time.Instant.now().toString());
        save("effective-config-before-run.json",effective);
        System.out.println("RUN2_PREFLIGHT_READY: PostgreSQL/Supabase UP; approved settings verified; browser installed; no site visited.");
    }
}
