package jpack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class Main {

    public static void main(String[] a) throws IOException {
        Args args = Args.parse(a);
        Path root = new File(args.getArg("root")).toPath();
        Path out = new File(args.getArg("out")).toPath();
        Section tree = Section.parse(root);

        BiFunction<String, String, String> transformer = (filename, contents) ->
                ! filename.endsWith(".html") ? contents :
                        "\"" + contents.replaceAll("\"", "\\\\\"") + "\"";

        Set<File> vendorRoots = Stream.of("vendor/dev","vendor/min" , "vendor/common")
                .map(File::new)
                .collect(Collectors.toSet());

        Function<String, String> nameExtractor = filename -> filename.contains(".") ?
                filename.substring(0, filename.indexOf(".")) :
                filename;
        Set<String> vendor = vendorRoots.stream()
                .flatMap(f -> Stream.of(f.listFiles()).map(g -> nameExtractor.apply(g.getName())))
                .collect(Collectors.toSet());

        String contents = tree.getContents(vendor, transformer);
        // only overwrite file when we know there are no exceptions
        BufferedWriter writer = new BufferedWriter(new FileWriter(out.toFile()));
        writer.write(contents);
        writer.flush();
        writer.close();
    }
}