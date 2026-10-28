package fi.csc.avaa.paituli.download.io;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
//import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class FileSizeOperations {

    public long count(List<String> absolutePaths) {
        return absolutePaths.stream().map(p -> sizeOf(p)).collect(Collectors.summingLong(Long::longValue));
    }

    long sizeOf(String path){
        File f = Paths.get(path).toFile();
        long size = 0;
        if (f.isDirectory())
            size = sizeOfDir(f);
        else {
            size = f.length();
        }
        return size;
    }

    long sizeOfDir(File f) {
         try (Stream<String> subPaths =  Arrays.stream(f.list())) {
             return subPaths.map(pp-> childPath(f, pp)).map(p -> sizeOf(p.toString())).collect(Collectors.summingLong(Long::longValue));
         }
    }

    String childPath(File f, String pp) {
        return f.getAbsolutePath() + "/" + pp;
    }
}
