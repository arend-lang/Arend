package org.arend.frontend;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;

/**
 * The terminal-aware help-rendering engine: detects the terminal width and word-wraps
 * help text and the grouped option listing to it. Pure formatting — it holds no help
 * <em>content</em> (that lives in {@link ConsoleHelp} and, per tool, in each tool's help
 * text), so the two concerns can change independently.
 */
final class ConsoleHelpRenderer {
  private ConsoleHelpRenderer() {}

  static int detectTerminalWidth() {
    String env = System.getenv("COLUMNS");
    if (env != null) {
      try { int width = Integer.parseInt(env.trim()); if (width >= 40) return Math.min(width, 200); }
      catch (NumberFormatException ignored) {}
    }
    if (System.console() != null) {
      try {
        Process process = new ProcessBuilder("stty", "size").redirectErrorStream(true)
            .redirectInput(new File("/dev/tty")).start();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
          String line = reader.readLine();
          process.waitFor();
          if (line != null) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
              int width = Integer.parseInt(parts[1]);
              if (width >= 40) return Math.min(width, 200);
            }
          }
        }
      } catch (Exception ignored) {}
    }
    return 100;
  }

  /** Prints a whole topic help text, word-wrapping each line to the terminal width while preserving its indent. */
  static void printTopicHelp(String text) {
    int width = detectTerminalWidth();
    for (String line : text.split("\n", -1)) {
      if (line.length() <= width) {
        System.out.println(line);
        continue;
      }
      int leading = 0;
      while (leading < line.length() && line.charAt(leading) == ' ') leading++;
      String indent = " ".repeat(leading);
      String rest = line.substring(leading);
      int budget = Math.max(20, width - leading);
      while (rest.length() > budget) {
        int cut = rest.lastIndexOf(' ', budget);
        if (cut <= 0) cut = budget;
        System.out.println(indent + rest.substring(0, cut));
        rest = rest.substring(cut).stripLeading();
      }
      if (!rest.isEmpty()) System.out.println(indent + rest);
    }
  }

  /** The description column indent: the widest option label (capped), so descriptions line up. */
  static int computeIndent(Options cmdOptions, int width) {
    int max = 0;
    for (Option opt : cmdOptions.getOptions()) {
      max = Math.max(max, renderLabel(opt).length());
    }
    int gap = 2;
    int cap = Math.min(width / 2, 50);
    return Math.max(30, Math.min(max + gap, cap));
  }

  /** The left-hand label for an option, e.g. {@code "  -L, --libdir <dir>"}. */
  static String renderLabel(Option opt) {
    StringBuilder builder = new StringBuilder("  ");
    if (opt.getOpt() != null) builder.append("-").append(opt.getOpt()).append(", ");
    else builder.append("    ");
    builder.append("--").append(opt.getLongOpt());
    if (opt.hasArg()) {
      String argName = opt.getArgName() == null ? "arg" : opt.getArgName();
      if (opt.hasOptionalArg()) builder.append(" [").append(argName).append("]");
      else builder.append(" <").append(argName).append(">");
    }
    return builder.toString();
  }

  /** Prints a titled group of options (label + wrapped description), recording each rendered long-opt in {@code placed}. */
  static void printGroup(Options cmdOptions, String title, List<String> longOpts, Set<String> placed, int width, int indent) {
    printWrapped(title, 0, width);
    for (String longOpt : longOpts) {
      Option opt = cmdOptions.getOption("--" + longOpt);
      if (opt == null) {
        for (Option candidate : cmdOptions.getOptions()) {
          if (longOpt.equals(candidate.getLongOpt())) { opt = candidate; break; }
        }
      }
      if (opt == null) continue;
      placed.add(longOpt);

      StringBuilder label = new StringBuilder(renderLabel(opt));
      String desc = opt.getDescription();
      if (desc == null || desc.isEmpty()) {
        System.out.println(label);
      } else if (label.length() >= indent) {
        System.out.println(label);
        printWrapped(desc, indent, width);
      } else {
        while (label.length() < indent) label.append(' ');
        label.append(wrapFirstLine(desc, indent, width));
        System.out.println(label);
        int firstLen = width - indent;
        if (desc.length() > firstLen) {
          String rest = remainingAfterFirstLine(desc, firstLen);
          if (!rest.isEmpty()) printWrapped(rest, indent, width);
        }
      }
    }
    System.out.println();
  }

  private static String wrapFirstLine(String text, int indent, int wrapAt) {
    int budget = wrapAt - indent;
    if (text.length() <= budget) return text;
    int cut = text.lastIndexOf(' ', budget);
    if (cut <= 0) cut = budget;
    return text.substring(0, cut);
  }

  private static String remainingAfterFirstLine(String text, int budget) {
    if (text.length() <= budget) return "";
    int cut = text.lastIndexOf(' ', budget);
    if (cut <= 0) cut = budget;
    return text.substring(cut).stripLeading();
  }

  /** Prints {@code text} word-wrapped to {@code wrapAt}, each line prefixed with {@code indent} spaces. */
  static void printWrapped(String text, int indent, int wrapAt) {
    String pad = " ".repeat(indent);
    int budget = wrapAt - indent;
    String rest = text;
    while (rest.length() > budget) {
      int cut = rest.lastIndexOf(' ', budget);
      if (cut <= 0) cut = budget;
      System.out.println(pad + rest.substring(0, cut));
      rest = rest.substring(cut).stripLeading();
    }
    if (!rest.isEmpty()) System.out.println(pad + rest);
  }
}
