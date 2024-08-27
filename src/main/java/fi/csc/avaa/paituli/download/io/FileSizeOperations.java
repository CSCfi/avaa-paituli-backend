package fi.csc.avaa.paituli.download.io;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
//import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class FileSizeOperations {

    public long count(List<String> absolutePaths) {
        return absolutePaths.stream().map(p -> laske(p)).collect(Collectors.summingLong(Long::longValue));
    }

    long laske(String path){
        File f = Paths.get(path).toFile();
        //System.out.println(f.getAbsolutePath());
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
             return subPaths.map(pp-> täydennä(f, pp)).map(p -> laske(p.toString())).collect(Collectors.summingLong(Long::longValue));
         }
    }

    String täydennä(File f, String pp) {
        return f.getAbsolutePath() + "/" + pp;
    }

    public static void main(String[] args) {
        List<String> absolutePaths = new ArrayList<String>();
        absolutePaths.add(args[0]);
        System.out.println(args[0]);
        FileSizeOperations fso = new FileSizeOperations();
        long alku = System.currentTimeMillis();
        long size = fso.count(absolutePaths);
        System.out.println("koko="+size);
        System.out.println("Time ms="+(System.currentTimeMillis()-alku));

    }
}
