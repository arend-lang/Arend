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

  public FileListErrorReporter(String dir) {
    myErrorList = new ArrayList<>();
    Path dirPath = Paths.get(dir, ".junieCommunication");

    try {
      Files.createDirectories(dirPath);
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
}