package jpack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.regex.*;

public interface Section {

    /**
     *
     * @param vendor the set of vendor library names
     * @param transformer applied to the contents of files based on their filename (e.g. stringify .html)
     * @return
     */
    String getContents(Set<String> vendor, BiFunction<String, String, String> transformer);

    class StringSection implements Section {
        private final String content;

        public StringSection(String content) {
            this.content = content;
        }

        @Override
        public String getContents(Set<String> vendor, BiFunction<String, String, String> transformer) {
            return content;
        }
    }

    class RequireSection implements Section {
        public final Path file;

        public RequireSection(Path file) {
            this.file = file;
        }

        @Override
        public String getContents(Set<String> vendor, BiFunction<String, String, String> transformer) {
            Path p = file;
            if (file.toFile().isDirectory())
                p = file.resolve("index.js");
            if (vendor.contains(file.toFile().getName()))
                return "";
            return transformer.apply(p.toFile().getName(), parse(p).getContents(vendor, transformer));
        }
    }

    class ListSection implements Section {
        public final List<Section> sections;

        public ListSection(List<Section> sections) {
            this.sections = sections;
        }

        @Override
        public String getContents(Set<String> vendor, BiFunction<String, String, String> transformer) {
            return sections.stream().map(s -> s.getContents(vendor, transformer)).reduce("", (a, b) -> a + b);
        }
    }

    static Section parse(Path root) {
        String requirePattern = "require\\([\"']([\\./a-zA-Z0-9\\-_]+)[\"']\\)[;]*";
        Pattern pat = Pattern.compile(requirePattern);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(root.toFile()));
            String line;
            List<Section> sections = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                line = line + "\n";
                Matcher m = pat.matcher(line);

                if (!m.find())
                    current.append(line);
                else while (true) {
                    int startIndex = m.start(0);
                    current.append(line.substring(0, startIndex));
                    if (current.length() > 0) {
                        sections.add(new StringSection(current.toString()));
                        current = new StringBuilder();
                    }

                    sections.add(new RequireSection(root.subpath(0, root.getNameCount() - 1).resolve(m.group(1))));

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
            if (current.length() > 0)
                sections.add(new StringSection(current.toString()));
            return new ListSection(sections);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}