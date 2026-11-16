package app.jingman.reader;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
public class VerifyScannedQimanTraversal {
    static String filename(String url)throws IOException {
        try{StringBuilder name=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8")))name.append(String.format(Locale.ROOT,"%02x",b&255));return "build/qiman-scan/"+name+".html";}catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }
    public static void main(String[] args)throws Exception {
        QimanSearchIndex index=new QimanSearchIndex(new ByteArrayInputStream(new byte[0]));
        QimanCatalogScanner scanner=new QimanCatalogScanner(index,null);
        List<String> missing=new ArrayList<>();
        while(scanner.hasNext()){
            try{scanner.step(url->new String(Files.readAllBytes(Paths.get(filename(url))),"UTF-8"));}
            catch(IOException e){missing.add(e.getMessage());scanner.deferFailure();}
        }
        System.out.println("Production scanner replay: "+scanner.done()+" pages, "+index.size()+" books, "+scanner.remaining()+" unresolved pages.");
        for(String error:missing)System.out.println(error);
        if(scanner.remaining()!=0)throw new AssertionError("Incomplete scanner replay");
    }
}
