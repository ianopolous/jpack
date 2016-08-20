package jpack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

public class Main {

    public static final String PREFIX = "// modules are defined as an array\n" +
            "// [ module function, map of requires ]\n" +
            "//\n" +
            "// map of requires is short require name -> numeric require\n" +
            "//\n" +
            "// anything defined in a previous bundle is accessed via the\n" +
            "// orig method which is the require for previous bundles\n" +
            "\n" +
            "(function outer (modules, cache, entry) {\n" +
            "    // Save the require from previous bundle to this closure if any\n" +
            "    var previousRequire = typeof require == \"function\" && require;\n" +
            "\n" +
            "    function newRequire(name, jumped){\n" +
            "        if(!cache[name]) {\n" +
            "            if(!modules[name]) {\n" +
            "                // if we cannot find the the module within our internal map or\n" +
            "                // cache jump to the current global require ie. the last bundle\n" +
            "                // that was added to the page.\n" +
            "                var currentRequire = typeof require == \"function\" && require;\n" +
            "                if (!jumped && currentRequire) return currentRequire(name, true);\n" +
            "\n" +
            "                // If there are other bundles on this page the require from the\n" +
            "                // previous one is saved to 'previousRequire'. Repeat this as\n" +
            "                // many times as there are bundles until the module is found or\n" +
            "                // we exhaust the require chain.\n" +
            "                if (previousRequire) return previousRequire(name, true);\n" +
            "                var err = new Error('Cannot find module \\'' + name + '\\'');\n" +
            "                err.code = 'MODULE_NOT_FOUND';\n" +
            "                throw err;\n" +
            "            }\n" +
            "            var m = cache[name] = {exports:{}};\n" +
            "            modules[name][0].call(m.exports, function(x){\n" +
            "                var id = modules[name][1][x];\n" +
            "                return newRequire(id ? id : x);\n" +
            "            },m,m.exports,outer,modules,cache,entry);\n" +
            "        }\n" +
            "        return cache[name].exports;\n" +
            "    }\n" +
            "    for(var i=0;i<entry.length;i++) newRequire(entry[i]);\n" +
            "\n" +
            "    // Override the current require with this new one\n" +
            "    return newRequire;\n" +
            "})";

    public static void main(String[] cmd) throws IOException {
        Args args = Args.parse(cmd);
        Path root = new File(args.getArg("root")).toPath();
        Path out = new File(args.getArg("out")).toPath();


        BiFunction<String, String, String> transformer = (filename, contents) ->
                (! filename.endsWith(".html") ? contents :
                        "module.exports = \"" + contents.replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n") + "\";\n");

        Set<File> vendorRoots = Stream.of("vendor/dev","vendor/min" , "vendor/common")
                .map(File::new)
                .collect(Collectors.toSet());

        Function<String, String> nameExtractor = filename -> filename.contains(".") ?
                filename.substring(0, filename.indexOf(".")) :
                filename;
        Set<String> vendor = vendorRoots.stream()
                .flatMap(f -> Stream.of(f.listFiles()).map(g -> nameExtractor.apply(g.getName())))
                .collect(Collectors.toSet());

        Map<Path, Source> sources = Source.parseSourceTree(root, vendor);

        // only overwrite file when we know there are no exceptions
        BufferedWriter writer = new BufferedWriter(new FileWriter(out.toFile()));

        writer.write(PREFIX);
        writer.write("({");
        writer.write(sources.values().stream()
                .map(s -> s.getContents(transformer))
                .reduce("", (a, b) -> a +", " + b)
                .substring(1));
        writer.write("},{},");
        writer.write("[" + sources.get(root).getId() + "]");
        writer.write(");");

        writer.flush();
        writer.close();
    }
}