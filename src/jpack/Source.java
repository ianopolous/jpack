package jpack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.regex.*;

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

    private static Source parseSourceTree(Path root, AtomicInteger nextLabel, Map<Path, Source> existing, Set<String> vendor) {
        Path p = root;
        if (root.toFile().isDirectory())
            root = root.resolve("index.js");
        if (vendor.contains(p.toFile().getName()))
            return null;

        String requirePattern = "require\\([\"']([\\./a-zA-Z0-9\\-_]+)[\"']\\)[;]*";
        Pattern pat = Pattern.compile(requirePattern);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(root.toFile()));
            String line;
            StringBuilder current = new StringBuilder();
            Map<String, String> deps = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                line = line + "\n";
                Matcher m = pat.matcher(line);

                if (!m.find())
                    current.append(line);
                else while (true) {
                    int startIndex = m.start(0);
                    current.append(line.substring(0, startIndex));

                    Source source = parseSourceTree(root.subpath(0, root.getNameCount() - 1).resolve(m.group(1)), nextLabel, existing, vendor);
                    deps.put(m.group(1), Integer.toString(source.id));
                    current.append(m.group());

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

    public static Map<Path, Source> parseSourceTree(Path root, Set<String> vendor) {
        // can't start at 0 because JS is mental
        AtomicInteger startLabel = new AtomicInteger(1);
        Map<Path, Source> res = new TreeMap<>();
        parseSourceTree(root, startLabel, res, vendor);
        return res;
    }
}
