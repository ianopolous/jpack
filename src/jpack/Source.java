package jpack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.regex.*;

import org.graalvm.polyglot.*;

public class Source {
    private final int id;
    private final String contents;
    private final Path file;
    private final Map<String, String> dependencies;

    public Source(int id, String contents, Path file, Map<String, String> dependencies) {
        this.id = id;
        this.contents = contents;
        this.file = file;
        this.dependencies = new TreeMap(dependencies);
    }

    public String getContents(BiFunction<String, String, String> transformer) {
        return id + ":[function(require,module,exports){" + transformer.apply(file.toFile().getName(), contents) + "},{"
                + (dependencies.isEmpty() ? "" : dependencies.entrySet().stream()
                .map(e -> "\""+e.getKey() + "\": "+e.getValue()+"")
                .reduce("", (a, b) -> a + ", " + b)
                .substring(1)) +
                "}]";
    }

    public int getId() {
        return id;
    }

    private static List<String> compileTemplate(Path htmlFile) throws IOException {
        String template = Files.readString(htmlFile);
        return compileTemplate(template);
    }

    private static List<String> compileTemplate(String template) throws IOException {
        try {
            Context js = Context.create("js");
            js.eval(org.graalvm.polyglot.Source.newBuilder("js", Paths.get("template-compiler.js").toFile()).build());
            Value compileFunc = js.getBindings("js").getMember("compile");
            Value compiled = compileFunc.execute(template);
            String render = compiled.getMember("render").asString();
            Value staticRenderFns = compiled.getMember("staticRenderFns");
            List<String> res = new ArrayList<>();
            res.add(render);
            if (staticRenderFns.hasArrayElements()) {
                for (int i=0; i < staticRenderFns.getArraySize(); i++)
                    res.add(staticRenderFns.getArrayElement(i).asString());
            }
            return res;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readTemplate(Path htmlFile) {
        try {
            return Files.readString(htmlFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void parseCss(BufferedReader reader, StringBuilder cssOutput) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().equals("</style>"))
                return;
            line = line + "\n";
            cssOutput.append(line);
        }
    }

    private static String readInlineTemplate(BufferedReader reader) throws IOException {
        StringBuilder res = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().equals("</template>"))
                return res.toString();
            line = line + "\n";
            res.append(line);
        }
        return res.toString();
    }

    private static void renderCompiledTemplate(List<String> compiled, StringBuilder current) {
        current.append("render: function() {");
        current.append(compiled.get(0));
        current.append("}");
        if (compiled.size() > 1) {
            current.append(",staticRenderFns: [");
            for (int i=1; i < compiled.size(); i++) {
                current.append("function() {" + compiled.get(i) + "},");
            }
            current.append("]");
        }
    }
    
    private static Source parseSourceTree(Path root,
                                          AtomicInteger nextLabel,
                                          Map<Path, Source> existing,
                                          Set<String> vendor,
                                          boolean compileTemplates,
                                          StringBuilder cssOutput) {
        Path p = root;
        if (root.toFile().isDirectory())
            root = root.resolve("index.js");
        if (vendor.contains(p.toFile().getName()))
            return null;
        boolean isComponent = root.toFile().getName().endsWith(".vue");

        String requirePattern = "require\\([\"']([\\./a-zA-Z0-9\\-_]+)[\"']\\)[;]*";
        Pattern pat = Pattern.compile(requirePattern);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(root.toFile()));
            String line, template = null;
            StringBuilder current = new StringBuilder();
            Map<String, String> deps = new HashMap<>();
            boolean inScript = false;
            while ((line = reader.readLine()) != null) {
                line = line + "\n";
                if (isComponent && line.trim().startsWith("<style")) {
                    boolean scoped = line.contains("scoped");
                    parseCss(reader, cssOutput);
                    continue;
                }
                if (isComponent && line.trim().startsWith("<template")) {
                    template = readInlineTemplate(reader);
                    continue;
                }
                if (isComponent && line.trim().startsWith("<script")) {
                    inScript = true;
                    continue;
                }
                if (isComponent && line.trim().equals("</script>")) {
                    inScript = false;
                    continue;
                }
                Matcher m = pat.matcher(line);

                if (!m.find()) {
                    current.append(line);
                    if (isComponent && line.trim().startsWith("module.exports")) {
                        if (template == null)
                            throw new IllegalStateException("template needs to be defined before <script> in " + root);
                        if (compileTemplates) {
                            List<String> compiled = compileTemplate(template);
                            renderCompiledTemplate(compiled, current);
                            current.append(",");
                        } else {
                            String name = root.toFile().getName() + ".template";
                            Path templatePath = root.getParent().resolve(name);
                            Source tmpl = new Source(nextLabel.getAndIncrement(), template, templatePath, Collections.emptyMap());
                            existing.put(templatePath, tmpl);
                            current.append("template: require('");
                            current.append(name);
                            current.append("'),");
                        }
                    }
                } else while (true) {
                    int startIndex = m.start(0);
                    String prior = line.substring(0, startIndex);
                    boolean isTemplate = prior.endsWith("template: ");

                    if (root.getParent() == null)
                        System.out.println("null parent of " + root);
                    
                    if (isTemplate && compileTemplates) {
                        current.append(line.substring(0, startIndex - 10));
                        List<String> compiled = compileTemplate(root.getParent().resolve(m.group(1)));
                        renderCompiledTemplate(compiled, current);
                    } else {
                        current.append(prior);
                        Source source = parseSourceTree(root.getParent().resolve(m.group(1)), nextLabel, existing, vendor, compileTemplates, cssOutput);
                        deps.put(m.group(1), Integer.toString(source.id));
                        current.append(m.group());
                    }

                    if (line.length() > m.end(0)) {
                        // handle remainder of line (could be long in minified js)
                        line = line.substring(m.end(0));
                        m = pat.matcher(line);
                        if (m.find())
                            continue;
                        else {
                            current.append(line);
                            break;
                        }
                    } else break;
                }
            }
            Source result = new Source(nextLabel.getAndIncrement(), current.toString(), root, deps);
            existing.put(root, result);
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<Path, Source> parseSourceTree(Path root, Set<String> vendor, boolean compileTemplates, StringBuilder cssOutput) {
        // can't start at 0 because JS is mental
        AtomicInteger startLabel = new AtomicInteger(1);
        Map<Path, Source> res = new TreeMap<>();
        parseSourceTree(root, startLabel, res, vendor, compileTemplates, cssOutput);
        return res;
    }
}
