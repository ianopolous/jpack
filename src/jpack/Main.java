package jpack;

import java.io.*;
import java.nio.file.*;
import java.util.function.*;

public class Main {

    public static void main(String[] a) throws IOException {
        Args args = Args.parse(a);
        Path root = new File(args.getArg("root")).toPath();
        Path out = new File(args.getArg("out")).toPath();
        Section tree = Section.parse(root);

        BiFunction<String, String, String> transformer = (filename, contents) ->
                ! filename.endsWith(".html") ? contents :
                        contents.replaceAll("\"", "\\\"");

        BufferedWriter writer = new BufferedWriter(new FileWriter(out.toFile()));
        writer.write(tree.getContents(transformer));
        writer.flush();
        writer.close();
    }
}