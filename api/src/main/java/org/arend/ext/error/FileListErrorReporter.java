package org.arend.ext.error;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;


public class FileListErrorReporter extends ListErrorReporter implements ErrorReporter{
  final private List<GeneralError> myErrorList;
//  private List<GeneralError> myCurrentErrorList;
  private final Path filePath;
  private static final String JUNIE_COMMUNICATION_FOLDER = ".junieCommunication";

  public FileListErrorReporter(String dir) {
    myErrorList = new ArrayList<>();
    Path dirPath = Paths.get(dir, JUNIE_COMMUNICATION_FOLDER);

    try {
      Files.createDirectories(dirPath);
      ensureGitignore(dir);
      Path filePath = dirPath.resolve("errorFile.txt");
      if (!Files.exists(filePath)) {
        Files.createFile(filePath);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    filePath = Path.of(dir + "/.junieCommunication/errorFile.txt");
    try {
      if (Files.notExists(filePath)) {
        Files.createFile(filePath);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void report(GeneralError error) {
    myErrorList.add(error);

    try {
      Files.createDirectories(filePath.getParent());
      if (Files.notExists(filePath)) {
        Files.createFile(filePath);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    String text = error.toString() + "\n";
    try {
      Files.write(
        filePath,
        text.getBytes(),
        StandardOpenOption.APPEND
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public List<GeneralError> getErrorList() {
    return myErrorList;
  }

  public void reportTo(ErrorReporter errorReporter) {
    for (GeneralError error : myErrorList) {
    errorReporter.report(error);
  }
  }

  private static void ensureGitignore(String dir) {
    Path gitignorePath = Paths.get(dir, ".gitignore");
    String entry = JUNIE_COMMUNICATION_FOLDER;
    try {
      if (Files.exists(gitignorePath)) {
        String content = Files.readString(gitignorePath);
        if (!content.contains(entry)) {
          String newContent = content.endsWith("\n") ? content + entry + "\n" : content + "\n" + entry + "\n";
          Files.writeString(gitignorePath, newContent);
        }
      } else {
        Files.writeString(gitignorePath, entry + "\n");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}