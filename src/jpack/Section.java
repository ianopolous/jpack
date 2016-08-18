package jpack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.regex.*;

public interface Section {

    String getContents(BiFunction<String, String, String> transformer);

    class StringSection implements Section {
        private final String content;

        public StringSection(String content) {
            this.content = content;
        }

        @Override
        public String getContents(BiFunction<String, String, String> transformer) {
            return content;
        }
    }

    class RequireSection implements Section {
        public final Path file;

        public RequireSection(Path file) {
            this.file = file;
        }

        @Override
        public String getContents(BiFunction<String, String, String> transformer) {
            return transformer.apply(file.toFile().getName(), parse(file).getContents(transformer));
        }
    }

    class ListSection implements Section {
        public final List<Section> sections;

        public ListSection(List<Section> sections) {
            this.sections = sections;
        }

        @Override
        public String getContents(BiFunction<String, String, String> transformer) {
            return sections.stream().map(s -> s.getContents(transformer)).reduce("", (a, b) -> a + b);
        }
    }

    static Section parse(Path root) {
        String requirePattern = "require\\(([\"\\./a-zA-Z0-9\\-_]+)\\)[;]*";
        Pattern pat = Pattern.compile(requirePattern);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(root.toFile()));
            String line;
            List<Section> sections = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                Matcher m = pat.matcher(line);

                if (m.find()) {
                    int startIndex = m.start(1);
                    current.append(line.substring(0, startIndex));
                    if (current.length() > 0) {
                        sections.add(new StringSection(current.toString()));
                        current = new StringBuilder();
                    }

                    sections.add(new RequireSection(root.resolve(m.group(1))));

                    if (line.length() > m.end(1))
                        current.append(line.substring(m.end(1)));

                }
                current.append(line);
            }
            if (current.length() > 0)
                sections.add(new StringSection(current.toString()));
            return new ListSection(sections);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}